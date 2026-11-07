package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Positions and poses for the 3D view, polled a few times a second while it's open: every player in the
 * dimension (crouching, swimming, sleeping, elytra, arm swing) and the mobs near where the camera looks.
 * Server thread; reads only what's already loaded.
 */
final class Live {
    private static final int MAX_MOBS = 250;

    private Live() {}

    static String json(MinecraftServer server, String dim, double cx, double cz, int radius) {
        JsonObject out = new JsonObject();
        out.addProperty("t", System.currentTimeMillis());
        JsonArray players = new JsonArray();
        JsonArray mobs = new JsonArray();
        out.add("players", players);
        out.add("mobs", mobs);
        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
        }
        if (world == null) return out.toString();

        for (ServerPlayerEntity p : world.getPlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getGameProfile().getName());
            o.addProperty("uuid", p.getUuidAsString());
            o.addProperty("x", round(p.getX()));
            o.addProperty("y", round(p.getY()));
            o.addProperty("z", round(p.getZ()));
            o.addProperty("yaw", round(p.getHeadYaw()));
            o.addProperty("body", round(p.bodyYaw));
            o.addProperty("pitch", round(p.getPitch()));
            o.addProperty("pose", p.getPose().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("sneak", p.isInSneakingPose());
            o.addProperty("sprint", p.isSprinting());
            o.addProperty("ground", p.isOnGround());
            o.addProperty("swing", p.handSwinging);
            o.addProperty("spectator", p.isSpectator());
            o.addProperty("slim", Faces.slim(p.getUuidAsString()));
            o.addProperty("skin", Faces.remember(p));
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty()) o.addProperty("held", Registries.ITEM.getId(held.getItem()).toString());
            players.add(o);
        }

        int r = Math.max(16, Math.min(96, radius));
        Box box = new Box(cx - r, world.getBottomY(), cz - r, cx + r, world.getTopY(), cz + r);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !(e instanceof PlayerEntity) && !e.isInvisible());
        int n = 0;
        for (LivingEntity e : list) {
            if (++n > MAX_MOBS) break;
            JsonObject o = new JsonObject();
            EntityType<?> type = e.getType();
            o.addProperty("id", e.getId());
            o.addProperty("type", EntityType.getId(type).toString());
            o.addProperty("x", round(e.getX()));
            o.addProperty("y", round(e.getY()));
            o.addProperty("z", round(e.getZ()));
            o.addProperty("yaw", round(e.bodyYaw));
            o.addProperty("head", round(e.getHeadYaw()));
            o.addProperty("w", round(e.getWidth()));
            o.addProperty("h", round(e.getHeight()));
            if (e.isBaby()) o.addProperty("baby", true);
            if (e.hasCustomName()) o.addProperty("name", e.getCustomName().getString());
            SpawnEggItem egg = SpawnEggItem.forEntity(type);
            if (egg != null) {
                o.addProperty("c1", egg.getColor(0));
                o.addProperty("c2", egg.getColor(1));
            }
            mobs.add(o);
        }
        return out.toString();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
