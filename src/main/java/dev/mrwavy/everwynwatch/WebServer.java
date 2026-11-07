package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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

    private final Services s;
    private final Map<String, int[]> failures = new ConcurrentHashMap<>(); // ip -> {count, windowStartSeconds}
    private HttpServer http;
    private ExecutorService pool;

    WebServer(Services s) {
        this.s = s;
    }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(s.cfg.bindAddress, s.cfg.port), 0);
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newFixedThreadPool(6, r -> {
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
            boolean get = "GET".equals(ex.getRequestMethod());
            if (path.equals("/icon") && get) {
                sendImage(ex, IconRenderer.icon(query(ex).getOrDefault("id", "")), 604800);
            } else if (path.equals("/skin") && get) {
                String u = query(ex).getOrDefault("u", "");
                sendImage(ex, u.matches("[0-9a-fA-F-]{32,36}") ? Faces.skin(u) : null, 3600);
            } else if (path.equals("/face") && get) {
                String u = query(ex).getOrDefault("u", "");
                sendImage(ex, u.matches("[0-9a-fA-F-]{32,36}") ? Faces.face(u) : null, 3600);
            } else if ((path.equals("/board") || path.equals("/board.json")) && get) {
                if (!s.cfg.publicLeaderboard) {
                    send(ex, 404, "text/plain", "The leaderboard isn't public.");
                } else if (path.equals("/board")) {
                    serveStatic(ex, "board.html");
                } else {
                    String json = onServer(s.boards::json, 20);
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    o.addProperty("serverName", s.cfg.serverName);
                    sendJson(ex, 200, o.toString());
                }
            } else if (path.startsWith("/api/")) {
                api(ex, path);
            } else if (STATIC.containsKey(path) && get) {
                serveStatic(ex, STATIC.get(path));
            } else {
                send(ex, 404, "text/plain", "Not found");
            }
        } catch (BadRequest | IllegalArgumentException e) {
            try {
                sendJson(ex, 400, error(e.getMessage()));
            } catch (Exception ignored) {
                // connection already gone
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
        Accounts.Account me = s.accounts.auth(ex.getRequestHeaders().getFirst("X-Watch-Token"));
        if (me == null) {
            recordFailure(ip);
            sendJson(ex, 401, error("Wrong token"));
            return;
        }
        failures.remove(ip);
        boolean staff = me.role() != Accounts.Role.VIEWER;
        boolean admin = me.role() == Accounts.Role.OWNER || me.role() == Accounts.Role.ADMIN;

        Map<String, String> q = query(ex);
        switch (path) {
            case "/api/ping" -> {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("serverName", s.cfg.serverName);
                sendJson(ex, 200, o.toString());
            }
            case "/api/state" -> {
                String state = s.snapshotter.json();
                sendJson(ex, 200, "{\"me\":" + Accounts.describe(me) + "," + state.substring(1));
            }
            case "/api/deaths" -> sendJson(ex, 200, onServer(() -> s.vault.summaries().toString(), 5));
            case "/api/death" -> {
                String id = q.getOrDefault("id", "");
                sendJson(ex, 200, onServer(() -> s.vault.detail(id).toString(), 5));
            }
            case "/api/catalog" -> sendJson(ex, 200, onServer(Catalog::json, 20));
            case "/api/tile" -> {
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                TileRenderer.Result r = s.tiles.get(dim, (int) parseLong(q.get("x"), 0), (int) parseLong(q.get("z"), 0));
                if (r.png() != null) {
                    send(ex, 200, "image/png", r.png());
                } else if (r.busy()) {
                    sendJson(ex, 503, error("busy"));
                } else {
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    ex.sendResponseHeaders(204, -1);
                }
            }
            case "/api/heights" -> {
                // Block heights for one map tile, for the 3D view (gzipped little-endian int16, 128x128).
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                TileRenderer.Result r = s.tiles.get(dim, (int) parseLong(q.get("x"), 0), (int) parseLong(q.get("z"), 0));
                if (r.png() == null && !r.busy()) {
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    ex.sendResponseHeaders(204, -1);
                } else if (r.heights() == null) {
                    sendJson(ex, 503, error("busy"));
                } else {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
                        gz.write(TileRenderer.heightBytes(r.heights()));
                    }
                    ex.getResponseHeaders().set("Content-Encoding", "gzip");
                    send(ex, 200, "application/octet-stream", bos.toByteArray());
                }
            }
            case "/api/chunk" -> {
                // Every block of one chunk for the "real blocks" 3D view (gzipped JSON, see ChunkExport)
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                try {
                    byte[] gz = ChunkExport.get(s.server, dim, (int) parseLong(q.get("x"), 0), (int) parseLong(q.get("z"), 0));
                    if (gz == null) {
                        ex.getResponseHeaders().set("Cache-Control", "no-store");
                        ex.sendResponseHeaders(204, -1);
                    } else {
                        ex.getResponseHeaders().set("Content-Encoding", "gzip");
                        send(ex, 200, "application/json; charset=utf-8", gz);
                    }
                } catch (ChunkExport.Busy b) {
                    sendJson(ex, 503, error("busy"));
                }
            }
            case "/api/asset" -> {
                // Block models, blockstates and textures (vanilla + mods) for the 3D view
                String p = q.getOrDefault("p", "");
                byte[] data = IconRenderer.asset(p);
                if (data == null) {
                    ex.getResponseHeaders().set("Cache-Control", "public, max-age=600");
                    ex.sendResponseHeaders(404, -1);
                } else {
                    ex.getResponseHeaders().set("Content-Type", p.endsWith(".png") ? "image/png" : "application/json; charset=utf-8");
                    ex.getResponseHeaders().set("Cache-Control", "private, max-age=86400");
                    ex.sendResponseHeaders(200, data.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(data);
                    }
                }
            }
            case "/api/live" -> {
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                double x = parseLong(q.get("x"), 0), z = parseLong(q.get("z"), 0);
                int r = (int) parseLong(q.get("r"), 64);
                sendJson(ex, 200, onServer(() -> Live.json(s.server, dim, x, z, r), 5));
            }
            case "/api/mobs" -> {
                String dim = q.getOrDefault("dim", "minecraft:overworld");
                int x0 = (int) parseLong(q.get("x0"), 0), z0 = (int) parseLong(q.get("z0"), 0);
                int x1 = (int) parseLong(q.get("x1"), 0), z1 = (int) parseLong(q.get("z1"), 0);
                sendJson(ex, 200, onServer(() -> Mobs.near(s.server, dim, x0, z0, x1, z1).toString(), 5));
            }
            case "/api/events" -> sendJson(ex, 200, s.events.since(parseLong(q.get("after"), 0)).toString());
            case "/api/trail" -> sendJson(ex, 200, s.trails.trail(q.getOrDefault("player", ""), (int) parseLong(q.get("hours"), 1)).toString());
            case "/api/perf" -> sendJson(ex, 200, s.perf.history((int) parseLong(q.get("hours"), 1)).toString());
            case "/api/mob-guard" -> {
                if (!admin) { sendJson(ex, 403, error("Only admins can view mob protection")); return; }
                sendJson(ex, 200, onServer(() -> s.mobGuard.status().toString(), 5));
            }
            case "/api/lag" -> sendJson(ex, 200, onServer(() -> PerfMonitor.lagReport(s.server).toString(), 10));
            case "/api/leaderboard" -> sendJson(ex, 200, onServer(s.boards::json, 20));
            case "/api/whitelist" -> {
                if (!admin) { sendJson(ex, 403, error("Only admins can view the whitelist")); return; }
                JsonArray arr = new JsonArray();
                for (String name : onServer(() -> s.server.getPlayerManager().getWhitelistedNames(), 5)) arr.add(name);
                sendJson(ex, 200, arr.toString());
            }
            case "/api/players" -> {
                JsonArray arr = new JsonArray();
                for (String[] p : onServer(s.boards::knownPlayers, 10)) {
                    JsonObject o = new JsonObject();
                    o.addProperty("uuid", p[0]);
                    o.addProperty("name", p[1]);
                    arr.add(o);
                }
                sendJson(ex, 200, arr.toString());
            }
            case "/api/inventory" -> {
                if (!staff) {
                    sendJson(ex, 403, error("Viewers can't open inventories"));
                    return;
                }
                String name = q.getOrDefault("player", "");
                sendJson(ex, 200, onServer(() -> Inventories.get(s.server, name).toString(), 10));
            }
            case "/api/ledger" -> {
                if (!staff) {
                    sendJson(ex, 403, error("Viewers can't use Ledger"));
                    return;
                }
                JsonObject o = s.ledger.lookup(q.getOrDefault("dim", "minecraft:overworld"), (int) parseLong(q.get("x"), 0),
                        (int) parseLong(q.get("z"), 0), (int) parseLong(q.get("radius"), 8), (int) parseLong(q.get("hours"), 24),
                        q.get("player"), (int) parseLong(q.get("limit"), 200));
                sendJson(ex, 200, o.toString());
            }
            case "/api/audio" -> {
                if (!admin) {
                    sendJson(ex, 403, error("Only admins can listen"));
                    return;
                }
                if (!VoiceBridge.svcLoaded) {
                    sendJson(ex, 409, error("novoice"));
                    return;
                }
                net.minecraft.server.network.ServerPlayerEntity p = s.server.getPlayerManager().getPlayer(q.getOrDefault("player", ""));
                if (p == null) {
                    sendJson(ex, 404, error("offline"));
                    return;
                }
                send(ex, 200, "application/octet-stream", VoiceBridge.read(p.getUuid(), parseLong(q.get("after"), 0)));
            }
            case "/api/mod-upload" -> {
                if (me.role() != Accounts.Role.OWNER) { sendJson(ex, 403, error("Only the owner can upload mods")); return; }
                if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use POST")); return; }
                if (!s.cfg.allowAdminActions) { sendJson(ex, 403, error("Admin actions are disabled")); return; }
                String filename = ex.getRequestHeaders().getFirst("X-Mod-Filename");
                String result = SingleModUpload.install(ex.getRequestBody(), filename);
                s.accounts.record(me, "mod_upload", result);
                s.events.add("admin", me.name(), result);
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("message", result);
                sendJson(ex, 200, o.toString());
            }
            case "/api/restart" -> {
                if (me.role() != Accounts.Role.OWNER) { sendJson(ex, 403, error("Only the owner can restart")); return; }
                if ("POST".equals(ex.getRequestMethod())) {
                    if (!s.cfg.allowAdminActions) { sendJson(ex, 403, error("Admin actions are disabled")); return; }
                    onServer(() -> { s.restart.begin(); return true; }, 5);
                    s.accounts.record(me, "server_restart", "Requested restart in 10 seconds");
                } else if (!"GET".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use GET or POST")); return; }
                sendJson(ex, 200, onServer(() -> s.restart.status().toString(), 5));
            }
            case "/api/pack" -> {
                if (me.role() != Accounts.Role.OWNER) {
                    sendJson(ex, 403, error("Only the owner can install modpacks"));
                    return;
                }
                if ("POST".equals(ex.getRequestMethod())) {
                    PackInstaller.start(ex.getRequestBody(), 300L * 1024 * 1024);
                    s.accounts.record(me, "pack_install", "Started installing a modpack");
                    s.events.add("admin", me.name(), "Started installing a modpack");
                }
                sendJson(ex, 200, PackInstaller.status().toString());
            }
            case "/api/accounts" -> {
                if (!admin) {
                    sendJson(ex, 403, error("Only admins can see this"));
                    return;
                }
                JsonObject o = new JsonObject();
                o.add("accounts", me.role() == Accounts.Role.OWNER ? s.accounts.list() : new JsonArray());
                o.add("audit", s.accounts.auditLog(200));
                sendJson(ex, 200, o.toString());
            }
            case "/api/action" -> {
                if (!"POST".equals(ex.getRequestMethod())) {
                    sendJson(ex, 405, error("Use POST"));
                    return;
                }
                if (!s.cfg.allowAdminActions) {
                    sendJson(ex, 403, error("Admin actions are turned off in the config"));
                    return;
                }
                byte[] body = ex.getRequestBody().readNBytes(65536);
                JsonObject req = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                String action = req.has("action") ? req.get("action").getAsString() : "";
                if (!Accounts.allowed(me.role(), action)) {
                    sendJson(ex, 403, error("Your login (" + me.role().name().toLowerCase() + ") can't do that"));
                    return;
                }
                String msg = onServer(() -> AdminActions.run(s, me.name(), req), 8);
                s.accounts.record(me, action, msg.startsWith("TOKEN:") ? "Created login " + req.get("name").getAsString() : msg);
                s.server.execute(s.snapshotter::refresh); // show the change in the app right away
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                if (msg.startsWith("TOKEN:")) {
                    o.addProperty("token", msg.substring(6));
                    o.addProperty("message", "Login created");
                } else {
                    o.addProperty("message", msg);
                }
                sendJson(ex, 200, o.toString());
            }
            default -> sendJson(ex, 404, error("Unknown endpoint"));
        }
    }

    /** Runs on the server thread and turns "bad input" errors into a 400 for the app. */
    private <T> T onServer(Callable<T> task, int seconds) throws Exception {
        try {
            return s.server.submit(() -> {
                try {
                    return task.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).get(seconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) throw new BadRequest(iae.getMessage());
            throw e;
        }
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String msg) {
            super(msg);
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

    private static void sendImage(HttpExchange ex, byte[] png, int maxAge) throws IOException {
        if (png == null) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=" + maxAge);
        ex.sendResponseHeaders(200, png.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(png);
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

    private static Map<String, String> query(HttpExchange ex) {
        String raw = ex.getRequestURI().getRawQuery();
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

    private static long parseLong(String v, long def) {
        if (v == null) return def;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
