package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny built-in web server (no extra libraries) that serves the app and its API. */
public final class WebServer {
    private static final String WEB_ROOT = "/assets/everwyn-watch/web/";
    private static final Map<String, String> STATIC = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/manifest.webmanifest", "manifest.webmanifest",
            "/icon-192.png", "icon-192.png",
            "/icon-512.png", "icon-512.png",
            "/apple-touch-icon.png", "apple-touch-icon.png");

    private final MinecraftServer server;
    private final WatchConfig cfg;
    private final Snapshotter snapshotter;
    private final EventLog events;
    private final TileRenderer tiles;
    private final Markers markers;
    private final byte[] tokenBytes;
    private final Map<String, int[]> failures = new ConcurrentHashMap<>(); // ip -> {count, windowStartSeconds}
    private HttpServer http;
    private ExecutorService pool;

    public WebServer(MinecraftServer server, WatchConfig cfg, Snapshotter snapshotter, EventLog events, TileRenderer tiles, Markers markers) {
        this.markers = markers;
        this.server = server;
        this.cfg = cfg;
        this.snapshotter = snapshotter;
        this.events = events;
        this.tiles = tiles;
        this.tokenBytes = cfg.token.getBytes(StandardCharsets.UTF_8);
    }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(cfg.bindAddress, cfg.port), 0);
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "EverwynWatch-Web-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        http.createContext("/", this::handle);
        http.start();
    }

    public void stop() {
        if (http != null) http.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                api(ex, path);
            } else if (STATIC.containsKey(path) && "GET".equals(ex.getRequestMethod())) {
                serveStatic(ex, STATIC.get(path));
            } else {
                send(ex, 404, "text/plain", "Not found");
            }
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Dashboard request failed", e);
            try {
                sendJson(ex, 500, error("Server error"));
            } catch (Exception ignored) {
                // connection already gone
            }
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws Exception {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        if (isLockedOut(ip)) {
            sendJson(ex, 429, error("Too many wrong tokens. Try again in a few minutes."));
            return;
        }
        String given = ex.getRequestHeaders().getFirst("X-Watch-Token");
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), tokenBytes)) {
            recordFailure(ip);
            sendJson(ex, 401, error("Wrong token"));
            return;
        }
        failures.remove(ip);

        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        switch (path) {
            case "/api/ping" -> {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("serverName", cfg.serverName);
                sendJson(ex, 200, o.toString());
            }
            case "/api/state" -> sendJson(ex, 200, snapshotter.json());
            case "/api/events" -> {
                long after = parseLong(q.get("after"), 0);
                sendJson(ex, 200, events.since(after).toString());
            }
            case "/api/tile" -> {
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                int x = (int) parseLong(q.get("x"), 0);
                int z = (int) parseLong(q.get("z"), 0);
                TileRenderer.Result r = tiles.get(dim, x, z);
                if (r.busy()) {
                    sendJson(ex, 503, error("busy"));
                } else if (r.png() == null) {
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    ex.sendResponseHeaders(204, -1);
                } else {
                    send(ex, 200, "image/png", r.png());
                }
            }
            case "/api/action" -> {
                if (!"POST".equals(ex.getRequestMethod())) {
                    sendJson(ex, 405, error("Use POST"));
                    return;
                }
                if (!cfg.allowAdminActions) {
                    sendJson(ex, 403, error("Admin actions are turned off in the config"));
                    return;
                }
                byte[] body = ex.getRequestBody().readNBytes(8192);
                JsonObject req = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                try {
                    String msg = server.submit(() -> AdminActions.run(server, events, markers, req)).get(5, TimeUnit.SECONDS);
                    server.execute(snapshotter::refresh); // show the change in the app right away
                    JsonObject o = new JsonObject();
                    o.addProperty("ok", true);
                    o.addProperty("message", msg);
                    sendJson(ex, 200, o.toString());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        sendJson(ex, 400, error(cause.getMessage()));
                    } else {
                        throw e;
                    }
                }
            }
            default -> sendJson(ex, 404, error("Unknown endpoint"));
        }
    }

    private boolean isLockedOut(String ip) {
        int[] f = failures.get(ip);
        if (f == null) return false;
        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec - f[1] > 600) {
            failures.remove(ip);
            return false;
        }
        return f[0] >= 10;
    }

    private void recordFailure(String ip) {
        int nowSec = (int) (System.currentTimeMillis() / 1000);
        failures.compute(ip, (k, f) -> {
            if (f == null || nowSec - f[1] > 600) return new int[]{1, nowSec};
            f[0]++;
            return f;
        });
    }

    private void serveStatic(HttpExchange ex, String file) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream(WEB_ROOT + file)) {
            if (in == null) {
                send(ex, 404, "text/plain", "Missing " + file);
                return;
            }
            byte[] data = in.readAllBytes();
            String type = file.endsWith(".html") ? "text/html; charset=utf-8"
                    : file.endsWith(".png") ? "image/png"
                    : file.endsWith(".webmanifest") ? "application/manifest+json"
                    : "application/octet-stream";
            send(ex, 200, type, data);
        }
    }

    private static String error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", msg);
        return o.toString();
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json);
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        send(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            m.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
