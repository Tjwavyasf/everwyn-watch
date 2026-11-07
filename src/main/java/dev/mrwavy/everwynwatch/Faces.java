package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Player heads (face + hat layer) cut from their real skins, for the map and lists. */
final class Faces {
    private static final Map<String, String> SKIN_URLS = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> SKINS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> SLIM = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(8)).build();

    private Faces() {}

    /** Reads the skin address from the player's profile. Returns true if they have one. */
    static boolean remember(ServerPlayerEntity p) {
        String uuid = p.getUuidAsString();
        String known = SKIN_URLS.get(uuid);
        if (known != null) return !known.isEmpty();
        String url = "";
        try {
            for (Property prop : p.getGameProfile().getProperties().get("textures")) {
                String json = new String(Base64.getDecoder().decode(prop.getValue()), StandardCharsets.UTF_8);
                JsonObject tex = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (tex != null && tex.has("SKIN")) {
                    JsonObject skin = tex.getAsJsonObject("SKIN");
                    url = skin.get("url").getAsString();
                    if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                            && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString())) SLIM.add(uuid);
                }
            }
        } catch (Exception ignored) {
            // no skin info (offline mode)
        }
        SKIN_URLS.put(uuid, url);
        return !url.isEmpty();
    }

    /** True if the player's skin uses the thin (Alex-style) arms. */
    static boolean slim(String uuid) {
        return SLIM.contains(uuid);
    }

    /** The player's whole skin PNG (for the 3D view), or null. Runs on a web thread. */
    static byte[] skin(String uuid) {
        byte[] cached = SKINS.get(uuid);
        if (cached != null) return cached.length == 0 ? null : cached;
        String url = SKIN_URLS.get(uuid);
        if (url == null || url.isEmpty()) return null;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !uri.getHost().endsWith("minecraft.net")) return null;
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) return null;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img == null || img.getWidth() < 64) return null;
            if (SKINS.size() > 500) SKINS.clear();
            SKINS.put(uuid, res.body());
            return res.body();
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Couldn't fetch skin for {}", uuid, e);
            return null;
        }
    }

    /** 32x32 PNG of the player's face, or null. Runs on a web thread. */
    static byte[] face(String uuid) {
        byte[] cached = CACHE.get(uuid);
        if (cached != null) return cached.length == 0 ? null : cached;
        String url = SKIN_URLS.get(uuid);
        if (url == null || url.isEmpty()) return null;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !uri.getHost().endsWith("minecraft.net")) return null;
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) return null;
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (skin == null || skin.getWidth() < 64) return null;
            int s = skin.getWidth() / 64; // HD skins
            BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(skin.getSubimage(8 * s, 8 * s, 8 * s, 8 * s), 0, 0, 32, 32, null);
            g.drawImage(skin.getSubimage(40 * s, 8 * s, 8 * s, 8 * s), 0, 0, 32, 32, null); // hat layer
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            byte[] png = bytes.toByteArray();
            if (CACHE.size() > 500) CACHE.clear();
            CACHE.put(uuid, png);
            return png;
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Couldn't fetch skin for {}", uuid, e);
            return null;
        }
    }
}
