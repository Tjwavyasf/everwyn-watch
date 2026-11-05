package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/** Settings saved to config/everwyn-watch.json. */
public class WatchConfig {
    /** Port the dashboard listens on. Your host must allow this port. */
    public int port = 8125;
    /** Leave as 0.0.0.0 so you can reach it from your phone. */
    public String bindAddress = "0.0.0.0";
    /** Password for the dashboard. Generated on first start. Change it any time. */
    public String token = "";
    /** Shown at the top of the app. */
    public String serverName = "Everwyn SMP";
    /** How often (in ticks, 20 = 1 second) player data is refreshed. */
    public int snapshotIntervalTicks = 20;
    /** Show player inventories in the app. */
    public boolean showInventory = true;
    /** Allow kick / message / teleport / heal / gamemode from the app. */
    public boolean allowAdminActions = true;
    /** How many chat/feed lines to keep in memory. */
    public int eventBufferSize = 500;
    /** How long a rendered map tile is reused before it is redrawn. */
    public int tileCacheSeconds = 120;
    /** Limit on new map tiles drawn per second, to protect TPS. */
    public int maxTileRendersPerSecond = 6;
    /**
     * Shared waypoints use Xaero's newer "Internal_overworld_waypoints" style.
     * If players' [Add] button puts waypoints in the wrong world, set this to false
     * for the older "Internal-overworld-waypoints" style.
     */
    public boolean xaeroUnderscoreFormat = true;

    /** Push notifications through the free ntfy app. Subscribe to this topic in the app. */
    public boolean notificationsEnabled = true;
    public String ntfyServer = "https://ntfy.sh";
    public String ntfyTopic = "";
    public boolean notifyJoin = true;
    public boolean notifyFirstJoin = true;
    public boolean notifyLeave = false;
    public boolean notifyDeath = false;
    public boolean notifyChallenge = true;
    /** Optional: your dashboard address, so tapping a notification opens it. */
    public String dashboardUrl = "";

    /** How many deaths to keep in the death vault (all players combined). */
    public int deathHistoryLimit = 300;

    private transient Path path;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static WatchConfig load(Path path) {
        WatchConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, WatchConfig.class);
            } catch (Exception e) {
                EverwynWatch.LOG.error("Could not read {}, using defaults", path, e);
            }
        }
        if (cfg == null) cfg = new WatchConfig();
        if (cfg.token == null || cfg.token.isBlank()) cfg.token = randomToken();
        if (cfg.ntfyTopic == null || cfg.ntfyTopic.isBlank()) cfg.ntfyTopic = "everwyn-" + randomToken().substring(0, 16).toLowerCase();
        cfg.path = path;
        cfg.save(path);
        return cfg;
    }

    public void save() {
        if (path != null) save(path);
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            EverwynWatch.LOG.error("Could not save {}", path, e);
        }
    }

    private static String randomToken() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
