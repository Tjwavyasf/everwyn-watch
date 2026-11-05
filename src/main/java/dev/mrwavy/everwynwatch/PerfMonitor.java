package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TPS / memory history, lag alerts, and a "what's causing lag" report. */
final class PerfMonitor {
    private static final int MAX_SAMPLES = 8640; // 24h at one sample per 10s
    private final WatchConfig cfg;
    private final ArrayDeque<double[]> samples = new ArrayDeque<>(); // time, tps, mspt, memUsedMb, memMaxMb, players, entities, chunks
    private int ticks;
    private long lowSince, lastAlert;
    private boolean alerted;

    PerfMonitor(WatchConfig cfg) {
        this.cfg = cfg;
    }

    /** Server thread. */
    void tick(MinecraftServer server, double mspt, double tps) {
        if (++ticks < 200) return;
        ticks = 0;
        long now = System.currentTimeMillis();
        Runtime rt = Runtime.getRuntime();
        double used = (rt.totalMemory() - rt.freeMemory()) / 1048576.0, max = rt.maxMemory() / 1048576.0;
        int entities = 0, chunks = 0;
        for (ServerWorld w : server.getWorlds()) {
            for (Entity ignored : w.iterateEntities()) entities++;
            chunks += w.getChunkManager().getLoadedChunkCount();
        }
        synchronized (this) {
            samples.addLast(new double[]{now, tps, mspt, Math.round(used), Math.round(max),
                    server.getPlayerManager().getCurrentPlayerCount(), entities, chunks});
            while (samples.size() > MAX_SAMPLES) samples.pollFirst();
        }

        // Lag alert after a full minute below the threshold, at most every 30 minutes
        if (tps < cfg.lagTpsThreshold) {
            if (lowSince == 0) lowSince = now;
            if (!alerted && now - lowSince >= 60_000 && now - lastAlert > 30 * 60_000) {
                alerted = true;
                lastAlert = now;
                String msg = String.format("TPS is %.1f (should be 20). %d entities, %d chunks loaded. Open the Server tab and tap Find lag.", tps, entities, chunks);
                Notifier.send("lag", cfg.serverName + " is lagging", msg, "warning");
                Discord.send("server", ":warning: **Server is lagging**: TPS " + String.format("%.1f", tps), null, null);
            }
        } else {
            if (alerted && tps >= 19) {
                Notifier.send("lag", cfg.serverName + " is back to normal", String.format("TPS is %.1f again.", tps), "white_check_mark");
                alerted = false;
            }
            lowSince = 0;
        }
    }

    synchronized JsonObject history(int hours) {
        long since = System.currentTimeMillis() - Math.max(1, Math.min(24, hours)) * 3_600_000L;
        JsonArray rows = new JsonArray();
        for (double[] s : samples) {
            if (s[0] < since) continue;
            JsonArray r = new JsonArray();
            r.add((long) s[0]);
            r.add(Math.round(s[1] * 10) / 10.0);
            r.add(Math.round(s[2] * 10) / 10.0);
            for (int i = 3; i < s.length; i++) r.add((long) s[i]);
            rows.add(r);
        }
        JsonObject o = new JsonObject();
        o.add("rows", rows);
        o.addProperty("columns", "time,tps,mspt,memUsedMb,memMaxMb,players,entities,chunks");
        return o;
    }

    /** Server thread: busiest chunks by entity count, and the most common entity types. */
    static JsonObject lagReport(MinecraftServer server) {
        record Key(String dim, int cx, int cz) {}
        Map<Key, Map<String, Integer>> perChunk = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        int items = 0, all = 0;
        for (ServerWorld w : server.getWorlds()) {
            String dim = w.getRegistryKey().getValue().toString();
            for (Entity e : w.iterateEntities()) {
                all++;
                if (e instanceof ItemEntity) items++;
                String type = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                totals.merge(type, 1, Integer::sum);
                Key k = new Key(dim, e.getChunkPos().x, e.getChunkPos().z);
                perChunk.computeIfAbsent(k, x -> new HashMap<>()).merge(type, 1, Integer::sum);
            }
        }
        List<Map.Entry<Key, Map<String, Integer>>> chunks = new ArrayList<>(perChunk.entrySet());
        chunks.sort((a, b) -> Integer.compare(sum(b.getValue()), sum(a.getValue())));
        JsonArray top = new JsonArray();
        for (int i = 0; i < Math.min(12, chunks.size()); i++) {
            Map.Entry<Key, Map<String, Integer>> e = chunks.get(i);
            JsonObject c = new JsonObject();
            c.addProperty("dim", e.getKey().dim());
            c.addProperty("cx", e.getKey().cx());
            c.addProperty("cz", e.getKey().cz());
            c.addProperty("x", e.getKey().cx() * 16 + 8);
            c.addProperty("z", e.getKey().cz() * 16 + 8);
            c.addProperty("count", sum(e.getValue()));
            c.add("types", topTypes(e.getValue(), 4));
            top.add(c);
        }
        JsonObject o = new JsonObject();
        o.addProperty("entities", all);
        o.addProperty("items", items);
        o.add("chunks", top);
        o.add("types", topTypes(totals, 10));
        int loaded = 0;
        for (ServerWorld w : server.getWorlds()) loaded += w.getChunkManager().getLoadedChunkCount();
        o.addProperty("loadedChunks", loaded);
        return o;
    }

    /** Removes dropped items (everywhere, or in one chunk). Server thread. */
    static int clearItems(MinecraftServer server, String dim, Integer cx, Integer cz) {
        int n = 0;
        for (ServerWorld w : server.getWorlds()) {
            if (dim != null && !w.getRegistryKey().getValue().toString().equals(dim)) continue;
            List<Entity> doomed = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (!(e instanceof ItemEntity)) continue;
                if (cx != null && (e.getChunkPos().x != cx || e.getChunkPos().z != cz)) continue;
                doomed.add(e);
            }
            for (Entity e : doomed) e.discard();
            n += doomed.size();
        }
        return n;
    }

    /** Removes hostile mobs in one chunk, skipping named or persistent ones. Server thread. */
    static int clearHostiles(MinecraftServer server, String dim, int cx, int cz) {
        int n = 0;
        for (ServerWorld w : server.getWorlds()) {
            if (!w.getRegistryKey().getValue().toString().equals(dim)) continue;
            List<Entity> doomed = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (!(e instanceof Monster) || !(e instanceof MobEntity mob)) continue;
                if (e.getChunkPos().x != cx || e.getChunkPos().z != cz) continue;
                if (mob.hasCustomName() || mob.isPersistent() || e.getCommandTags().contains(Challenges.MOB_TAG)) continue;
                doomed.add(e);
            }
            for (Entity e : doomed) e.discard();
            n += doomed.size();
        }
        return n;
    }

    private static int sum(Map<String, Integer> m) {
        int s = 0;
        for (int v : m.values()) s += v;
        return s;
    }

    private static JsonArray topTypes(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        JsonArray arr = new JsonArray();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            JsonObject t = new JsonObject();
            t.addProperty("type", list.get(i).getKey());
            t.addProperty("count", list.get(i).getValue());
            arr.add(t);
        }
        return arr;
    }
}
