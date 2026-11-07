package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Crash reports from players' games (sent by the Everwyn Crash Reporter mod in the modpack) and from this
 * server itself. Each is saved as a text file in config/everwyn-watch-crashes/ with a short summary
 * (what went wrong, the root cause, and which mods show up in the error) in index.json.
 */
final class CrashReports {
    private static final int KEEP = 300;
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final Pattern FRAME = Pattern.compile("^\\s*at ([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+)\\.[^.(]+\\(");
    private static final String[] BORING = {"java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "com.mojang.",
            "net.fabricmc.", "org.spongepowered.", "org.lwjgl.", "io.netty.", "com.google.", "it.unimi.", "org.apache.",
            "org.slf4j.", "cpw.", "oshi.", "org.objectweb.", "kotlin.", "kotlinx.", "dev.mrwavy.everwyncrash."};

    private final Path dir, indexFile;
    private final List<JsonObject> index = new ArrayList<>(); // newest last
    private final Map<String, List<Long>> uploadsByIp = new ConcurrentHashMap<>();

    CrashReports(Path dir) {
        this.dir = dir;
        this.indexFile = dir.resolve("index.json");
        try {
            if (Files.exists(indexFile)) {
                for (JsonElement el : JsonParser.parseString(Files.readString(indexFile)).getAsJsonArray()) index.add(el.getAsJsonObject());
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read {}", indexFile, e);
        }
    }

    /** Saves a report sent by a player's game. Returns the summary, or null if it was already here. */
    synchronized JsonObject addFromPlayer(String ip, byte[] body, boolean gzipped, String player, String kind, String file, String pack, String mods) throws Exception {
        List<Long> times = uploadsByIp.computeIfAbsent(ip, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        times.removeIf(t -> now - t > 3_600_000);
        if (times.size() >= 20) throw new IllegalStateException("Too many crash reports from this address. Try again later.");
        times.add(now);
        byte[] raw = gzipped ? gunzip(body) : body;
        String text = new String(raw, StandardCharsets.UTF_8);
        String name = clean(player, 16, "[A-Za-z0-9_]");
        String fileName = clean(file, 80, "[A-Za-z0-9_.\\-]");
        for (JsonObject o : index) { // same player sending the same file twice
            if (name.equals(o.get("player").getAsString()) && fileName.equals(o.get("file").getAsString())) return null;
        }
        return add("player", name, switch (kind == null ? "" : kind) {
            case "java", "log" -> kind;
            default -> "crash";
        }, fileName, clean(pack, 60, "[A-Za-z0-9 _.\\-]"), mods == null ? "" : mods.replaceAll("[^0-9]", ""), text);
    }

    /** Adds this server's own crash reports (crash-reports/ in the server folder) that aren't here yet. */
    synchronized void importServerReports() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("crash-reports");
        if (!Files.isDirectory(folder)) return;
        Set<String> have = new LinkedHashSet<>();
        for (JsonObject o : index) if ("server".equals(o.get("source").getAsString())) have.add(o.get("file").getAsString());
        long since = System.currentTimeMillis() - 14L * 24 * 3600 * 1000;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String n = p.getFileName().toString();
                if (!n.startsWith("crash-") || have.contains(n) || Files.getLastModifiedTime(p).toMillis() < since) continue;
                byte[] data = Files.readAllBytes(p);
                if (data.length > MAX_BYTES) data = java.util.Arrays.copyOfRange(data, (int) (data.length - MAX_BYTES), data.length);
                add("server", "", "crash", n, "", "", new String(data, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read the server's crash reports", e);
        }
    }

    private JsonObject add(String source, String player, String kind, String file, String pack, String mods, String text) throws Exception {
        long now = System.currentTimeMillis();
        String id = Long.toString(now, 36) + Integer.toString((int) (Math.random() * 1296), 36);
        JsonObject o = summarize(text);
        o.addProperty("id", id);
        o.addProperty("source", source);
        o.addProperty("player", player);
        o.addProperty("kind", kind);
        o.addProperty("file", file);
        o.addProperty("pack", pack);
        o.addProperty("mods", mods);
        o.addProperty("time", now);
        o.addProperty("size", text.length());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".txt"), text, StandardCharsets.UTF_8);
        index.add(o);
        while (index.size() > KEEP) {
            JsonObject old = index.remove(0);
            Files.deleteIfExists(dir.resolve(old.get("id").getAsString() + ".txt"));
        }
        save();
        return o;
    }

    /**
     * The useful part of a crash report: Minecraft's one-line description, the root cause (last "Caused by"),
     * Fabric's "incompatible mods" lines, and the first few non-Minecraft packages in the stack trace, which
     * usually name the mod that crashed.
     */
    static JsonObject summarize(String text) {
        JsonObject o = new JsonObject();
        String desc = null, cause = null, first = null;
        List<String> problems = new ArrayList<>();
        Set<String> mentions = new LinkedHashSet<>();
        boolean inProblems = false;
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (desc == null && t.startsWith("Description: ")) desc = t.substring(13);
            if (t.startsWith("Caused by: ")) cause = t.substring(11);
            if (first == null && t.matches("^[a-zA-Z_$][\\w$.]*(Exception|Error|Throwable)(:.*)?$")) first = t;
            if (t.contains("Incompatible mods found") || t.contains("Mod resolution failed")) inProblems = true;
            else if (inProblems && (t.startsWith("- ") || t.startsWith("\t- ")) && problems.size() < 8) problems.add(t.substring(2));
            else if (inProblems && t.isEmpty() && !problems.isEmpty()) inProblems = false;
            if (t.startsWith("# Problematic frame:")) cause = "Java crashed (native code)";
            if (t.startsWith("C  [") && cause != null && cause.startsWith("Java crashed") && mentions.size() < 3) mentions.add(nativeName(t.substring(3).split("\\+")[0].replace("[", "").replace("]", "").trim()));
            Matcher m = FRAME.matcher(line);
            if (m.find() && mentions.size() < 4) {
                String cls = m.group(1);
                boolean boring = false;
                for (String b : BORING) if (cls.startsWith(b)) { boring = true; break; }
                if (!boring) {
                    String[] parts = cls.split("\\.");
                    mentions.add(String.join(".", java.util.Arrays.copyOf(parts, Math.min(3, parts.length - 1))));
                }
            }
            if (t.contains("Mixin apply failed") || t.contains("MixinApplyError")) {
                Matcher mm = Pattern.compile("([\\w-]+)\\.mixins?\\.json").matcher(t);
                if (mm.find()) mentions.add("mixins of " + mm.group(1));
            }
        }
        String root = cause != null ? cause : first;
        if (!problems.isEmpty() && root == null) root = "Incompatible mods";
        o.addProperty("description", desc == null ? "" : cap(desc, 200));
        o.addProperty("cause", root == null ? "" : cap(root, 300));
        JsonArray pr = new JsonArray();
        problems.forEach(p -> pr.add(cap(p, 200)));
        o.add("problems", pr);
        JsonArray me = new JsonArray();
        mentions.forEach(me::add);
        o.add("mentions", me);
        return o;
    }

    /** Native crash files people recognize: graphics drivers are the usual cause. */
    private static String nativeName(String dll) {
        String d = dll.toLowerCase(java.util.Locale.ROOT);
        if (d.startsWith("atio") || d.startsWith("amdxx") || d.startsWith("atig")) return dll + " (AMD graphics driver)";
        if (d.startsWith("nvoglv") || d.startsWith("nvgpucomp") || d.startsWith("libnvidia")) return dll + " (NVIDIA graphics driver)";
        if (d.startsWith("ig") && d.contains("icd")) return dll + " (Intel graphics driver)";
        if (d.startsWith("lwjgl")) return dll + " (Minecraft's graphics library)";
        return dll;
    }

    synchronized JsonArray list() {
        JsonArray out = new JsonArray();
        for (int i = index.size() - 1; i >= 0; i--) out.add(index.get(i));
        return out;
    }

    synchronized String text(String id) throws Exception {
        if (!id.matches("[a-z0-9]{1,20}")) throw new IllegalArgumentException("Unknown crash report");
        for (JsonObject o : index) if (o.get("id").getAsString().equals(id)) return Files.readString(dir.resolve(id + ".txt"));
        throw new IllegalArgumentException("That crash report no longer exists");
    }

    synchronized String delete(String id) {
        if ("all".equals(id)) {
            for (JsonObject o : index) rm(dir.resolve(o.get("id").getAsString() + ".txt"));
            int n = index.size();
            index.clear();
            save();
            return "Deleted " + n + " crash report" + (n == 1 ? "" : "s");
        }
        for (JsonObject o : index) {
            if (o.get("id").getAsString().equals(id)) {
                index.remove(o);
                rm(dir.resolve(id + ".txt"));
                save();
                return "Crash report deleted";
            }
        }
        throw new IllegalArgumentException("That crash report no longer exists");
    }

    private static void rm(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Could not delete {}", p, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            JsonArray arr = new JsonArray();
            index.forEach(arr::add);
            Path tmp = indexFile.resolveSibling("index.json.tmp");
            Files.writeString(tmp, arr.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not save {}", indexFile, e);
        }
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalArgumentException("too big");
            }
            return out.toByteArray();
        }
    }

    private static String clean(String s, int max, String allowed) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (String.valueOf(c).matches(allowed) && b.length() < max) b.append(c);
        return b.toString();
    }

    private static String cap(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
