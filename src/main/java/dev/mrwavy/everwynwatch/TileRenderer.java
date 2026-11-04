package dev.mrwavy.everwynwatch;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Draws top-down map tiles (128x128 blocks, one pixel per block) in the same
 * colors as vanilla maps. Only reads chunks that are already loaded, so it never
 * generates or loads terrain. Areas seen before stay on the map after they unload.
 */
public final class TileRenderer {
    public static final int TILE = 128;

    private record Key(String dim, int tx, int tz) {}
    private record Cached(int[] pixels, byte[] png, long renderedAt) {}

    private final MinecraftServer server;
    private final WatchConfig cfg;
    private final ConcurrentHashMap<Key, Cached> cache = new ConcurrentHashMap<>();

    private long windowStart = 0;
    private int rendersInWindow = 0;

    public TileRenderer(MinecraftServer server, WatchConfig cfg) {
        this.server = server;
        this.cfg = cfg;
    }

    /** Result of a tile request. {@code png == null} means nothing explored there yet. */
    public record Result(byte[] png, boolean busy) {}

    public Result get(String dim, int tx, int tz) throws Exception {
        Key key = new Key(dim, tx, tz);
        Cached old = cache.get(key);
        long now = System.currentTimeMillis();
        if (old != null && now - old.renderedAt < cfg.tileCacheSeconds * 1000L) {
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
        final int[] base = old != null ? old.pixels : null;
        int[] pixels = server.submit(() -> render(target, tx, tz, base)).get(3, TimeUnit.SECONDS);

        if (pixels == null) return new Result(null, false);
        byte[] png = encode(pixels);
        if (cache.size() > 3000) cache.clear();
        cache.put(key, new Cached(pixels, png, now));
        return new Result(png, false);
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

    /** Runs on the server thread. */
    private static int[] render(ServerWorld world, int tx, int tz, int[] base) {
        int[] colors = base != null ? base.clone() : new int[TILE * TILE];
        int[] heights = new int[TILE * TILE];
        boolean[] fresh = new boolean[TILE * TILE];
        boolean any = base != null;

        ServerChunkManager chunks = world.getChunkManager();
        boolean ceiling = world.getDimension().hasCeiling();
        int bottom = world.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int bx0 = tx * TILE;
        int bz0 = tz * TILE;
        int[] mapColor = new int[TILE * TILE];
        int[] waterDepth = new int[TILE * TILE];
        int[] floorColor = new int[TILE * TILE];

        for (int cx = 0; cx < TILE / 16; cx++) {
            for (int cz = 0; cz < TILE / 16; cz++) {
                int chunkX = (bx0 >> 4) + cx;
                int chunkZ = (bz0 >> 4) + cz;
                WorldChunk chunk = chunks.getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                any = true;

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = (chunkX << 4) + lx;
                        int wz = (chunkZ << 4) + lz;
                        int i = (wz - bz0) * TILE + (wx - bx0);

                        int y;
                        BlockState state;
                        if (!ceiling) {
                            y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz) + 1;
                            do {
                                y--;
                                state = chunk.getBlockState(pos.set(wx, y, wz));
                            } while (state.getMapColor(world, pos) == MapColor.CLEAR && y > bottom);
                        } else {
                            // Nether-style roof: skip the roof, then find the first floor below open air.
                            y = Math.min(110, world.getTopY() - 1);
                            boolean seenAir = false;
                            state = chunk.getBlockState(pos.set(wx, y, wz));
                            for (; y > bottom; y--) {
                                state = chunk.getBlockState(pos.set(wx, y, wz));
                                if (state.isAir()) seenAir = true;
                                else if (seenAir && state.getMapColor(world, pos) != MapColor.CLEAR) break;
                            }
                        }

                        int depth = 0;
                        if (state.getFluidState().isIn(FluidTags.WATER)) {
                            int wy = y;
                            BlockState below = state;
                            while (depth < 16 && wy > bottom && below.getFluidState().isIn(FluidTags.WATER)) {
                                depth++;
                                wy--;
                                below = chunk.getBlockState(pos.set(wx, wy, wz));
                            }
                            // Remember the lake/sea floor so shallow water can show it through.
                            MapColor floor = below.getMapColor(world, pos);
                            floorColor[i] = floor == MapColor.CLEAR ? -1 : floor.color;
                        }

                        MapColor mc = state.getMapColor(world, pos.set(wx, y, wz));
                        mapColor[i] = mc == MapColor.CLEAR ? -1 : mc.color;
                        heights[i] = y;
                        waterDepth[i] = depth;
                        fresh[i] = true;
                    }
                }
            }
        }
        if (!any) return null;

        // Xaero-style relief: light comes from the north-west, so slopes facing it get
        // brighter and slopes facing away get darker, with smooth steps instead of 3 tones.
        for (int z = 0; z < TILE; z++) {
            for (int x = 0; x < TILE; x++) {
                int i = z * TILE + x;
                if (!fresh[i]) continue;
                if (mapColor[i] == -1) {
                    colors[i] = 0;
                    continue;
                }
                int c = mapColor[i];
                double shade;
                if (waterDepth[i] > 0) {
                    // Water gets steadily darker with depth; shallow water shows a hint of the floor.
                    int d = waterDepth[i];
                    if (d <= 3 && floorColor[i] != -1) {
                        double mix = d == 1 ? 0.45 : d == 2 ? 0.3 : 0.15;
                        c = blend(c, floorColor[i], mix);
                    }
                    shade = 1.05 - Math.min(d, 16) * 0.025;
                } else {
                    int h = heights[i];
                    int north = (z > 0 && fresh[i - TILE] && waterDepth[i - TILE] == 0) ? heights[i - TILE] : h;
                    int west = (x > 0 && fresh[i - 1] && waterDepth[i - 1] == 0) ? heights[i - 1] : h;
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
