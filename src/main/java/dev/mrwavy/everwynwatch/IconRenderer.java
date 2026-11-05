package dev.mrwavy.everwynwatch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Real inventory icons for every item, vanilla and modded.
 *
 * Servers don't ship Minecraft's textures, so on first start this downloads the
 * official client jar for the running version from Mojang (once, ~25 MB, verified
 * by checksum) and reads models + textures from it and from every mod jar.
 * Flat items are drawn from their layers; blocks are drawn as a little 3D cube.
 * Results are cached as PNGs in config/everwyn-watch-cache/icons.
 */
final class IconRenderer {
    private static final int SIZE = 64;
    private static Path cacheDir;
    private static Path iconDir;
    private static volatile FileSystem clientFs;
    private static volatile String status = "starting";
    private static final Map<String, List<Path>> NS_ROOTS = new HashMap<>();
    private static final Map<String, byte[]> MEMORY = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private IconRenderer() {}

    static void init(Path configDir) {
        cacheDir = configDir.resolve("everwyn-watch-cache");
        iconDir = cacheDir.resolve("icons");
        StringBuilder modKey = new StringBuilder(SharedConstants.getGameVersion().getName());
        List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        mods.sort(Comparator.comparing(m -> m.getMetadata().getId()));
        for (ModContainer mod : mods) {
            modKey.append('|').append(mod.getMetadata().getId()).append('@').append(mod.getMetadata().getVersion().getFriendlyString());
            try {
                for (Path root : mod.getRootPaths()) {
                    Path assets = root.resolve("assets");
                    if (!Files.isDirectory(assets)) continue;
                    try (Stream<Path> list = Files.list(assets)) {
                        list.filter(Files::isDirectory).forEach(ns ->
                                NS_ROOTS.computeIfAbsent(ns.getFileName().toString().replace("/", ""), k -> new ArrayList<>()).add(ns));
                    }
                }
            } catch (Throwable ignored) {
                // built-in pseudo mods have no files
            }
        }

        // Forget old icons when mods change
        try {
            Files.createDirectories(iconDir);
            Path keyFile = cacheDir.resolve("icons.key");
            String key = Integer.toHexString(modKey.toString().hashCode());
            String old = Files.exists(keyFile) ? Files.readString(keyFile).trim() : "";
            if (!old.equals(key)) {
                deleteTree(iconDir);
                Files.createDirectories(iconDir);
                Files.writeString(keyFile, key);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Everwyn Watch icon cache problem", e);
        }

        Thread t = new Thread(IconRenderer::loadClientJar, "EverwynWatch-Icons");
        t.setDaemon(true);
        t.start();
    }

    static String status() {
        return status;
    }

    private static void loadClientJar() {
        String ver = SharedConstants.getGameVersion().getName();
        Path jar = cacheDir.resolve("client-" + ver + ".jar");
        try {
            if (!Files.exists(jar)) {
                status = "downloading";
                EverwynWatch.LOG.info("Everwyn Watch: downloading Minecraft {} textures from Mojang for item icons (one time)...", ver);
                HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(20)).build();
                JsonObject manifest = getJson(http, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                String versionUrl = null;
                for (JsonElement el : manifest.getAsJsonArray("versions")) {
                    JsonObject v = el.getAsJsonObject();
                    if (v.get("id").getAsString().equals(ver)) versionUrl = v.get("url").getAsString();
                }
                if (versionUrl == null) throw new IllegalStateException("Version " + ver + " not in Mojang's list");
                JsonObject client = getJson(http, versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
                String url = client.get("url").getAsString();
                String sha1 = client.get("sha1").getAsString();

                Path tmp = cacheDir.resolve("client-" + ver + ".jar.part");
                HttpResponse<Path> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build(),
                        HttpResponse.BodyHandlers.ofFile(tmp));
                if (res.statusCode() != 200) throw new IllegalStateException("Download failed: HTTP " + res.statusCode());
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                try (InputStream in = Files.newInputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
                if (!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(sha1)) {
                    Files.deleteIfExists(tmp);
                    throw new IllegalStateException("Checksum didn't match");
                }
                Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            }
            clientFs = FileSystems.newFileSystem(jar);
            MISSING.clear(); // vanilla parents are available now; retry earlier misses
            status = "ready";
            EverwynWatch.LOG.info("Everwyn Watch: item icons ready");
        } catch (Exception e) {
            status = "failed";
            EverwynWatch.LOG.warn("Everwyn Watch couldn't get vanilla textures ({}). Modded icons still work; vanilla items show letters.", e.toString());
        }
    }

    private static JsonObject getJson(HttpClient http, String url) throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** PNG bytes for the item's inventory icon, or null if it can't be drawn. */
    static byte[] icon(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || iconDir == null) return null;
        String key = id.toString();
        byte[] mem = MEMORY.get(key);
        if (mem != null) return mem.length == 0 ? null : mem;
        if (MISSING.contains(key)) return null;

        Path file = iconDir.resolve(id.getNamespace()).resolve(id.getPath().replace('/', '_') + ".png");
        try {
            if (Files.exists(file)) {
                byte[] data = Files.readAllBytes(file);
                remember(key, data);
                return data.length == 0 ? null : data;
            }
            BufferedImage img = renderItem(id.getNamespace(), id.getPath());
            boolean final_ = clientFs != null || "failed".equals(status);
            if (img == null) {
                if (final_) {
                    Files.createDirectories(file.getParent());
                    Files.write(file, new byte[0]);
                    remember(key, new byte[0]);
                } else {
                    MISSING.add(key); // retry once vanilla textures are ready
                }
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            byte[] data = out.toByteArray();
            if (final_) {
                Files.createDirectories(file.getParent());
                Files.write(file, data);
            }
            remember(key, data);
            return data;
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Icon failed for {}", key, e);
            return null;
        }
    }

    private static void remember(String key, byte[] data) {
        if (MEMORY.size() > 6000) MEMORY.clear();
        MEMORY.put(key, data);
    }

    // ------------------------------------------------------------------ models

    private static final class Model {
        final Map<String, String> textures = new HashMap<>();
        boolean generated, block, entity;
    }

    private static BufferedImage renderItem(String ns, String path) {
        Model m = resolve(ns + ":item/" + path);
        if (m == null) {
            m = resolve(ns + ":block/" + path);
            if (m != null) m.block = true;
        }
        if (m == null) return null;
        if (m.generated || (!m.block && m.textures.containsKey("layer0"))) return flat(m.textures);
        if (m.block) {
            BufferedImage cube = cube(m.textures);
            if (cube != null) return cube;
        }
        String particle = tex(m.textures, "particle");
        return particle == null ? null : flatSingle(particle);
    }

    private static Model resolve(String start) {
        Model m = new Model();
        String cur = start;
        boolean first = true;
        for (int depth = 0; cur != null && depth < 16; depth++) {
            String id = norm(cur);
            String path = id.substring(id.indexOf(':') + 1);
            if (id.equals("minecraft:builtin/generated")) { m.generated = true; break; }
            if (id.equals("minecraft:builtin/entity")) { m.entity = true; break; }
            if (path.startsWith("block/")) m.block = true;
            JsonObject json = json(id);
            if (json == null) {
                if (first) return null;
                break;
            }
            first = false;
            if (json.has("textures") && json.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) m.textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
            cur = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        if (m.generated) m.block = false;
        return m;
    }

    private static String norm(String ref) {
        return ref.contains(":") ? ref : "minecraft:" + ref;
    }

    private static JsonObject json(String id) {
        int c = id.indexOf(':');
        byte[] data = read(id.substring(0, c), "models/" + id.substring(c + 1) + ".json");
        if (data == null) return null;
        try {
            JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads assets/<ns>/<path> from mod jars first, then the vanilla client jar. */
    private static byte[] read(String ns, String path) {
        List<Path> roots = NS_ROOTS.get(ns);
        if (roots != null) {
            for (Path root : roots) {
                try {
                    Path p = root.resolve(path);
                    if (Files.isRegularFile(p)) return Files.readAllBytes(p);
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        FileSystem fs = clientFs;
        if (fs != null) {
            try {
                Path p = fs.getPath("/assets/" + ns + "/" + path);
                if (Files.isRegularFile(p)) return Files.readAllBytes(p);
            } catch (Exception ignored) {
                // not there
            }
        }
        return null;
    }

    private static String tex(Map<String, String> textures, String... keys) {
        for (String key : keys) {
            String v = textures.get(key);
            for (int i = 0; v != null && v.startsWith("#") && i < 10; i++) v = textures.get(v.substring(1));
            if (v != null && !v.startsWith("#")) return v;
        }
        return null;
    }

    private static BufferedImage loadTexture(String ref) {
        String id = norm(ref);
        int c = id.indexOf(':');
        String ns = id.substring(0, c), path = id.substring(c + 1);
        byte[] data = read(ns, "textures/" + path + ".png");
        if (data == null) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return null;
            int w = img.getWidth();
            if (img.getHeight() > w) img = img.getSubimage(0, 0, w, w); // animated strip: first frame
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            Integer tint = tintFor(ns, path);
            if (tint != null) shade(argb, tint);
            return argb;
        } catch (Exception e) {
            return null;
        }
    }

    /** Vanilla grass/leaves textures are grey and get colored by the biome in-game. */
    private static Integer tintFor(String ns, String path) {
        if (!ns.equals("minecraft")) return null;
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.equals("spruce_leaves")) return 0x619961;
        if (name.equals("birch_leaves")) return 0x80A755;
        if (name.endsWith("leaves") && !name.startsWith("cherry") && !name.startsWith("azalea") && !name.startsWith("flowering")) return 0x48B518;
        if (name.equals("vine") || name.equals("lily_pad")) return 0x48B518;
        if (name.equals("grass_block_top") || name.equals("grass") || name.equals("tall_grass_top") || name.equals("tall_grass_bottom")
                || name.equals("fern") || name.equals("large_fern_top") || name.equals("large_fern_bottom") || name.equals("grass_block_side_overlay")) return 0x91BD59;
        if (name.startsWith("leather_") && !name.contains("overlay")) return 0xA06540;
        return null;
    }

    private static void shade(BufferedImage img, int rgb) {
        int tr = (rgb >> 16) & 0xFF, tg = (rgb >> 8) & 0xFF, tb = rgb & 0xFF;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int a = p >>> 24;
                if (a == 0) continue;
                int r = ((p >> 16) & 0xFF) * tr / 255, g = ((p >> 8) & 0xFF) * tg / 255, b = (p & 0xFF) * tb / 255;
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private static BufferedImage flat(Map<String, String> textures) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pixelGraphics(out);
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            String ref = tex(textures, "layer" + i);
            if (ref == null) continue;
            BufferedImage t = loadTexture(ref);
            if (t == null) continue;
            g.drawImage(t, 0, 0, SIZE, SIZE, null);
            any = true;
        }
        g.dispose();
        return any ? out : null;
    }

    private static BufferedImage flatSingle(String ref) {
        BufferedImage t = loadTexture(ref);
        if (t == null) return null;
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pixelGraphics(out);
        g.drawImage(t, 0, 0, SIZE, SIZE, null);
        g.dispose();
        return out;
    }

    /** A little isometric block like the game's inventory: top, left (front) and right sides. */
    private static BufferedImage cube(Map<String, String> textures) {
        String top = tex(textures, "up", "top", "end", "all", "texture", "side", "particle");
        String left = tex(textures, "south", "front", "north", "side", "all", "texture", "particle");
        String right = tex(textures, "east", "side", "west", "all", "texture", "particle");
        if (top == null && left == null && right == null) return null;
        BufferedImage tTop = top != null ? loadTexture(top) : null;
        BufferedImage tLeft = left != null ? loadTexture(left) : null;
        BufferedImage tRight = right != null ? loadTexture(right) : null;
        if (tTop == null) tTop = tLeft != null ? tLeft : tRight;
        if (tLeft == null) tLeft = tRight != null ? tRight : tTop;
        if (tRight == null) tRight = tLeft;
        if (tTop == null) return null;

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pixelGraphics(out);
        face(g, darken(tLeft, 0.8), new AffineTransform(1.875, 0.9375, 0, 1.875, 2, 16));
        face(g, darken(tRight, 0.6), new AffineTransform(1.875, -0.9375, 0, 1.875, 32, 31));
        face(g, tTop, new AffineTransform(1.875, 0.9375, -1.875, 0.9375, 32, 1));
        g.dispose();
        return out;
    }

    private static void face(Graphics2D g, BufferedImage tex, AffineTransform at) {
        at.scale(16.0 / tex.getWidth(), 16.0 / tex.getHeight());
        g.drawImage(tex, at, null);
    }

    private static BufferedImage darken(BufferedImage src, double f) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = p >>> 24;
                int r = (int) (((p >> 16) & 0xFF) * f), gg = (int) (((p >> 8) & 0xFF) * f), b = (int) ((p & 0xFF) * f);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        return out;
    }

    private static Graphics2D pixelGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        return g;
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best effort
                }
            });
        } catch (Exception ignored) {
            // best effort
        }
    }
}
