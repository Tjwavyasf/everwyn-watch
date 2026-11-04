package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;

/**
 * Builds the JSON the app polls. Runs on the server thread every second or so,
 * so web requests never touch live game objects.
 */
public final class Snapshotter {
    private final MinecraftServer server;
    private final WatchConfig cfg;
    private final Markers markers;
    private final Challenges challenges;
    private volatile String json = "{}";
    private double avgMspt = 0;

    public Snapshotter(MinecraftServer server, WatchConfig cfg, Markers markers, Challenges challenges) {
        this.challenges = challenges;
        this.server = server;
        this.cfg = cfg;
        this.markers = markers;
    }

    public String json() {
        return json;
    }

    /** Called every tick with that tick's length; keeps a smoothed average. */
    public void recordTick(double ms) {
        avgMspt = avgMspt == 0 ? ms : avgMspt * 0.95 + ms * 0.05;
    }

    public void refresh() {
        JsonObject root = new JsonObject();
        root.addProperty("time", System.currentTimeMillis());
        root.addProperty("serverName", cfg.serverName);
        root.addProperty("maxPlayers", server.getMaxPlayerCount());
        root.addProperty("mspt", Math.round(avgMspt * 10) / 10.0);
        root.addProperty("tps", Math.round(Math.min(20.0, 1000.0 / Math.max(50.0, avgMspt)) * 10) / 10.0);
        root.addProperty("adminActions", cfg.allowAdminActions);

        JsonArray worlds = new JsonArray();
        for (ServerWorld w : server.getWorlds()) {
            JsonObject wo = new JsonObject();
            wo.addProperty("id", w.getRegistryKey().getValue().toString());
            wo.addProperty("timeOfDay", w.getTimeOfDay() % 24000);
            wo.addProperty("raining", w.isRaining());
            wo.addProperty("thundering", w.isThundering());
            wo.addProperty("players", w.getPlayers().size());
            worlds.add(wo);
        }
        root.add("worlds", worlds);

        // World spawn, so the app can open there when nobody is online
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            net.minecraft.util.math.BlockPos sp = overworld.getSpawnPos();
            JsonObject spawn = new JsonObject();
            spawn.addProperty("dim", overworld.getRegistryKey().getValue().toString());
            spawn.addProperty("x", sp.getX());
            spawn.addProperty("y", sp.getY());
            spawn.addProperty("z", sp.getZ());
            root.add("spawn", spawn);
        }

        JsonArray players = new JsonArray();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try {
                players.add(player(p));
            } catch (Exception e) {
                EverwynWatch.LOG.debug("Skipped player in snapshot", e);
            }
        }
        root.add("players", players);

        JsonObject m = markers.toJson();
        root.add("waypoints", m.get("waypoints"));
        root.add("deaths", m.get("deaths"));
        root.add("challenges", challenges.toJson());

        json = root.toString();
    }

    private JsonObject player(ServerPlayerEntity p) {
        JsonObject o = new JsonObject();
        String name = p.getGameProfile().getName();
        o.addProperty("name", name);
        o.addProperty("uuid", p.getUuidAsString());
        o.addProperty("dim", p.getWorld().getRegistryKey().getValue().toString());
        o.addProperty("x", Math.round(p.getX() * 10) / 10.0);
        o.addProperty("y", Math.round(p.getY() * 10) / 10.0);
        o.addProperty("z", Math.round(p.getZ() * 10) / 10.0);
        o.addProperty("yaw", Math.round(p.getYaw()));
        o.addProperty("health", Math.round(p.getHealth() * 10) / 10.0);
        o.addProperty("maxHealth", Math.round(p.getMaxHealth() * 10) / 10.0);
        o.addProperty("food", p.getHungerManager().getFoodLevel());
        o.addProperty("level", p.experienceLevel);
        o.addProperty("gamemode", p.interactionManager.getGameMode().getName());
        o.addProperty("ping", p.pingMilliseconds);
        o.addProperty("sneaking", p.isSneaking());
        o.addProperty("sleeping", p.isSleeping());

        String origin = OriginsCompat.origin(p);
        if (origin != null) o.addProperty("origin", origin);

        // Every scoreboard objective the player has a score in (RPG levels, kills, etc.)
        Scoreboard sb = server.getScoreboard();
        JsonArray scores = new JsonArray();
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (sb.playerHasObjective(name, obj)) {
                JsonObject s = new JsonObject();
                s.addProperty("name", obj.getDisplayName().getString());
                s.addProperty("value", sb.getPlayerScore(name, obj).getScore());
                scores.add(s);
            }
        }
        o.add("scores", scores);

        if (cfg.showInventory) {
            JsonObject inv = new JsonObject();
            inv.add("main", items(p.getInventory().main));
            inv.add("armor", items(p.getInventory().armor));
            inv.add("offhand", items(p.getInventory().offHand));
            inv.addProperty("selected", p.getInventory().selectedSlot);
            o.add("inventory", inv);
        }
        return o;
    }

    private static JsonArray items(DefaultedList<ItemStack> list) {
        JsonArray arr = new JsonArray();
        for (ItemStack s : list) {
            if (s.isEmpty()) {
                arr.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            JsonObject it = new JsonObject();
            it.addProperty("id", Registries.ITEM.getId(s.getItem()).toString());
            it.addProperty("name", s.getName().getString());
            it.addProperty("count", s.getCount());
            if (s.hasEnchantments()) it.addProperty("ench", true);
            if (s.isDamageable() && s.getMaxDamage() > 0) {
                it.addProperty("dur", Math.round(100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()));
            }
            arr.add(it);
        }
        return arr;
    }
}
