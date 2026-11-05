package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Leaderboards from Minecraft's own player stats (playtime, kills, deaths, blocks
 * mined, distance) plus every scoreboard (RPG levels and such). Keeps a daily
 * snapshot so it can show "this week" as well as all time.
 */
final class Leaderboards {
    private static final String[][] STATS = {
            {"playtime", "Playtime", "ticks"},
            {"mobKills", "Mob kills", ""},
            {"playerKills", "Player kills", ""},
            {"deaths", "Deaths", ""},
            {"mined", "Blocks mined", ""},
            {"distance", "Distance traveled", "cm"},
    };
    private static final String[] DISTANCE_STATS = {"walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm", "walk_on_water_one_cm",
            "walk_under_water_one_cm", "fly_one_cm", "aviate_one_cm", "boat_one_cm", "horse_one_cm", "minecart_one_cm", "pig_one_cm", "strider_one_cm", "climb_one_cm"};

    private final MinecraftServer server;
    private final Path baselineFile;
    private final TreeMap<String, Map<String, Map<String, Long>>> baselines = new TreeMap<>(); // date -> uuid -> metric -> value
    private volatile String cached;
    private volatile long cachedAt;
    private int ticks = 1180;

    Leaderboards(MinecraftServer server, Path baselineFile) {
        this.server = server;
        this.baselineFile = baselineFile;
        load();
    }

    /** Server thread. Once a minute: save stats of online players, take today's snapshot if missing. */
    void tick() {
        if (++ticks < 1200) return;
        ticks = 0;
        String today = LocalDate.now().toString();
        if (!baselines.containsKey(today)) {
            saveOnlineStats();
            baselines.put(today, collect());
            while (baselines.size() > 40) baselines.pollFirstEntry();
            save();
        }
    }

    /** Server thread: makes sure stat files on disk are current. */
    void saveOnlineStats() {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try {
                p.getStatHandler().save();
            } catch (Exception ignored) {
                // stats will be a little behind
            }
        }
    }

    /** Server thread (cached for a minute). */
    String json() {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < 60_000) return cached;
        saveOnlineStats();
        Map<String, Map<String, Long>> current = collect();

        // baseline about a week ago (or the oldest we have)
        Map<String, Map<String, Long>> base = null;
        String cutoff = LocalDate.now().minusDays(7).toString();
        for (Map.Entry<String, Map<String, Map<String, Long>>> e : baselines.entrySet()) {
            if (e.getKey().compareTo(cutoff) <= 0 || base == null) base = e.getValue();
            if (e.getKey().compareTo(cutoff) > 0) break;
        }

        JsonObject root = new JsonObject();
        JsonArray cats = new JsonArray();
        for (String[] s : STATS) {
            JsonObject c = new JsonObject();
            c.addProperty("key", s[0]);
            c.addProperty("name", s[1]);
            c.addProperty("unit", s[2]);
            cats.add(c);
        }
        Scoreboard sb = server.getScoreboard();
        for (ScoreboardObjective obj : sb.getObjectives()) {
            JsonObject c = new JsonObject();
            c.addProperty("key", "sb:" + obj.getName());
            c.addProperty("name", obj.getDisplayName().getString());
            c.addProperty("unit", "");
            cats.add(c);
        }
        root.add("categories", cats);

        JsonArray players = new JsonArray();
        for (Map.Entry<String, Map<String, Long>> e : current.entrySet()) {
            String uuid = e.getKey();
            String name = nameOf(uuid);
            if (name == null) continue;
            JsonObject p = new JsonObject();
            p.addProperty("uuid", uuid);
            p.addProperty("name", name);
            p.addProperty("online", server.getPlayerManager().getPlayer(UUID.fromString(uuid)) != null);
            JsonObject all = new JsonObject(), week = new JsonObject();
            Map<String, Long> b = base != null ? base.get(uuid) : null;
            for (Map.Entry<String, Long> m : e.getValue().entrySet()) {
                all.addProperty(m.getKey(), m.getValue());
                long before = b != null && b.containsKey(m.getKey()) ? b.get(m.getKey()) : 0;
                week.addProperty(m.getKey(), Math.max(0, m.getValue() - before));
            }
            p.add("all", all);
            p.add("week", week);
            players.add(p);
        }
        root.add("players", players);
        String first = baselines.isEmpty() ? LocalDate.now().toString() : baselines.firstKey();
        root.addProperty("trackingSince", first);
        cached = root.toString();
        cachedAt = now;
        return cached;
    }

    /** uuid -> metric -> value for everyone who has a stats file, plus scoreboard values. */
    private Map<String, Map<String, Long>> collect() {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        Path dir = server.getSavePath(WorldSavePath.STATS);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    String fn = f.getFileName().toString();
                    if (!fn.endsWith(".json")) continue;
                    String uuid = fn.substring(0, fn.length() - 5);
                    try {
                        UUID.fromString(uuid);
                        out.put(uuid, readStats(f));
                    } catch (Exception ignored) {
                        // not a player stats file
                    }
                }
            } catch (Exception e) {
                EverwynWatch.LOG.warn("Couldn't read player stats", e);
            }
        }
        // scoreboards (by player name -> uuid)
        Scoreboard sb = server.getScoreboard();
        Map<String, String> nameToUuid = new HashMap<>();
        for (String uuid : out.keySet()) {
            String n = nameOf(uuid);
            if (n != null) nameToUuid.put(n, uuid);
        }
        for (ScoreboardObjective obj : sb.getObjectives()) {
            for (ScoreboardPlayerScore score : sb.getAllPlayerScores(obj)) {
                String uuid = nameToUuid.get(score.getPlayerName());
                if (uuid != null) out.get(uuid).put("sb:" + obj.getName(), (long) score.getScore());
            }
        }
        return out;
    }

    private static Map<String, Long> readStats(Path f) throws Exception {
        Map<String, Long> m = new LinkedHashMap<>();
        JsonObject stats;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            stats = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("stats");
        }
        JsonObject custom = stats != null && stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : new JsonObject();
        m.put("playtime", get(custom, "minecraft:play_time") + get(custom, "minecraft:play_one_minute"));
        m.put("mobKills", get(custom, "minecraft:mob_kills"));
        m.put("playerKills", get(custom, "minecraft:player_kills"));
        m.put("deaths", get(custom, "minecraft:deaths"));
        long dist = 0;
        for (String d : DISTANCE_STATS) dist += get(custom, "minecraft:" + d);
        m.put("distance", dist);
        long mined = 0;
        if (stats != null && stats.has("minecraft:mined")) {
            for (Map.Entry<String, JsonElement> e : stats.getAsJsonObject("minecraft:mined").entrySet()) mined += e.getValue().getAsLong();
        }
        m.put("mined", mined);
        return m;
    }

    private static long get(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsLong() : 0;
    }

    private String nameOf(String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) return p.getGameProfile().getName();
            if (server.getUserCache() != null) {
                Optional<GameProfile> gp = server.getUserCache().getByUuid(id);
                if (gp.isPresent()) return gp.get().getName();
            }
        } catch (Exception ignored) {
            // unknown
        }
        return null;
    }

    /** Known players (uuid + name) for the offline player picker. */
    List<String[]> knownPlayers() {
        List<String[]> out = new ArrayList<>();
        Path dir = server.getSavePath(WorldSavePath.STATS);
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String fn = f.getFileName().toString();
                if (!fn.endsWith(".json")) continue;
                String uuid = fn.substring(0, fn.length() - 5);
                String n = nameOf(uuid);
                if (n != null) out.add(new String[]{uuid, n});
            }
        } catch (Exception ignored) {
            // none
        }
        return out;
    }

    private void load() {
        if (!Files.exists(baselineFile)) return;
        try (Reader r = Files.newBufferedReader(baselineFile, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> day : o.entrySet()) {
                Map<String, Map<String, Long>> players = new HashMap<>();
                for (Map.Entry<String, JsonElement> pl : day.getValue().getAsJsonObject().entrySet()) {
                    Map<String, Long> metrics = new HashMap<>();
                    for (Map.Entry<String, JsonElement> m : pl.getValue().getAsJsonObject().entrySet()) metrics.put(m.getKey(), m.getValue().getAsLong());
                    players.put(pl.getKey(), metrics);
                }
                baselines.put(day.getKey(), players);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read leaderboard history", e);
        }
    }

    private void save() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Map<String, Map<String, Long>>> day : baselines.entrySet()) {
            JsonObject players = new JsonObject();
            for (Map.Entry<String, Map<String, Long>> pl : day.getValue().entrySet()) {
                JsonObject metrics = new JsonObject();
                pl.getValue().forEach(metrics::addProperty);
                players.add(pl.getKey(), metrics);
            }
            o.add(day.getKey(), players);
        }
        try {
            Files.createDirectories(baselineFile.getParent());
            try (Writer w = Files.newBufferedWriter(baselineFile, StandardCharsets.UTF_8)) {
                new Gson().toJson(o, w);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not save leaderboard history", e);
        }
    }
}
