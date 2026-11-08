package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Reads the Ledger mod's log (who broke, placed, took or put what) straight from its
 * SQLite file, read-only, and runs Ledger's own rollback command for undoing griefs.
 */
final class LedgerBridge {
    private final MinecraftServer server;
    private final Path db;
    private Map<String, String> tables;

    LedgerBridge(MinecraftServer server) {
        this.server = server;
        this.db = findDatabase(server);
    }

    /** For tests: a database file directly. */
    LedgerBridge(MinecraftServer server, Path db) {
        this.server = server;
        this.db = db;
    }

    /** Ledger keeps ledger.sqlite in the world folder unless config/ledger.toml sets database location. */
    private static Path findDatabase(MinecraftServer server) {
        Path fallback = server.getSavePath(WorldSavePath.ROOT).resolve("ledger.sqlite");
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve("ledger.toml");
            if (Files.exists(cfg)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*location\\s*=\\s*[\"']([^\"']+)[\"']").matcher(Files.readString(cfg));
                if (m.find()) {
                    Path custom = Path.of(m.group(1)).resolve("ledger.sqlite");
                    if (!custom.isAbsolute()) custom = FabricLoader.getInstance().getGameDir().resolve(custom);
                    if (Files.exists(custom)) return custom;
                }
            }
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Couldn't read Ledger's config", e);
        }
        return fallback;
    }

    boolean installed() {
        return FabricLoader.getInstance().isModLoaded("ledger");
    }

    String status() {
        if (!installed()) return "missing";
        if (!Files.exists(db)) return "nodb";
        return "ready";
    }

    /**
     * Ledger keeps its database in WAL mode, which SQLite often won't open read-only, so if the read-only
     * connection fails this opens it normally (still only reading, like Ledger's own lookups do).
     */
    private Connection connect() throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Ledger's database driver isn't available on this server (is Ledger set up for MySQL instead of SQLite?)");
        }
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        Properties p = new Properties();
        p.setProperty("open_mode", "1"); // read-only
        p.setProperty("busy_timeout", "5000");
        try {
            Connection c = DriverManager.getConnection(url, p);
            c.createStatement().executeQuery("SELECT 1 FROM sqlite_master LIMIT 1").close(); // make sure it really opened
            return c;
        } catch (Exception readOnlyFailed) {
            Properties q = new Properties();
            q.setProperty("busy_timeout", "5000");
            return DriverManager.getConnection(url, q);
        }
    }

    /** Table names differ in case between Ledger versions, so look them up. */
    private Map<String, String> tables(Connection c) throws Exception {
        if (tables != null) return tables;
        Map<String, String> t = new HashMap<>();
        try (ResultSet rs = c.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                String name = rs.getString(1);
                t.put(name.toLowerCase(Locale.ROOT).replace("_", ""), name);
            }
        }
        for (String need : new String[]{"actions", "actionidentifiers", "objectidentifiers", "worlds", "sources", "players"}) {
            if (!t.containsKey(need)) throw new IllegalStateException("Ledger database is missing table " + need);
        }
        tables = t;
        return t;
    }

    /** Recent changes in a square around (x, z). Runs on a web thread. Problems come back as a readable message. */
    JsonObject lookup(String dim, int x, int z, int radius, int hours, String player, int limit) {
        if (!"ready".equals(status())) throw new IllegalArgumentException(installed() ? "Ledger hasn't made its database yet" : "Ledger isn't installed on the server");
        try {
            return query(dim, x, z, radius, hours, player, limit);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) { // includes driver/native-library errors, which aren't Exceptions
            tables = null; // look the tables up again next time
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            EverwynWatch.LOG.warn("Everwyn Watch couldn't read Ledger's database {}", db, e);
            String why = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            throw new IllegalArgumentException("Couldn't read Ledger's database: " + why);
        }
    }

    private JsonObject query(String dim, int x, int z, int radius, int hours, String player, int limit) throws Exception {
        radius = Math.max(1, Math.min(64, radius));
        limit = Math.max(1, Math.min(500, limit));
        long since = hours > 0 ? System.currentTimeMillis() - hours * 3_600_000L : 0;
        JsonArray rows = new JsonArray();
        Map<String, Integer> byPlayer = new HashMap<>();
        try (Connection c = connect()) {
            Map<String, String> t = tables(c);
            String sql = "SELECT a.id, ai.action_identifier, a.time, a.x, a.y, a.z, o.identifier, oo.identifier, s.name, p.player_name, a.rolled_back "
                    + "FROM " + t.get("actions") + " a "
                    + "JOIN " + t.get("actionidentifiers") + " ai ON ai.id = a.action_id "
                    + "JOIN " + t.get("worlds") + " w ON w.id = a.world_id "
                    + "JOIN " + t.get("objectidentifiers") + " o ON o.id = a.object_id "
                    + "JOIN " + t.get("objectidentifiers") + " oo ON oo.id = a.old_object_id "
                    + "JOIN " + t.get("sources") + " s ON s.id = a.source "
                    + "LEFT JOIN " + t.get("players") + " p ON p.id = a.player_id "
                    + "WHERE w.identifier = ? AND a.x BETWEEN ? AND ? AND a.z BETWEEN ? AND ?"
                    + (player != null && !player.isBlank() ? " AND p.player_name = ?" : "")
                    + " ORDER BY a.id DESC LIMIT ?";
            try (PreparedStatement st = c.prepareStatement(sql)) {
                int i = 1;
                st.setString(i++, dim);
                st.setInt(i++, x - radius);
                st.setInt(i++, x + radius);
                st.setInt(i++, z - radius);
                st.setInt(i++, z + radius);
                if (player != null && !player.isBlank()) st.setString(i++, player);
                st.setInt(i, Math.min(2000, limit * 4));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next() && rows.size() < limit) {
                        long time = parseTime(rs.getString(3));
                        if (since > 0 && time > 0 && time < since) continue;
                        JsonObject r = new JsonObject();
                        r.addProperty("id", rs.getLong(1));
                        r.addProperty("action", rs.getString(2));
                        r.addProperty("time", time);
                        r.addProperty("x", rs.getInt(4));
                        r.addProperty("y", rs.getInt(5));
                        r.addProperty("z", rs.getInt(6));
                        r.addProperty("object", rs.getString(7));
                        r.addProperty("oldObject", rs.getString(8));
                        String who = rs.getString(10);
                        String source = rs.getString(9);
                        r.addProperty("who", who != null ? who : "@" + source);
                        r.addProperty("rolledBack", rs.getBoolean(11));
                        rows.add(r);
                        byPlayer.merge(who != null ? who : "@" + source, 1, Integer::sum);
                    }
                }
            }
        }
        JsonObject o = new JsonObject();
        o.add("actions", rows);
        JsonObject counts = new JsonObject();
        byPlayer.forEach(counts::addProperty);
        o.add("byPlayer", counts);
        return o;
    }

    /** Undo changes around a spot using Ledger's rollback. Server thread. */
    String rollback(String dim, int x, int y, int z, int radius, int hours, String player) {
        if (!"ready".equals(status())) throw new IllegalArgumentException("Ledger isn't available");
        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
        }
        if (world == null) throw new IllegalArgumentException("Unknown dimension");
        radius = Math.max(2, Math.min(64, radius));
        hours = Math.max(1, Math.min(24 * 30, hours));
        StringBuilder cmd = new StringBuilder("ledger rollback range:").append(radius).append(" after:").append(hours).append("h");
        if (player != null && !player.isBlank()) {
            if (!player.matches("@?[A-Za-z0-9_]{1,30}")) throw new IllegalArgumentException("Bad player name");
            cmd.append(" source:").append(player);
        }
        ServerCommandSource src = server.getCommandSource().withWorld(world).withPosition(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
        server.getCommandManager().executeWithPrefix(src, cmd.toString());
        return "Rolling back changes within " + radius + " blocks from the last " + hours + "h"
                + (player != null && !player.isBlank() ? " by " + player : "") + ". Ledger works in the background; give it a few seconds.";
    }

    private static long parseTime(String s) {
        if (s == null) return 0;
        try {
            long n = Long.parseLong(s.trim());
            return n > 100_000_000_000L ? n : n * 1000;
        } catch (NumberFormatException ignored) {
            // text timestamp
        }
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
            // no zone
        }
        try {
            String t = s.trim().replace(' ', 'T');
            if (t.length() > 26) t = t.substring(0, 26);
            return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
}
