package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Turns a CurseForge modpack export (the zip with manifest.json) into the
 * server's mods and configs, right on the server:
 *  1. downloads every mod in the manifest from CurseForge
 *  2. leaves out mods that only work in players' games
 *     (but keeps any that a server mod needs)
 *  3. copies the pack's configs and KubeJS scripts
 *  4. moves the current mods into a backup folder and puts the new ones in
 * Nothing changes until every download has worked. Restart the server after.
 */
final class PackInstaller {
    /** CurseForge projects that only do something in a player's game (graphics, HUD, menus, sounds, zoom...). */
    static final Set<String> CLIENT_ONLY_SLUGS = Set.of(
            "sodium", "irisshaders", "indium", "magnesium-extras", "sodium-options-api", "reeses-sodium-options",
            "immediatelyfast", "moreculling", "entityculling-client", "cull-leaves", "badoptimizations", "fix-gpu-memory-leak",
            "dynamic-fps", "better-fps-render-distance-fabric", "create-better-fps", "enhanced-block-entities", "exordium",
            "continuity", "entity-model-features", "entity-texture-features-fabric", "fa-player-extension", "skin-layers-3d",
            "dynamiclights-reforged", "bobby", "modmenu", "fancymenu", "drippy-loading-screen", "konkrete-fabric", "spiffyhud",
            "controlling", "searchables", "mouse-tweaks", "keybind-fix", "studious-keybinds", "just-zoom", "dynamic-crosshair",
            "borderless-mining", "memory-settings", "fast-ip-ping", "lazy-language-loader", "resource-pack-overrides",
            "cosmetics", "presence-footsteps", "ambimod", "true-adaptive-music", "melody", "inventory-particles",
            "smooth-swapping", "smooth-movement", "mace-combat-backport-mace-3d-in-hand", "accurate-block-placement-reborn",
            "voice-physics", "crash-assistant");

    private static final Set<String> BUILTIN_IDS = Set.of("minecraft", "java", "fabricloader", "fabric");
    private static final Pattern MODLIST_ITEM = Pattern.compile("<li><a href=\"https://www\\.curseforge\\.com/minecraft/([^/]+)/([^\"]+)\">(.+?) \\(by [^)]*\\)</a></li>");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();

    private static volatile JsonObject status = idle();
    private static volatile Thread worker;

    private PackInstaller() {}

    private static JsonObject idle() {
        JsonObject o = new JsonObject();
        o.addProperty("state", "idle");
        return o;
    }

    static JsonObject status() {
        return status.deepCopy();
    }

    static synchronized boolean busy() {
        return worker != null && worker.isAlive();
    }

    /** Saves the uploaded zip and starts installing in the background. */
    static synchronized void start(InputStream body, long maxBytes) throws IOException {
        if (busy()) throw new IllegalArgumentException("A modpack install is already running");
        Path dir = gameDir().resolve("everwyn-pack");
        Files.createDirectories(dir);
        Path zip = dir.resolve("upload.zip");
        long n;
        try (InputStream in = body) {
            n = Files.copy(new LimitedStream(in, maxBytes), zip, StandardCopyOption.REPLACE_EXISTING);
        }
        if (n < 100) throw new IllegalArgumentException("That file is empty");
        try (ZipFile z = new ZipFile(zip.toFile())) {
            if (z.getEntry("manifest.json") == null) throw new IllegalArgumentException("That isn't a CurseForge modpack zip (no manifest.json inside)");
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("That file isn't a zip");
        }
        JsonObject s = new JsonObject();
        s.addProperty("state", "running");
        s.addProperty("step", "Starting");
        status = s;
        worker = new Thread(() -> {
            try {
                install(zip);
            } catch (Exception e) {
                EverwynWatch.LOG.error("Everwyn Watch modpack install failed", e);
                fail("Install failed: " + e.getMessage());
            }
        }, "EverwynWatch-Pack");
        worker.setDaemon(true);
        worker.start();
    }

    private static void install(Path zipPath) throws Exception {
        Path game = gameDir();
        Path work = game.resolve("everwyn-pack");
        Path staging = work.resolve("staging");
        deleteTree(staging);
        Files.createDirectories(staging);

        JsonObject manifest;
        List<String[]> slugs = new ArrayList<>(); // [category, slug, name] in manifest order
        try (ZipFile z = new ZipFile(zipPath.toFile())) {
            manifest = JsonParser.parseString(read(z, "manifest.json")).getAsJsonObject();
            ZipEntry ml = z.getEntry("modlist.html");
            if (ml != null) {
                Matcher m = MODLIST_ITEM.matcher(read(z, "modlist.html"));
                while (m.find()) slugs.add(new String[]{m.group(1), m.group(2), m.group(3)});
            }
        }
        String mc = manifest.getAsJsonObject("minecraft").get("version").getAsString();
        if (!mc.equals("1.20.1")) throw new IllegalStateException("This pack is for Minecraft " + mc + " but the server runs 1.20.1");
        String overridesDir = manifest.has("overrides") ? manifest.get("overrides").getAsString() : "overrides";

        // ---- 1. download every mod
        JsonArray files = manifest.getAsJsonArray("files");
        List<int[]> todo = new ArrayList<>();
        Map<String, String> slugOf = new HashMap<>(), nameOf = new HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.get(i).getAsJsonObject();
            int p = f.get("projectID").getAsInt(), fid = f.get("fileID").getAsInt();
            String key = p + "/" + fid;
            if (i < slugs.size()) {
                slugOf.put(key, slugs.get(i)[1]);
                nameOf.put(key, slugs.get(i)[2]);
                if (!slugs.get(i)[0].equals("mc-mods")) continue; // resource packs and shaders are for players only
            }
            todo.add(new int[]{p, fid});
        }
        progress("Downloading mods", 0, todo.size());
        AtomicInteger done = new AtomicInteger();
        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        Map<String, String> fileToKey = Collections.synchronizedMap(new HashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int[] t : todo) {
            pool.execute(() -> {
                String key = t[0] + "/" + t[1];
                try {
                    String fileName = download(t[0], t[1], staging);
                    if (fileName != null) fileToKey.put(fileName, key);
                } catch (Exception e) {
                    failed.add(nameOf.getOrDefault(key, "project " + t[0]) + " (" + e.getMessage() + ")");
                }
                progress("Downloading mods", done.incrementAndGet(), todo.size());
            });
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.HOURS);
        if (!failed.isEmpty()) {
            JsonObject s = status.deepCopy();
            s.addProperty("state", "failed");
            s.addProperty("message", failed.size() + " mod(s) wouldn't download, so nothing was changed. Try again in a few minutes.");
            JsonArray fa = new JsonArray();
            failed.forEach(fa::add);
            s.add("failed", fa);
            status = s;
            return;
        }

        // ---- 2. the pack's own jars (overrides/mods)
        try (ZipFile z = new ZipFile(zipPath.toFile())) {
            String prefix = overridesDir + "/mods/";
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.isDirectory() || !e.getName().startsWith(prefix) || !e.getName().endsWith(".jar")) continue;
                String fn = e.getName().substring(prefix.length());
                if (fn.contains("/")) continue;
                try (InputStream in = z.getInputStream(e)) {
                    Files.copy(in, staging.resolve(fn), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // ---- 3. sort out client-only mods
        progress("Checking which mods the server needs", 0, 0);
        Map<String, JarInfo> jars = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(staging, "*.jar")) {
            for (Path p : ds) jars.put(p.getFileName().toString(), JarInfo.read(p));
        }
        Set<String> skip = new HashSet<>();
        Map<String, String> why = new HashMap<>();
        for (Map.Entry<String, JarInfo> e : jars.entrySet()) {
            JarInfo j = e.getValue();
            String slug = slugOf.get(fileToKey.get(e.getKey()));
            if (!j.fabric) {
                skip.add(e.getKey());
                why.put(e.getKey(), "not a Fabric mod");
            } else if ("client".equals(j.environment)) {
                skip.add(e.getKey());
                why.put(e.getKey(), "only runs in players' games");
            } else if (slug != null && CLIENT_ONLY_SLUGS.contains(slug)) {
                skip.add(e.getKey());
                why.put(e.getKey(), "graphics/HUD/menu mod (players only)");
            }
        }
        // put back anything a kept mod needs
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> provided = new HashSet<>(BUILTIN_IDS);
            for (Map.Entry<String, JarInfo> e : jars.entrySet()) if (!skip.contains(e.getKey())) provided.addAll(e.getValue().provides);
            if (provided.contains("fabric-api")) provided.add("fabric");
            for (Map.Entry<String, JarInfo> e : jars.entrySet()) {
                if (skip.contains(e.getKey())) continue;
                for (String dep : e.getValue().depends) {
                    if (provided.contains(dep)) continue;
                    for (Map.Entry<String, JarInfo> s : jars.entrySet()) {
                        if (skip.contains(s.getKey()) && s.getValue().fabric && s.getValue().provides.contains(dep)) {
                            skip.remove(s.getKey());
                            why.remove(s.getKey());
                            changed = true;
                        }
                    }
                }
            }
        }
        // anything still missing (not in the pack at all) is only a warning
        Set<String> provided = new HashSet<>(BUILTIN_IDS);
        for (Map.Entry<String, JarInfo> e : jars.entrySet()) if (!skip.contains(e.getKey())) provided.addAll(e.getValue().provides);
        if (provided.contains("fabric-api")) provided.add("fabric");
        JsonArray missing = new JsonArray();
        for (Map.Entry<String, JarInfo> e : jars.entrySet()) {
            if (skip.contains(e.getKey())) continue;
            for (String dep : e.getValue().depends) {
                if (!provided.contains(dep) && !dep.startsWith("fabric-")) missing.add(e.getValue().name + " needs " + dep);
            }
        }

        // ---- 4. configs + KubeJS (only now that downloads worked)
        progress("Copying configs and scripts", 0, 0);
        int configFiles = 0;
        try (ZipFile z = new ZipFile(zipPath.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.isDirectory() || !e.getName().startsWith(overridesDir + "/")) continue;
                String rel = e.getName().substring(overridesDir.length() + 1);
                String top = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : "";
                if (!top.equals("config") && !top.equals("kubejs") && !top.equals("defaultconfigs")) continue;
                if (rel.contains("..")) continue;
                if (rel.equals("config/everwyn-watch.json")) continue; // never replace the dashboard's own settings
                Path out = game.resolve(rel).normalize();
                if (!out.startsWith(game)) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = z.getInputStream(e)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                configFiles++;
            }
        }

        // ---- 5. swap the mods folder (old mods go to a backup)
        progress("Swapping in the new mods", 0, 0);
        Path mods = game.resolve("mods");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        Path backup = game.resolve("mods-backup-" + stamp);
        Files.createDirectories(backup);
        Files.createDirectories(mods);
        int movedOld = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
            for (Path p : ds) {
                String fn = p.getFileName().toString();
                if (fn.startsWith("everwyn-watch") && fn.endsWith(".jar")) continue; // keep the dashboard itself
                Files.move(p, backup.resolve(fn), StandardCopyOption.REPLACE_EXISTING);
                movedOld++;
            }
        }
        int installed = 0;
        JsonArray skipped = new JsonArray();
        for (String fn : jars.keySet()) {
            if (skip.contains(fn)) {
                JsonObject o = new JsonObject();
                o.addProperty("file", fn);
                o.addProperty("name", jars.get(fn).name);
                o.addProperty("why", why.get(fn));
                skipped.add(o);
                continue;
            }
            if (fn.startsWith("everwyn-watch")) continue;
            Files.move(staging.resolve(fn), mods.resolve(fn), StandardCopyOption.REPLACE_EXISTING);
            installed++;
        }

        JsonObject s = new JsonObject();
        s.addProperty("state", "done");
        s.addProperty("installed", installed);
        s.addProperty("skippedCount", skipped.size());
        s.add("skipped", skipped);
        s.addProperty("configFiles", configFiles);
        s.addProperty("oldMods", movedOld);
        s.addProperty("backup", backup.getFileName().toString());
        s.add("missing", missing);
        s.addProperty("finishedAt", System.currentTimeMillis());
        s.addProperty("message", "Installed " + installed + " mods. Restart the server from Bisect to load them.");
        status = s;
        EverwynWatch.LOG.info("Everwyn Watch: modpack installed ({} mods, {} left out, old mods in {})", installed, skipped.size(), backup.getFileName());
    }

    /** Puts the mods from the last backup back (before restarting). */
    static synchronized String undo() throws IOException {
        if (busy()) throw new IllegalArgumentException("An install is still running");
        JsonObject s = status;
        if (!s.has("backup")) throw new IllegalArgumentException("There's nothing to undo");
        Path game = gameDir(), mods = game.resolve("mods"), backup = game.resolve(s.get("backup").getAsString());
        if (!Files.isDirectory(backup)) throw new IllegalArgumentException("The backup folder is gone");
        Path rejected = game.resolve("everwyn-pack").resolve("undone-" + System.currentTimeMillis());
        Files.createDirectories(rejected);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
            for (Path p : ds) {
                String fn = p.getFileName().toString();
                if (fn.startsWith("everwyn-watch") && fn.endsWith(".jar")) continue;
                Files.move(p, rejected.resolve(fn), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backup)) {
            for (Path p : ds) Files.move(p, mods.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(backup);
        status = idle();
        return "Your old mods are back. (Configs from the pack stay; restart to be safe.)";
    }

    /** Downloads one CurseForge file into dir. Returns the file name, or null if it isn't a mod jar. */
    private static String download(int project, int file, Path dir) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.curseforge.com/api/v1/mods/" + project + "/files/" + file + "/download"))
                        .header("User-Agent", "Mozilla/5.0 (EverwynWatch modpack installer)")
                        .timeout(Duration.ofMinutes(3)).build();
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
                String path = res.uri().getPath();
                String name = URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8).replaceAll("[\\\\/:*?\"<>|]", "_");
                if (!name.endsWith(".jar")) return null; // resource pack / shader zip
                Files.write(dir.resolve(name), res.body());
                return name;
            } catch (Exception e) {
                last = e;
                Thread.sleep(1500L * (attempt + 1));
            }
        }
        throw last;
    }

    private static void progress(String step, int done, int total) {
        JsonObject s = status.deepCopy();
        s.addProperty("state", "running");
        s.addProperty("step", step);
        s.addProperty("done", done);
        s.addProperty("total", total);
        status = s;
    }

    private static void fail(String message) {
        JsonObject s = status.deepCopy();
        s.addProperty("state", "failed");
        s.addProperty("message", message);
        status = s;
    }

    private static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    private static String read(ZipFile z, String name) throws IOException {
        try (InputStream in = z.getInputStream(z.getEntry(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            for (Path x : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        }
    }

    /** What a jar's fabric.mod.json says (including mods bundled inside it). */
    private static final class JarInfo {
        boolean fabric;
        String name = "?", environment = "*";
        final Set<String> provides = new HashSet<>();
        final Set<String> depends = new HashSet<>();

        static JarInfo read(Path jar) {
            JarInfo info = new JarInfo();
            try (ZipFile z = new ZipFile(jar.toFile())) {
                ZipEntry fmj = z.getEntry("fabric.mod.json");
                if (fmj == null) return info;
                info.fabric = true;
                JsonObject o = parse(z.getInputStream(fmj).readAllBytes());
                if (o == null) return info;
                info.name = o.has("name") ? o.get("name").getAsString() : o.get("id").getAsString();
                if (o.has("environment")) info.environment = o.get("environment").getAsString();
                addIds(o, info.provides);
                if (o.has("depends") && o.get("depends").isJsonObject()) info.depends.addAll(o.getAsJsonObject("depends").keySet());
                // bundled (jar-in-jar) mods count as provided
                if (o.has("jars") && o.get("jars").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("jars")) {
                        String inner = el.getAsJsonObject().get("file").getAsString();
                        ZipEntry ie = z.getEntry(inner);
                        if (ie == null) continue;
                        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(z.getInputStream(ie).readAllBytes()))) {
                            ZipEntry e;
                            while ((e = zin.getNextEntry()) != null) {
                                if (e.getName().equals("fabric.mod.json")) {
                                    JsonObject io = parse(zin.readAllBytes());
                                    if (io != null) addIds(io, info.provides);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // unreadable jar: treat as a normal mod
                info.fabric = true;
            }
            info.depends.removeAll(info.provides);
            return info;
        }

        private static void addIds(JsonObject o, Set<String> into) {
            if (o.has("id")) into.add(o.get("id").getAsString());
            if (o.has("provides") && o.get("provides").isJsonArray()) for (JsonElement p : o.getAsJsonArray("provides")) into.add(p.getAsString());
        }

        private static JsonObject parse(byte[] b) {
            try {
                // some mods put comments or stray characters in fabric.mod.json
                com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(new java.io.StringReader(new String(b, StandardCharsets.UTF_8).replace("﻿", "")));
                r.setLenient(true);
                return JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Stops reading after maxBytes so a huge upload can't fill the disk. */
    private static final class LimitedStream extends InputStream {
        private final InputStream in;
        private long left;

        LimitedStream(InputStream in, long max) {
            this.in = in;
            this.left = max;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) throw new IOException("File too big");
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (left <= 0) throw new IOException("File too big");
            int n = in.read(buf, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }
}
