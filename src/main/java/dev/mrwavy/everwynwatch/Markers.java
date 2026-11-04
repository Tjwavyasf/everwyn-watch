package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Waypoints made in the app and each player's last death spot.
 * Saved to config/everwyn-watch-markers.json so they survive restarts.
 */
public final class Markers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_WAYPOINTS = 300;

    private final Path file;
    private final Map<String, JsonObject> waypoints = new LinkedHashMap<>();
    private final Map<String, JsonObject> deaths = new LinkedHashMap<>(); // by player name

    public Markers(Path file) {
        this.file = file;
        load();
    }

    public synchronized JsonObject addWaypoint(String name, String dim, int x, int y, int z, int color) {
        if (waypoints.size() >= MAX_WAYPOINTS) throw new IllegalArgumentException("Too many waypoints (max " + MAX_WAYPOINTS + ")");
        JsonObject w = new JsonObject();
        String id = UUID.randomUUID().toString().substring(0, 8);
        w.addProperty("id", id);
        w.addProperty("name", name);
        w.addProperty("dim", dim);
        w.addProperty("x", x);
        w.addProperty("y", y);
        w.addProperty("z", z);
        w.addProperty("color", Math.floorMod(color, 16));
        w.addProperty("time", System.currentTimeMillis());
        waypoints.put(id, w);
        save();
        return w;
    }

    public synchronized JsonObject waypoint(String id) {
        JsonObject w = waypoints.get(id);
        if (w == null) throw new IllegalArgumentException("That waypoint no longer exists");
        return w.deepCopy();
    }

    public synchronized void removeWaypoint(String id) {
        if (waypoints.remove(id) == null) throw new IllegalArgumentException("That waypoint no longer exists");
        save();
    }

    public synchronized void recordDeath(String player, String dim, double x, double y, double z, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("player", player);
        d.addProperty("dim", dim);
        d.addProperty("x", (int) Math.floor(x));
        d.addProperty("y", (int) Math.floor(y));
        d.addProperty("z", (int) Math.floor(z));
        d.addProperty("message", message);
        d.addProperty("time", System.currentTimeMillis());
        deaths.remove(player); // keep newest last
        deaths.put(player, d);
        while (deaths.size() > 200) deaths.remove(deaths.keySet().iterator().next());
        save();
    }

    public synchronized JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonArray w = new JsonArray();
        waypoints.values().forEach(v -> w.add(v.deepCopy()));
        JsonArray d = new JsonArray();
        deaths.values().forEach(v -> d.add(v.deepCopy()));
        o.add("waypoints", w);
        o.add("deaths", d);
        return o;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("waypoints")) o.getAsJsonArray("waypoints").forEach(e -> {
                JsonObject w = e.getAsJsonObject();
                waypoints.put(w.get("id").getAsString(), w);
            });
            if (o.has("deaths")) o.getAsJsonArray("deaths").forEach(e -> {
                JsonObject d = e.getAsJsonObject();
                deaths.put(d.get("player").getAsString(), d);
            });
        } catch (Exception e) {
            EverwynWatch.LOG.error("Could not read {}", file, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(), w);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EverwynWatch.LOG.error("Could not save {}", file, e);
        }
    }
}
