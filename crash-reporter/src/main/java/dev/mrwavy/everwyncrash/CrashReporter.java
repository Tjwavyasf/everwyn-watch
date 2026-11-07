package dev.mrwavy.everwyncrash;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sends crash reports to the server's Everwyn Watch app.
 *
 * - When the game crashes, Minecraft writes crash-reports/crash-...txt and exits; a shutdown hook sends it.
 * - Anything that couldn't be sent (no internet, the PC lost power, Java itself crashed: hs_err_pid*.log,
 *   or the game never got far enough to start this mod, e.g. "incompatible mods") is sent on the next launch.
 *
 * Settings: config/everwyn-crash-reporter.properties (url, key, enabled). Sent files are remembered in
 * config/everwyn-crash-reporter-sent.txt so nothing is sent twice.
 */
public final class CrashReporter implements PreLaunchEntrypoint {
    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final long FIRST_RUN_WINDOW_MS = 3L * 24 * 3600 * 1000; // first run: only the last 3 days
    private static final String[] LOG_MARKERS = {
            "Incompatible mods found", "Mod resolution failed", "Could not execute entrypoint", "FormattedException",
            "---- Minecraft Crash Report ----", "Mixin apply failed", "MixinApplyError", "MixinTransformerError",
            "Encountered an unexpected exception", "java.lang.OutOfMemoryError"
    };

    private Path gameDir, configFile, sentFile;
    private String url, key, pack, player = "";
    private int modCount;
    private final Set<String> sent = new HashSet<>();
    private final Set<String> crashesAtStart = new HashSet<>();

    @Override
    public void onPreLaunch() {
        try {
            FabricLoader fl = FabricLoader.getInstance();
            start(fl.getGameDir(), fl.getConfigDir(), launchName(fl), fl.getAllMods().size());
        } catch (Throwable e) {
            log("couldn't start: " + e);
        }
    }

    /** Everything after Fabric hands over (separate so it can be tested without a game). */
    void start(Path gameDir, Path config, String player, int modCount) {
        this.player = player == null ? "" : player;
        this.modCount = modCount;
        try {
            this.gameDir = gameDir;
            configFile = config.resolve("everwyn-crash-reporter.properties");
            sentFile = config.resolve("everwyn-crash-reporter-sent.txt");
            if (!loadSettings()) return;
            boolean firstRun = !Files.exists(sentFile);
            if (!firstRun) sent.addAll(Files.readAllLines(sentFile, StandardCharsets.UTF_8));
            for (Path p : list(gameDir.resolve("crash-reports"), "crash-")) crashesAtStart.add(p.getFileName().toString());

            // On a crash Minecraft saves the report and exits: send it on the way out
            Runtime.getRuntime().addShutdownHook(new Thread(this::sendNewCrashes, "EverwynCrashReporter-Exit"));

            // Earlier ones that never got sent, in the background so the game starts normally
            Thread t = new Thread(() -> sendBacklog(firstRun), "EverwynCrashReporter");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            log("couldn't start: " + e);
        }
    }

    /** Reads (or creates) the settings file. Returns false when switched off or not set up. */
    private boolean loadSettings() throws Exception {
        Properties p = new Properties();
        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        } else {
            try (Writer w = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                w.write("# Everwyn Crash Reporter: sends your game's crash reports to the server admins.\n");
                w.write("# The admins get these lines from the Everwyn Watch app (Server tab > Crash reports).\n");
                w.write("# Set enabled=false to stop sending.\n");
                w.write("enabled=true\nurl=\nkey=\npack=\n");
            }
        }
        url = p.getProperty("url", "").trim().replaceAll("/+$", "");
        key = p.getProperty("key", "").trim();
        pack = p.getProperty("pack", "").trim();
        boolean enabled = !"false".equalsIgnoreCase(p.getProperty("enabled", "true").trim());
        if (!enabled) return false;
        if (url.isEmpty() || key.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            log("not set up yet (url/key missing in " + configFile.getFileName() + ")");
            return false;
        }
        return true;
    }

    /** Shutdown hook: crash reports that appeared while this game was running. */
    private void sendNewCrashes() {
        try {
            for (Path p : list(gameDir.resolve("crash-reports"), "crash-")) {
                String name = p.getFileName().toString();
                if (crashesAtStart.contains(name) || sent.contains(name)) continue;
                send(p, "crash", Duration.ofSeconds(6));
            }
        } catch (Throwable e) {
            log("couldn't send the crash report now, will try next launch: " + e);
        }
    }

    /** Crash reports, Java crash logs and failed-launch logs that haven't been sent yet. */
    private void sendBacklog(boolean firstRun) {
        try {
            long since = firstRun ? System.currentTimeMillis() - FIRST_RUN_WINDOW_MS : 0;
            List<Object[]> todo = new ArrayList<>();
            for (Path p : list(gameDir.resolve("crash-reports"), "crash-")) todo.add(new Object[]{p, "crash"});
            for (Path p : list(gameDir, "hs_err_pid")) todo.add(new Object[]{p, "java"});
            for (Path p : list(gameDir.resolve("logs"), "")) {
                String n = p.getFileName().toString();
                if (n.endsWith(".log.gz") && !n.startsWith("debug")) todo.add(new Object[]{p, "log"});
            }
            for (Object[] item : todo) {
                Path p = (Path) item[0];
                String name = p.getFileName().toString();
                if (sent.contains(name)) continue;
                if (Files.getLastModifiedTime(p).toMillis() < since) {
                    remember(name); // too old: don't send on the very first run
                    continue;
                }
                if ("log".equals(item[1]) && !looksLikeCrash(p)) {
                    remember(name);
                    continue;
                }
                send(p, (String) item[1], Duration.ofSeconds(20));
            }
        } catch (Throwable e) {
            log("couldn't send earlier crash reports: " + e);
        }
    }

    private boolean looksLikeCrash(Path gz) {
        try {
            String text = new String(readTail(gz, true), StandardCharsets.UTF_8);
            if (text.contains("Crash report saved to")) return false; // the crash report file already covers it
            for (String m : LOG_MARKERS) if (text.contains(m)) return true;
        } catch (Exception ignored) {
            // unreadable log: skip it
        }
        return false;
    }

    private synchronized void send(Path file, String kind, Duration timeout) throws Exception {
        String name = file.getFileName().toString();
        if (sent.contains(name)) return;
        byte[] body = readTail(file, name.endsWith(".gz"));
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(gz)) {
            out.write(body);
        }
        String q = "?kind=" + enc(kind) + "&file=" + enc(name) + "&player=" + enc(player) + "&pack=" + enc(pack)
                + "&mods=" + modCount;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/crash" + q))
                .timeout(timeout)
                .header("X-Crash-Key", key)
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("Content-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofByteArray(gz.toByteArray()))
                .build();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 || res.statusCode() == 409) { // 409: the server already has it
            remember(name);
            log("sent " + name + " to the server admins");
        } else if (res.statusCode() == 401 || res.statusCode() == 413) {
            remember(name); // wrong key or too big: retrying won't help
            log("the server refused " + name + " (HTTP " + res.statusCode() + ")");
        } else {
            log("couldn't send " + name + " (HTTP " + res.statusCode() + "), will try again next launch");
        }
    }

    /** The whole file, or its last 3 MB if it's bigger (the end has the error). */
    private static byte[] readTail(Path file, boolean gzipped) throws Exception {
        try (InputStream raw = Files.newInputStream(file); InputStream in = gzipped ? new GZIPInputStream(raw) : raw) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES * 3) { // keep memory sane on huge logs: keep the newest part
                    byte[] all = out.toByteArray();
                    out.reset();
                    out.write(all, (int) (all.length - MAX_BYTES), (int) MAX_BYTES);
                }
            }
            byte[] all = out.toByteArray();
            if (all.length <= MAX_BYTES) return all;
            byte[] tail = new byte[(int) MAX_BYTES];
            System.arraycopy(all, (int) (all.length - MAX_BYTES), tail, 0, tail.length);
            return tail;
        }
    }

    private synchronized void remember(String name) {
        if (!sent.add(name)) return;
        try {
            Files.writeString(sentFile, String.join("\n", sent) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("couldn't save " + sentFile.getFileName() + ": " + e);
        }
    }

    private static List<Path> list(Path dir, String prefix) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().forEach(out::add);
        } catch (Exception ignored) {
            // folder not readable
        }
        return out;
    }

    /** The Minecraft name the launcher started the game with. */
    private static String launchName(FabricLoader fl) {
        try {
            String[] args = fl.getLaunchArguments(false);
            for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--username")) return args[i + 1];
        } catch (Throwable ignored) {
            // not available
        }
        return "";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static void log(String msg) {
        System.out.println("[Everwyn Crash Reporter] " + msg);
    }
}
