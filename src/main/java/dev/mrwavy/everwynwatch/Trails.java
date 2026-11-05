package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Where each player has been over the last 24 hours (a point every few seconds while they move). */
final class Trails {
    private static final long KEEP_SECONDS = 24 * 3600;
    private final Path file;
    private final List<String> dims = new ArrayList<>();
    private final Map<String, ArrayDeque<int[]>> byPlayer = new HashMap<>(); // name -> [time, dim, x, z]
    private int ticks;

    Trails(Path file) {
        this.file = file;
        load();
    }

    /** Server thread, every tick. */
    synchronized void tick(MinecraftServer server) {
        if (++ticks < 60) return; // every 3 seconds
        ticks = 0;
        int now = (int) (System.currentTimeMillis() / 1000);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String dim = p.getWorld().getRegistryKey().getValue().toString();
            int d = dims.indexOf(dim);
            if (d < 0) {
                dims.add(dim);
                d = dims.size() - 1;
            }
            int x = (int) Math.floor(p.getX()), z = (int) Math.floor(p.getZ());
            ArrayDeque<int[]> q = byPlayer.computeIfAbsent(p.getGameProfile().getName(), k -> new ArrayDeque<>());
            int[] last = q.peekLast();
            boolean moved = last == null || last[1] != d || Math.abs(last[2] - x) + Math.abs(last[3] - z) >= 3;
            if (moved || now - last[0] >= 120) q.addLast(new int[]{now, d, x, z});
        }
        if (now % 300 < 3) prune(now);
    }

    private void prune(int now) {
        for (Iterator<Map.Entry<String, ArrayDeque<int[]>>> it = byPlayer.entrySet().iterator(); it.hasNext(); ) {
            ArrayDeque<int[]> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst()[0] > KEEP_SECONDS) q.pollFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    synchronized JsonObject trail(String player, int hours) {
        int since = (int) (System.currentTimeMillis() / 1000) - Math.max(1, Math.min(24, hours)) * 3600;
        JsonObject o = new JsonObject();
        JsonArray dimArr = new JsonArray();
        dims.forEach(dimArr::add);
        o.add("dims", dimArr);
        JsonArray pts = new JsonArray();
        ArrayDeque<int[]> q = byPlayer.get(player);
        if (q != null) {
            for (int[] p : q) {
                if (p[0] < since) continue;
                JsonArray a = new JsonArray();
                for (int v : p) a.add(v);
                pts.add(a);
            }
        }
        o.add("points", pts);
        return o;
    }

    synchronized void save() {
        JsonObject o = new JsonObject();
        JsonArray dimArr = new JsonArray();
        dims.forEach(dimArr::add);
        o.add("dims", dimArr);
        JsonObject players = new JsonObject();
        for (Map.Entry<String, ArrayDeque<int[]>> e : byPlayer.entrySet()) {
            JsonArray pts = new JsonArray();
            for (int[] p : e.getValue()) {
                JsonArray a = new JsonArray();
                for (int v : p) a.add(v);
                pts.add(a);
            }
            players.add(e.getKey(), pts);
        }
        o.add("players", players);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new Gson().toJson(o, w);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not save trails", e);
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement d : o.getAsJsonArray("dims")) dims.add(d.getAsString());
            int now = (int) (System.currentTimeMillis() / 1000);
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("players").entrySet()) {
                ArrayDeque<int[]> q = new ArrayDeque<>();
                for (JsonElement pe : e.getValue().getAsJsonArray()) {
                    JsonArray a = pe.getAsJsonArray();
                    int[] p = {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()};
                    if (now - p[0] <= KEEP_SECONDS) q.addLast(p);
                }
                if (!q.isEmpty()) byPlayer.put(e.getKey(), q);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read trails", e);
        }
    }
}
