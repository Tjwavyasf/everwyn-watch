package dev.mrwavy.everwynwatch;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Draws top-down map tiles (128x128 blocks, one pixel per block) in the same
 * colors as vanilla maps.
 *
 * Loaded areas are read live (on the server thread). Everything else is read
 * straight from the world's save files on a web thread, without loading those
 * chunks into the game, so the whole explored world shows up even when nobody is
 * online. It never generates new terrain.
 */
public final class TileRenderer {
    public static final int TILE = 128;
    private static final int N = TILE * TILE;
    private static final int DISK_CACHE_MS = 10 * 60_000;

    private record Key(String dim, int tx, int tz) {}
    private record Cached(int[] pixels, byte[] png, long renderedAt, boolean live) {}

    /** Raw samples for one tile, before shading. */
    private static final class Raw {
        final int[] mapColor = new int[N];
        final int[] heights = new int[N];
        final int[] waterDepth = new int[N];
        final int[] floorColor = new int[N];
        final boolean[] fresh = new boolean[N];
        final boolean[] chunkDone = new boolean[64];
        boolean anyLive, anyDisk;
    }

    private final MinecraftServer server;
    private final WatchConfig cfg;
    private final ConcurrentHashMap<Key, Cached> cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockState, Integer> COLOR = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockState, Boolean> WATER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BlockState> STATES = new ConcurrentHashMap<>();

    private long windowStart = 0;
    private int rendersInWindow = 0;

    // What the map has been doing, shown in the app so problems are easy to spot
    private static final java.util.concurrent.atomic.AtomicLong DRAWN = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong EMPTY = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong LIVE_CHUNKS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong SAVED_CHUNKS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong UNFINISHED = new java.util.concurrent.atomic.AtomicLong();
    private static volatile String lastError = null, lastStatus = null;
    private static volatile long lastErrorAt = 0, lastLog = 0;

    static com.google.gson.JsonObject stats() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("tilesDrawn", DRAWN.get());
        o.addProperty("emptyTiles", EMPTY.get());
        o.addProperty("liveChunks", LIVE_CHUNKS.get());
        o.addProperty("savedChunks", SAVED_CHUNKS.get());
        o.addProperty("unfinishedChunks", UNFINISHED.get());
        if (lastStatus != null) o.addProperty("lastChunkStatus", lastStatus);
        if (lastError != null) {
            o.addProperty("lastError", lastError);
            o.addProperty("lastErrorAt", lastErrorAt);
        }
        return o;
    }

    private static void problem(String what, Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        lastError = what + ": " + root.getClass().getSimpleName() + (root.getMessage() != null ? " - " + root.getMessage() : "");
        lastErrorAt = System.currentTimeMillis();
        if (lastErrorAt - lastLog > 60_000) {
            lastLog = lastErrorAt;
            EverwynWatch.LOG.warn("Everwyn Watch map problem ({})", what, e);
        }
    }

    public TileRenderer(MinecraftServer server, WatchConfig cfg) {
        this.server = server;
        this.cfg = cfg;
    }

    /** Result of a tile request. {@code png == null} means nothing explored there yet. */
    public record Result(byte[] png, boolean busy) {}

    public Result get(String dim, int tx, int tz) throws Exception {
        Key key = new Key(dim, tx, tz);
        Cached old = cache.get(key);
        if (old == null) {
            // Not in memory: use the copy saved on disk, if any (renderedAt 0 = check for updates).
            old = loadFromDisk(key);
            if (old != null) {
                if (cache.size() > 3000) cache.clear();
                cache.put(key, old);
            }
        }
        long now = System.currentTimeMillis();
        if (old != null && now - old.renderedAt < (old.live ? cfg.tileCacheSeconds * 1000L : DISK_CACHE_MS)) {
            return new Result(old.png, false);
        }
        if (!allowRender()) {
            // Too many fresh tiles this second; hand back the old one if we have it.
            return old != null ? new Result(old.png, false) : new Result(null, true);
        }

        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
        }
        if (world == null) return new Result(null, false);

        final ServerWorld target = world;
        Raw raw;
        try {
            raw = server.submit(() -> sampleLoaded(target, tx, tz)).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            problem("reading loaded chunks", e);
            raw = new Raw();
        }
        try {
            sampleSaved(target, tx, tz, raw);
        } catch (Exception e) {
            problem("reading saved chunks", e);
        }
        if (!raw.anyLive && !raw.anyDisk && old == null) {
            EMPTY.incrementAndGet();
            return new Result(null, false);
        }
        DRAWN.incrementAndGet();

        int[] pixels = shade(raw, old != null ? old.pixels : null);
        if (old != null && java.util.Arrays.equals(pixels, old.pixels)) {
            cache.put(key, new Cached(old.pixels, old.png, now, raw.anyLive));
            return new Result(old.png, false);
        }
        byte[] png = encode(pixels);
        if (cache.size() > 3000) cache.clear();
        cache.put(key, new Cached(pixels, png, now, raw.anyLive));
        saveToDisk(key, png);
        return new Result(png, false);
    }

    // ------------------------------------------------------------------ live (loaded) chunks

    /** Server thread: samples chunks that are loaded right now. */
    private static Raw sampleLoaded(ServerWorld world, int tx, int tz) {
        Raw raw = new Raw();
        ServerChunkManager chunks = world.getChunkManager();
        boolean ceiling = world.getDimension().hasCeiling();
        int bottom = world.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int bx0 = tx * TILE, bz0 = tz * TILE;

        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                int chunkX = (bx0 >> 4) + cx, chunkZ = (bz0 >> 4) + cz;
                WorldChunk chunk = chunks.getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                raw.anyLive = true;
                raw.chunkDone[cz * 8 + cx] = true;
                LIVE_CHUNKS.incrementAndGet();
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = (chunkX << 4) + lx, wz = (chunkZ << 4) + lz;
                        int i = (wz - bz0) * TILE + (wx - bx0);
                        int y;
                        BlockState state;
                        if (!ceiling) {
                            y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz) + 1;
                            do {
                                y--;
                                state = chunk.getBlockState(pos.set(wx, y, wz));
                            } while (color(state) == -1 && y > bottom);
                        } else {
                            // Nether-style roof: skip the roof, then find the first floor below open air.
                            y = Math.min(110, world.getTopY() - 1);
                            boolean seenAir = false;
                            state = chunk.getBlockState(pos.set(wx, y, wz));
                            for (; y > bottom; y--) {
                                state = chunk.getBlockState(pos.set(wx, y, wz));
                                if (state.isAir()) seenAir = true;
                                else if (seenAir && color(state) != -1) break;
                            }
                        }
                        int depth = 0;
                        if (water(state)) {
                            int wy = y;
                            BlockState below = state;
                            while (depth < 16 && wy > bottom && water(below)) {
                                depth++;
                                wy--;
                                below = chunk.getBlockState(pos.set(wx, wy, wz));
                            }
                            raw.floorColor[i] = color(below);
                        }
                        raw.mapColor[i] = color(state);
                        raw.heights[i] = y;
                        raw.waterDepth[i] = depth;
                        raw.fresh[i] = true;
                    }
                }
            }
        }
        return raw;
    }

    // ------------------------------------------------------------------ saved (unloaded) chunks

    /** Web thread: fills in chunks that aren't loaded, straight from the region files. */
    private static void sampleSaved(ServerWorld world, int tx, int tz, Raw raw) throws Exception {
        boolean ceiling = world.getDimension().hasCeiling();
        int bottom = world.getBottomY(), top = world.getTopY();
        int sections = (top - bottom) >> 4, minSection = bottom >> 4;
        int bx0 = tx * TILE, bz0 = tz * TILE;

        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                if (raw.chunkDone[cz * 8 + cx]) continue;
                int chunkX = (bx0 >> 4) + cx, chunkZ = (bz0 >> 4) + cz;
                Optional<NbtCompound> opt = world.getChunkManager().threadedAnvilChunkStorage
                        .getNbt(new ChunkPos(chunkX, chunkZ)).get(5, TimeUnit.SECONDS);
                if (opt.isEmpty()) continue;
                NbtCompound nbt = opt.get();
                String status = nbt.getString("Status");
                lastStatus = status;
                if (!status.equals("minecraft:full") && !status.equals("full")) { // half-generated edge of the world
                    UNFINISHED.incrementAndGet();
                    continue;
                }
                if (!nbt.contains("sections", NbtElement.LIST_TYPE)) continue; // very old format

                // Decode every section into palette indexes
                BlockState[][] palettes = new BlockState[sections][];
                int[][] indexes = new int[sections][];
                NbtList list = nbt.getList("sections", NbtElement.COMPOUND_TYPE);
                for (int s = 0; s < list.size(); s++) {
                    NbtCompound sec = list.getCompound(s);
                    int si = sec.getByte("Y") - minSection;
                    if (si < 0 || si >= sections || !sec.contains("block_states", NbtElement.COMPOUND_TYPE)) continue;
                    decodeSection(sec.getCompound("block_states"), si, palettes, indexes);
                }

                raw.anyDisk = true;
                raw.chunkDone[cz * 8 + cx] = true;
                SAVED_CHUNKS.incrementAndGet();
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int i = ((chunkZ << 4) + lz - bz0) * TILE + ((chunkX << 4) + lx - bx0);
                        int startY = ceiling ? Math.min(110, top - 1) : top - 1;
                        boolean seenAir = false;
                        int foundY = bottom;
                        BlockState found = Blocks.AIR.getDefaultState();
                        for (int y = startY; y >= bottom; y--) {
                            BlockState st = at(palettes, indexes, y - bottom, lx, lz);
                            if (ceiling) {
                                if (st.isAir()) seenAir = true;
                                else if (seenAir && color(st) != -1) { found = st; foundY = y; break; }
                            } else if (color(st) != -1) {
                                found = st;
                                foundY = y;
                                break;
                            }
                        }
                        int depth = 0;
                        if (water(found)) {
                            int wy = foundY;
                            BlockState below = found;
                            while (depth < 16 && wy > bottom && water(below)) {
                                depth++;
                                wy--;
                                below = at(palettes, indexes, wy - bottom, lx, lz);
                            }
                            raw.floorColor[i] = color(below);
                        }
                        raw.mapColor[i] = color(found);
                        raw.heights[i] = foundY;
                        raw.waterDepth[i] = depth;
                        raw.fresh[i] = true;
                    }
                }
            }
        }
    }

    /** The 1.18+ chunk format: a palette of block states plus tightly packed indexes. */
    private static void decodeSection(NbtCompound bs, int si, BlockState[][] palettes, int[][] indexes) {
        NbtList pal = bs.getList("palette", NbtElement.COMPOUND_TYPE);
        int n = pal.size();
        if (n == 0) return;
        BlockState[] states = new BlockState[n];
        for (int p = 0; p < n; p++) states[p] = state(pal.getCompound(p));
        palettes[si] = states;
        if (n == 1) return; // whole section is one block
        long[] data = bs.getLongArray("data");
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(n - 1));
        int perLong = 64 / bits;
        if (data.length < (4096 + perLong - 1) / perLong) {
            palettes[si] = null;
            return;
        }
        long mask = (1L << bits) - 1;
        int[] idx = new int[4096];
        for (int k = 0; k < 4096; k++) {
            int v = (int) ((data[k / perLong] >>> ((k % perLong) * bits)) & mask);
            idx[k] = v < n ? v : 0;
        }
        indexes[si] = idx;
    }

    private static BlockState at(BlockState[][] palettes, int[][] indexes, int relY, int lx, int lz) {
        int si = relY >> 4;
        if (si < 0 || si >= palettes.length) return Blocks.AIR.getDefaultState();
        BlockState[] pal = palettes[si];
        if (pal == null) return Blocks.AIR.getDefaultState();
        int[] idx = indexes[si];
        if (idx == null) return pal[0];
        return pal[idx[((relY & 15) * 16 + lz) * 16 + lx]];
    }

    private static BlockState state(NbtCompound c) {
        String key = c.toString();
        BlockState cached = STATES.get(key);
        if (cached != null) return cached;
        BlockState st;
        try {
            st = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), c);
        } catch (Exception e) {
            st = Blocks.AIR.getDefaultState();
        }
        if (STATES.size() < 50_000) STATES.put(key, st);
        return st;
    }

    private static int color(BlockState st) {
        Integer c = COLOR.get(st);
        if (c != null) return c;
        int v;
        try {
            MapColor mc = st.getMapColor(null, BlockPos.ORIGIN);
            v = mc == null || mc == MapColor.CLEAR ? -1 : mc.color;
        } catch (Exception e) {
            // a few modded blocks need a real world to pick a color
            try {
                MapColor mc = st.getBlock().getDefaultMapColor();
                v = mc == null || mc == MapColor.CLEAR ? -1 : mc.color;
            } catch (Exception e2) {
                v = -1;
            }
        }
        COLOR.put(st, v);
        return v;
    }

    private static boolean water(BlockState st) {
        return WATER.computeIfAbsent(st, s -> s.getFluidState().isIn(FluidTags.WATER));
    }

    // ------------------------------------------------------------------ shading

    /** Xaero-style relief: light from the north-west; water darker with depth. */
    private static int[] shade(Raw raw, int[] base) {
        int[] colors = base != null ? base.clone() : new int[N];
        for (int z = 0; z < TILE; z++) {
            for (int x = 0; x < TILE; x++) {
                int i = z * TILE + x;
                if (!raw.fresh[i]) continue;
                if (raw.mapColor[i] == -1) {
                    colors[i] = 0;
                    continue;
                }
                int c = raw.mapColor[i];
                double shade;
                if (raw.waterDepth[i] > 0) {
                    int d = raw.waterDepth[i];
                    if (d <= 3 && raw.floorColor[i] != -1) {
                        double mix = d == 1 ? 0.45 : d == 2 ? 0.3 : 0.15;
                        c = blend(c, raw.floorColor[i], mix);
                    }
                    shade = 1.05 - Math.min(d, 16) * 0.025;
                } else {
                    int h = raw.heights[i];
                    int north = (z > 0 && raw.fresh[i - TILE] && raw.waterDepth[i - TILE] == 0) ? raw.heights[i - TILE] : h;
                    int west = (x > 0 && raw.fresh[i - 1] && raw.waterDepth[i - 1] == 0) ? raw.heights[i - 1] : h;
                    int slope = (h - north) + (h - west);
                    shade = 0.9 + Math.max(-4, Math.min(4, slope)) * 0.07;
                }
                int r = clamp((int) (((c >> 16) & 0xFF) * shade));
                int g = clamp((int) (((c >> 8) & 0xFF) * shade));
                int b = clamp((int) ((c & 0xFF) * shade));
                colors[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return colors;
    }

    // ------------------------------------------------------------------ saved tile images

    /** Tiles are saved in <world>/everwyn-watch/tiles/<dimension>/<x>_<z>.png */
    private Path tileFile(Key key) {
        String dimFolder = key.dim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return server.getSavePath(WorldSavePath.ROOT).resolve("everwyn-watch").resolve("tiles")
                .resolve(dimFolder).resolve(key.tx() + "_" + key.tz() + ".png");
    }

    private Cached loadFromDisk(Key key) {
        Path file = tileFile(key);
        if (!Files.isRegularFile(file)) return null;
        try {
            byte[] png = Files.readAllBytes(file);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null || img.getWidth() != TILE || img.getHeight() != TILE) return null;
            int[] pixels = img.getRGB(0, 0, TILE, TILE, null, 0, TILE);
            return new Cached(pixels, png, 0L, false);
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Could not read saved map tile {}", file, e);
            return null;
        }
    }

    private void saveToDisk(Key key, byte[] png) {
        Path file = tileFile(key);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, png);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Could not save map tile {}", file, e);
        }
    }

    private synchronized boolean allowRender() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            rendersInWindow = 0;
        }
        if (rendersInWindow >= Math.max(1, cfg.maxTileRendersPerSecond)) return false;
        rendersInWindow++;
        return true;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static byte[] encode(int[] argb) throws Exception {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, TILE, TILE, argb, 0, TILE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
