package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative server-side hostile population cap. Never loads chunks. */
final class MobGuard {
    private final WatchConfig cfg;
    private int ticks, lastTotal, lastEligible, lastRemoved;
    private long totalRemoved, lastScanAt;
    private long emergencySince, lastEmergencyAt;
    private boolean emergency;

    MobGuard(WatchConfig cfg) { this.cfg = cfg; }

    /** Runs on the Minecraft server thread; counts loaded mobs only, once every ten seconds. */
    void tick(MinecraftServer server, double tps) {
        if (++ticks < 200) return;
        ticks = 0;
        if (!cfg.mobGuardEnabled) return;
        long now = System.currentTimeMillis();
        boolean below = tps < Math.max(5.0, Math.min(19.0, cfg.mobGuardEmergencyTps));
        if (below) {
            if (emergencySince == 0) emergencySince = now;
        } else {
            emergencySince = 0;
            emergency = false;
        }
        // Only switch to emergency limits after sustained lag, never due to one bad tick.
        boolean activeEmergency = below && emergencySince != 0 && now - emergencySince >= 60_000;
        emergency = activeEmergency;
        if (activeEmergency && now - lastEmergencyAt >= 30 * 60_000L) {
            lastEmergencyAt = now;
            Notifier.send("lag", cfg.serverName + " mob protection active", "TPS has remained below " + cfg.mobGuardEmergencyTps + ". Safely trimming excess hostile mobs.", "warning");
            Discord.send("server", ":warning: **Mob protection active** (sustained low TPS)", null, null);
        }
        int chunkLimit = clamp(cfg.mobGuardPerChunk, 1, 64);
        int worldLimit = clamp(cfg.mobGuardPerDimension, 20, 2000);
        int batchLimit = clamp(cfg.mobGuardMaxRemovedPerScan, 1, 100);
        if (activeEmergency) {
            chunkLimit = Math.max(2, chunkLimit * 3 / 4);
            worldLimit = Math.max(20, worldLimit * 3 / 4);
        }
        lastTotal = 0;
        lastEligible = 0;
        lastRemoved = 0;
        lastScanAt = now;
        for (ServerWorld world : server.getWorlds()) {
            Map<Long, List<MobEntity>> byChunk = new HashMap<>();
            List<MobEntity> eligible = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof Monster) || !(entity instanceof MobEntity mob)) continue;
                lastTotal++;
                if (!removable(mob, world, cfg.mobGuardPlayerDistance)) continue;
                lastEligible++;
                eligible.add(mob);
                long key = net.minecraft.util.math.ChunkPos.toLong(mob.getChunkPos().x, mob.getChunkPos().z);
                byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(mob);
            }
            if (eligible.isEmpty()) continue;
            // Order deterministic for ties; trim mobs farthest from players first.
            Comparator<MobEntity> order = Comparator.<MobEntity>comparingDouble(m -> nearestPlayerDistanceSquared(m, world)).reversed()
                    .thenComparing(m -> m.getUuid().toString());
            eligible.sort(order);
            Set<MobEntity> removals = new HashSet<>();
            for (List<MobEntity> group : byChunk.values()) {
                if (group.size() <= chunkLimit) continue;
                group.sort(order);
                for (int i = 0; i < group.size() - chunkLimit; i++) removals.add(group.get(i));
            }
            // World-wide limit is applied to non-protected, away-from-player mobs.
            for (int i = 0; i < eligible.size() - worldLimit; i++) removals.add(eligible.get(i));
            for (MobEntity mob : eligible) {
                if (lastRemoved >= batchLimit) break;
                if (!removals.contains(mob) || mob.isRemoved()) continue;
                if (!removable(mob, world, cfg.mobGuardPlayerDistance)) continue;
                mob.discard();
                lastRemoved++;
                totalRemoved++;
            }
            if (lastRemoved >= batchLimit) break;
        }
        if (lastRemoved > 0) EverwynWatch.LOG.info("MobGuard trimmed {} excess hostile mobs ({} total removed)", lastRemoved, totalRemoved);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static boolean removable(MobEntity mob, ServerWorld world, int minDistance) {
        if (mob.hasCustomName() || mob.isPersistent() || mob.getCommandTags().contains(Challenges.MOB_TAG) || mob.hasPassengers() || mob.hasVehicle()) return false;
        return nearestPlayerDistanceSquared(mob, world) > (long) clamp(minDistance, 16, 128) * clamp(minDistance, 16, 128);
    }

    private static double nearestPlayerDistanceSquared(MobEntity mob, ServerWorld world) {
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            nearest = Math.min(nearest, p.squaredDistanceTo(mob));
        }
        return nearest;
    }

    JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", cfg.mobGuardEnabled);
        o.addProperty("perChunk", cfg.mobGuardPerChunk);
        o.addProperty("perDimension", cfg.mobGuardPerDimension);
        o.addProperty("playerDistance", cfg.mobGuardPlayerDistance);
        o.addProperty("maxRemovedPerScan", cfg.mobGuardMaxRemovedPerScan);
        o.addProperty("emergencyTps", cfg.mobGuardEmergencyTps);
        o.addProperty("emergency", emergency);
        o.addProperty("lastScanAt", lastScanAt);
        o.addProperty("lastTotal", lastTotal);
        o.addProperty("lastEligible", lastEligible);
        o.addProperty("lastRemoved", lastRemoved);
        o.addProperty("totalRemoved", totalRemoved);
        return o;
    }
}
