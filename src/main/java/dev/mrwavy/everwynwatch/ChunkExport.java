package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Every block of one chunk (16x16, full height) for the app's "real blocks" 3D view, plus the biome
 * of each 4x4 column so grass, leaves and water get the right color.
 *
 * Loaded chunks are copied on the server thread (a quick copy of each section) and encoded on a web
 * thread. Other chunks are read from the save files. It never loads or generates chunks.
 *
 * JSON: {cx, cz, live, palette:["minecraft:oak_stairs[facing=east,half=bottom,...]", ...],
 *        sections:[{y, one:i} | {y, b8:base64} | {y, b16:base64}], biomes:[ids], grid:[16 indexes],
 *        biomeInfo:{id:{t, d, w, g?, f?, m?}}}
 * Block order inside a section is (y*16 + z)*16 + x, like Minecraft.
 */
final class ChunkExport {
    static final class Busy extends Exception {}

    private record Cached(byte[] gz, long at, boolean live) {}
    private static final ConcurrentHashMap<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, JsonObject> BIOME_INFO = new ConcurrentHashMap<>();
    private static final byte[] NOTHING = new byte[0];
    private static long windowStart;
    private static int inWindow;

    /** One chunk's blocks before encoding. */
    private static final class Raw {
        final List<String> palette = new ArrayList<>();
        final List<Integer> sectionY = new ArrayList<>();
        final List<int[]> sections = new ArrayList<>();
        final String[] grid = new String[16];
        boolean live;
    }

    private ChunkExport() {}

    /** Gzipped JSON for the chunk, or null if nothing has been saved there yet. */
    static byte[] get(MinecraftServer server, String dim, int cx, int cz) throws Exception {
        String key = dim + "|" + cx + "|" + cz;
        long now = System.currentTimeMillis();
        Cached c = CACHE.get(key);
        if (c != null && now - c.at < (c.live ? 3_000 : 60_000)) return c.gz.length == 0 ? null : c.gz;
        if (!allow()) {
            if (c != null) return c.gz.length == 0 ? null : c.gz;
            throw new Busy();
        }
        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
        }
        if (world == null) return null;
        final ServerWorld target = world;

        Raw raw = null;
        List<Object[]> live = server.submit(() -> copyLoaded(target, cx, cz)).get(3, TimeUnit.SECONDS);
        if (live != null) {
            raw = fromLoaded(live);
        } else {
            raw = fromSaved(target, cx, cz);
        }
        byte[] gz = raw == null ? NOTHING : encode(server, raw, cx, cz);
        if (CACHE.size() > 4000) CACHE.clear();
        CACHE.put(key, new Cached(gz, now, raw != null && raw.live));
        return gz.length == 0 ? null : gz;
    }

    private static synchronized boolean allow() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            inWindow = 0;
        }
        if (inWindow >= 40) return false;
        inWindow++;
        return true;
    }

    // ------------------------------------------------------------------ loaded chunks

    /**
     * Server thread: copies the sections of a loaded chunk (fast) so they can be read on a web thread.
     * Returns null if the chunk isn't loaded. Element 0 is the biome grid (String[16]); the rest are
     * {sectionY, PalettedContainer copy}.
     */
    private static List<Object[]> copyLoaded(ServerWorld world, int cx, int cz) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) return null;
        List<Object[]> out = new ArrayList<>();
        String[] grid = new String[16];
        for (int i = 0; i < 16; i++) {
            int lx = (i & 3) * 4 + 2, lz = (i >> 2) * 4 + 2;
            int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
            RegistryEntry<Biome> b = chunk.getBiomeForNoiseGen(((cx << 4) + lx) >> 2, y >> 2, ((cz << 4) + lz) >> 2);
            grid[i] = b.getKey().map(k -> k.getValue().toString()).orElse("minecraft:plains");
        }
        out.add(new Object[]{grid});
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            out.add(new Object[]{bottom + i, s.getBlockStateContainer().copy()});
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Raw fromLoaded(List<Object[]> copied) {
        Raw raw = new Raw();
        raw.live = true;
        System.arraycopy((String[]) copied.get(0)[0], 0, raw.grid, 0, 16);
        Map<BlockState, Integer> ids = new IdentityHashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        for (int s = 1; s < copied.size(); s++) {
            int sy = (Integer) copied.get(s)[0];
            PalettedContainer<BlockState> states = (PalettedContainer<BlockState>) copied.get(s)[1];
            int[] idx = new int[4096];
            boolean any = false;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = states.get(x, y, z);
                        Integer id = ids.get(st);
                        if (id == null) {
                            String name = stateString(NbtHelper.fromBlockState(st));
                            id = byName.computeIfAbsent(name, n -> {
                                raw.palette.add(n);
                                return raw.palette.size() - 1;
                            });
                            ids.put(st, id);
                        }
                        idx[(y * 16 + z) * 16 + x] = id;
                        if (!isAir(raw.palette.get(id))) any = true;
                    }
                }
            }
            if (!any) continue;
            raw.sectionY.add(sy);
            raw.sections.add(idx);
        }
        return raw;
    }

    // ------------------------------------------------------------------ saved chunks

    /** Web thread: reads a chunk that isn't loaded straight from the region file. */
    private static Raw fromSaved(ServerWorld world, int cx, int cz) throws Exception {
        Optional<NbtCompound> opt = world.getChunkManager().threadedAnvilChunkStorage
                .getNbt(new ChunkPos(cx, cz)).get(5, TimeUnit.SECONDS);
        if (opt.isEmpty()) return null;
        NbtCompound nbt = opt.get();
        String status = nbt.getString("Status");
        if (!status.equals("minecraft:full") && !status.equals("full")) return null; // half-generated edge
        if (!nbt.contains("sections", NbtElement.LIST_TYPE)) return null;

        Raw raw = new Raw();
        Map<String, Integer> byName = new HashMap<>();
        NbtList list = nbt.getList("sections", NbtElement.COMPOUND_TYPE);
        // biome palette + indexes per section, to color by the surface biome later
        Map<Integer, String[]> biomePal = new HashMap<>();
        Map<Integer, int[]> biomeIdx = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound sec = list.getCompound(i);
            int sy = sec.getByte("Y");
            if (sec.contains("biomes", NbtElement.COMPOUND_TYPE)) {
                NbtCompound b = sec.getCompound("biomes");
                NbtList pal = b.getList("palette", NbtElement.STRING_TYPE);
                if (pal.size() > 0) {
                    String[] names = new String[pal.size()];
                    for (int p = 0; p < names.length; p++) names[p] = pal.getString(p);
                    biomePal.put(sy, names);
                    biomeIdx.put(sy, unpack(b.getLongArray("data"), names.length, 64, 0));
                }
            }
            if (!sec.contains("block_states", NbtElement.COMPOUND_TYPE)) continue;
            NbtCompound bs = sec.getCompound("block_states");
            NbtList pal = bs.getList("palette", NbtElement.COMPOUND_TYPE);
            int n = pal.size();
            if (n == 0) continue;
            int[] local = new int[n];
            boolean anySolid = false;
            for (int p = 0; p < n; p++) {
                String name = stateString(pal.getCompound(p));
                if (!isAir(name)) anySolid = true;
                local[p] = byName.computeIfAbsent(name, k -> {
                    raw.palette.add(k);
                    return raw.palette.size() - 1;
                });
            }
            if (!anySolid) continue;
            int[] packed = unpack(bs.getLongArray("data"), n, 4096, 4);
            if (packed == null) continue; // damaged section
            int[] idx = new int[4096];
            for (int k = 0; k < 4096; k++) idx[k] = local[packed[k]];
            raw.sectionY.add(sy);
            raw.sections.add(idx);
        }
        // surface biome of each 4x4 column
        for (int cell = 0; cell < 16; cell++) {
            int lx = (cell & 3) * 4 + 2, lz = (cell >> 2) * 4 + 2;
            int top = Integer.MIN_VALUE;
            for (int s = raw.sections.size() - 1; s >= 0 && top == Integer.MIN_VALUE; s--) {
                int[] idx = raw.sections.get(s);
                for (int y = 15; y >= 0; y--) {
                    if (!isAir(raw.palette.get(idx[(y * 16 + lz) * 16 + lx]))) {
                        top = raw.sectionY.get(s) * 16 + y;
                        break;
                    }
                }
            }
            String biome = "minecraft:plains";
            if (top != Integer.MIN_VALUE) {
                int sy = Math.floorDiv(top, 16);
                String[] names = biomePal.get(sy);
                if (names != null) {
                    int[] bi = biomeIdx.get(sy);
                    int q = (((top & 15) >> 2) * 4 + (lz >> 2)) * 4 + (lx >> 2);
                    biome = bi == null ? names[0] : names[Math.min(names.length - 1, bi[q])];
                }
            }
            raw.grid[cell] = biome;
        }
        return raw;
    }

    /**
     * Unpacks Minecraft's packed palette indexes (1.16+ layout: values never span two longs).
     * Returns all zeros when the palette has one entry, null if the data is too short.
     */
    private static int[] unpack(long[] data, int paletteSize, int count, int minBits) {
        int[] out = new int[count];
        if (paletteSize <= 1) return out;
        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        if (data.length < (count + perLong - 1) / perLong) return null;
        long mask = (1L << bits) - 1;
        for (int k = 0; k < count; k++) {
            int v = (int) ((data[k / perLong] >>> ((k % perLong) * bits)) & mask);
            out[k] = v < paletteSize ? v : 0;
        }
        return out;
    }

    // ------------------------------------------------------------------ encoding

    /** "minecraft:oak_stairs[facing=east,half=bottom]" from {Name, Properties}, properties sorted. */
    private static String stateString(NbtCompound c) {
        String name = c.getString("Name");
        if (name.isEmpty()) name = "minecraft:air";
        if (!c.contains("Properties", NbtElement.COMPOUND_TYPE)) return name;
        NbtCompound props = c.getCompound("Properties");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String k : props.getKeys()) sorted.put(k, props.getString(k));
        if (sorted.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air");
    }

    private static byte[] encode(MinecraftServer server, Raw raw, int cx, int cz) throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("cx", cx);
        o.addProperty("cz", cz);
        o.addProperty("live", raw.live);
        JsonArray pal = new JsonArray();
        raw.palette.forEach(pal::add);
        o.add("palette", pal);
        boolean small = raw.palette.size() <= 256;
        JsonArray secs = new JsonArray();
        Base64.Encoder b64 = Base64.getEncoder();
        for (int s = 0; s < raw.sections.size(); s++) {
            int[] idx = raw.sections.get(s);
            JsonObject so = new JsonObject();
            so.addProperty("y", raw.sectionY.get(s));
            boolean same = true;
            for (int k = 1; k < 4096 && same; k++) same = idx[k] == idx[0];
            if (same) {
                so.addProperty("one", idx[0]);
            } else if (small) {
                byte[] b = new byte[4096];
                for (int k = 0; k < 4096; k++) b[k] = (byte) idx[k];
                so.addProperty("b8", b64.encodeToString(b));
            } else {
                byte[] b = new byte[8192];
                for (int k = 0; k < 4096; k++) {
                    b[k * 2] = (byte) idx[k];
                    b[k * 2 + 1] = (byte) (idx[k] >> 8);
                }
                so.addProperty("b16", b64.encodeToString(b));
            }
            secs.add(so);
        }
        o.add("sections", secs);
        List<String> biomes = new ArrayList<>();
        JsonArray grid = new JsonArray();
        for (String b : raw.grid) {
            int i = biomes.indexOf(b);
            if (i < 0) {
                biomes.add(b);
                i = biomes.size() - 1;
            }
            grid.add(i);
        }
        JsonArray bl = new JsonArray();
        JsonObject info = new JsonObject();
        for (String b : biomes) {
            bl.add(b);
            info.add(b, biomeInfo(server, b));
        }
        o.add("biomes", bl);
        o.add("grid", grid);
        o.add("biomeInfo", info);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    /** Climate and colors of a biome: {t: temperature, d: downfall, w: water, g/f: grass/foliage override, m: grass modifier}. */
    private static JsonObject biomeInfo(MinecraftServer server, String id) {
        JsonObject known = BIOME_INFO.get(id);
        if (known != null) return known;
        JsonObject out = new JsonObject();
        out.addProperty("t", 0.8);
        out.addProperty("d", 0.4);
        out.addProperty("w", 0x3F76E4);
        try {
            Identifier ident = Identifier.tryParse(id);
            Biome biome = ident == null ? null : server.getRegistryManager().get(RegistryKeys.BIOME).get(ident);
            if (biome != null) {
                out.addProperty("t", biome.getTemperature());
                out.addProperty("w", biome.getWaterColor());
                JsonElement el = Biome.NETWORK_CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager()), biome)
                        .result().orElse(null);
                if (el != null && el.isJsonObject()) {
                    JsonObject j = el.getAsJsonObject();
                    if (j.has("downfall")) out.addProperty("d", j.get("downfall").getAsFloat());
                    if (j.has("effects") && j.get("effects").isJsonObject()) {
                        JsonObject fx = j.getAsJsonObject("effects");
                        if (fx.has("grass_color")) out.addProperty("g", fx.get("grass_color").getAsInt());
                        if (fx.has("foliage_color")) out.addProperty("f", fx.get("foliage_color").getAsInt());
                        if (fx.has("grass_color_modifier")) out.addProperty("m", fx.get("grass_color_modifier").getAsString());
                    }
                }
            }
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Biome colors unavailable for {}", id, e);
        }
        BIOME_INFO.put(id, out);
        return out;
    }
}
