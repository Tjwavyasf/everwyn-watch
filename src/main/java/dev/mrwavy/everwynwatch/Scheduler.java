package dev.mrwavy.everwynwatch;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Things that happen on their own: mob waves and care packages on set days and
 * times, chat announcements, and restart warnings (the restart itself is done by
 * Bisect's own schedule at the same time).
 */
final class Scheduler {
    private static final int[] WARN_AT = {300, 120, 60, 30, 10, 5, 4, 3, 2, 1};

    private final Services s;
    private final Path file;
    private final Map<String, JsonObject> jobs = new LinkedHashMap<>();
    private long lastMinute = -1;
    private long restartAt = 0;
    private int lastWarned = Integer.MAX_VALUE;
    private int ticks;

    Scheduler(Services s, Path file) {
        this.s = s;
        this.file = file;
        load();
    }

    /** Server thread, every tick. */
    void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        long nowMs = System.currentTimeMillis();
        restartCountdown(nowMs);
        long minute = nowMs / 60_000;
        if (minute == lastMinute) return;
        lastMinute = minute;
        for (JsonObject job : new ArrayList<>(jobs.values())) {
            try {
                String type = job.get("type").getAsString();
                if (type.equals("announce") && job.has("every") && job.get("every").getAsInt() > 0) {
                    if (minute % job.get("every").getAsInt() == 0) run(job);
                    continue;
                }
                ZoneId zone = zone(job);
                ZonedDateTime at = ZonedDateTime.now(zone);
                if (type.equals("restart")) at = at.plusMinutes(5); // warnings start 5 minutes early
                if (matches(job, at)) {
                    if (type.equals("restart")) {
                        restartAt = (minute + 5) * 60_000;
                        lastWarned = Integer.MAX_VALUE;
                    } else {
                        run(job);
                    }
                }
            } catch (Exception e) {
                EverwynWatch.LOG.error("Everwyn Watch schedule {} failed", job.get("name"), e);
            }
        }
    }

    private void restartCountdown(long nowMs) {
        if (restartAt == 0) return;
        int left = (int) Math.ceil((restartAt - nowMs) / 1000.0);
        if (left <= 0) {
            broadcast(Text.literal("[Server] ").formatted(Formatting.RED).append(Text.literal("Restarting now. Saving the world...").formatted(Formatting.WHITE)));
            s.server.getPlayerManager().saveAllPlayerData();
            s.server.saveAll(true, true, true);
            s.events.add("event", null, "Scheduled restart: world saved");
            restartAt = 0;
            return;
        }
        for (int w : WARN_AT) {
            if (left <= w && lastWarned > w) {
                lastWarned = w;
                String when = w >= 60 ? (w / 60) + " minute" + (w >= 120 ? "s" : "") : w + " second" + (w == 1 ? "" : "s");
                broadcast(Text.literal("[Server] ").formatted(Formatting.RED).append(Text.literal("Restarting in " + when + "!").formatted(Formatting.YELLOW)));
                break;
            }
        }
    }

    private static boolean matches(JsonObject job, ZonedDateTime t) {
        String[] hm = job.get("time").getAsString().split(":");
        if (t.getHour() != Integer.parseInt(hm[0]) || t.getMinute() != Integer.parseInt(hm[1])) return false;
        int dow = t.getDayOfWeek().getValue() % 7; // 0 = Sunday
        for (JsonElement d : job.getAsJsonArray("days")) {
            if (d.getAsInt() == dow) return true;
        }
        return false;
    }

    private static ZoneId zone(JsonObject job) {
        try {
            return ZoneId.of(job.has("tz") ? job.get("tz").getAsString() : "UTC");
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    /** Runs a job right now. Server thread. */
    String run(JsonObject job) {
        String type = job.get("type").getAsString();
        String result;
        switch (type) {
            case "announce" -> {
                broadcast(Text.literal("[" + s.cfg.adminName + "] ").formatted(Formatting.GOLD).append(Text.literal(job.get("text").getAsString()).formatted(Formatting.WHITE)));
                result = "Announcement sent";
            }
            case "restart" -> {
                restartAt = System.currentTimeMillis() + 300_000;
                lastWarned = Integer.MAX_VALUE;
                result = "Restart countdown started (5 minutes). Restart the server from Bisect when it finishes.";
            }
            case "wave", "package" -> result = AdminActions.run(s, "Schedule", job.getAsJsonObject("payload").deepCopy());
            default -> throw new IllegalArgumentException("Unknown schedule type");
        }
        job.addProperty("lastRun", System.currentTimeMillis());
        save();
        s.events.add("event", null, "Schedule \"" + job.get("name").getAsString() + "\": " + result);
        return result;
    }

    String add(JsonObject req) {
        if (jobs.size() >= 40) throw new IllegalArgumentException("Too many schedules");
        String type = req.get("type").getAsString();
        JsonObject job = new JsonObject();
        job.addProperty("id", UUID.randomUUID().toString().substring(0, 8));
        job.addProperty("type", type);
        String name = req.has("name") ? req.get("name").getAsString().trim() : "";
        job.addProperty("name", name.isEmpty() ? type : name.substring(0, Math.min(40, name.length())));
        job.addProperty("tz", req.has("tz") ? req.get("tz").getAsString() : "UTC");
        zone(job);
        int every = req.has("every") ? req.get("every").getAsInt() : 0;
        if (type.equals("announce") && every > 0) {
            job.addProperty("every", Math.max(5, Math.min(1440, every)));
        } else {
            String time = req.get("time").getAsString();
            if (!time.matches("\\d{1,2}:\\d{2}")) throw new IllegalArgumentException("Pick a time");
            job.addProperty("time", time);
            JsonArray days = req.getAsJsonArray("days");
            if (days == null || days.isEmpty()) throw new IllegalArgumentException("Pick at least one day");
            job.add("days", days);
        }
        switch (type) {
            case "announce" -> {
                String text = req.get("text").getAsString().replaceAll("\\p{Cntrl}", " ").trim();
                if (text.isEmpty()) throw new IllegalArgumentException("Write the announcement");
                job.addProperty("text", text.substring(0, Math.min(256, text.length())));
            }
            case "wave", "package" -> {
                JsonObject payload = req.getAsJsonObject("payload");
                if (payload == null) throw new IllegalArgumentException("Missing details");
                payload.addProperty("action", type.equals("wave") ? "wave_create" : "package");
                job.add("payload", payload);
            }
            case "restart" -> { }
            default -> throw new IllegalArgumentException("Unknown schedule type");
        }
        jobs.put(job.get("id").getAsString(), job);
        save();
        return "Scheduled \"" + job.get("name").getAsString() + "\"";
    }

    String remove(String id) {
        JsonObject job = jobs.remove(id);
        if (job == null) throw new IllegalArgumentException("That schedule is gone");
        save();
        return "Removed schedule \"" + job.get("name").getAsString() + "\"";
    }

    String runNow(String id) {
        JsonObject job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("That schedule is gone");
        return run(job);
    }

    JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (JsonObject j : jobs.values()) {
            JsonObject o = j.deepCopy();
            o.remove("payload");
            if (j.has("payload") && j.getAsJsonObject("payload").has("name")) o.add("target", j.getAsJsonObject("payload").get("name"));
            arr.add(o);
        }
        return arr;
    }

    long restartAt() {
        return restartAt;
    }

    private void broadcast(Text t) {
        s.server.getPlayerManager().broadcast(t, false);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) {
                JsonObject j = el.getAsJsonObject();
                jobs.put(j.get("id").getAsString(), j);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read schedules", e);
        }
    }

    private void save() {
        JsonArray arr = new JsonArray();
        jobs.values().forEach(arr::add);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(arr, w);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not save schedules", e);
        }
    }
}
