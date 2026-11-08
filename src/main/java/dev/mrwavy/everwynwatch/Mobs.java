package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mobs in the area the app is looking at, colored like their spawn eggs. Server thread. */
final class Mobs {
    private static final int MAX = 400;
    private static final Map<EntityType<?>, JsonObject> TYPE_INFO = new HashMap<>();

    private Mobs() {}

    static JsonObject near(MinecraftServer server, String dim, int x0, int z0, int x1, int z1) {
        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
        }
        JsonObject out = new JsonObject();
        out.addProperty("dim", dim);
        JsonArray mobs = new JsonArray();
        JsonObject types = new JsonObject();
        out.add("mobs", mobs);
        out.add("types", types);
        if (world == null) return out;

        // keep the area sensible
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int hw = Math.min(1024, Math.abs(x1 - x0) / 2 + 16), hh = Math.min(1024, Math.abs(z1 - z0) / 2 + 16);
        Box box = new Box(cx - hw, world.getBottomY(), cz - hh, cx + hw, world.getTopY(), cz + hh);
        List<MobEntity> list = world.getEntitiesByClass(MobEntity.class, box, Entity::isAlive);
        int total = list.size();
        if (total > MAX) {
            list.sort((a, b) -> Double.compare(dist(a, cx, cz), dist(b, cx, cz)));
            list = list.subList(0, MAX);
        }
        for (MobEntity m : list) {
            JsonObject o = new JsonObject();
            String type = Registries.ENTITY_TYPE.getId(m.getType()).toString();
            o.addProperty("u", m.getUuidAsString());
            o.addProperty("t", type);
            o.addProperty("x", Math.round(m.getX() * 10) / 10.0);
            o.addProperty("y", (int) Math.floor(m.getY()));
            o.addProperty("z", Math.round(m.getZ() * 10) / 10.0);
            o.addProperty("hp", Math.round(m.getHealth()));
            o.addProperty("max", Math.round(m.getMaxHealth()));
            if (m.hasCustomName()) o.addProperty("n", m.getCustomName().getString());
            String owner = Spawner.ownerName(server, m);
            if (owner != null) {
                o.addProperty("pet", true);
                o.addProperty("owner", owner);
            } else if (Spawner.canTame(m)) o.addProperty("tamable", true);
            if (m.getCommandTags().contains(Challenges.MOB_TAG)) o.addProperty("wave", true);
            mobs.add(o);
            if (!types.has(type)) types.add(type, info(m));
        }
        out.addProperty("total", total);
        return out;
    }

    private static JsonObject info(MobEntity m) {
        return TYPE_INFO.computeIfAbsent(m.getType(), t -> {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.getName().getString());
            String cat = t == EntityType.WITHER || t == EntityType.ENDER_DRAGON || t == EntityType.WARDEN || t == EntityType.ELDER_GUARDIAN ? "boss"
                    : m instanceof Monster ? "hostile"
                    : m instanceof MerchantEntity || m instanceof IronGolemEntity ? "villager"
                    : "animal";
            o.addProperty("cat", cat);
            SpawnEggItem egg = SpawnEggItem.forEntity(t);
            if (egg != null) {
                JsonArray c = new JsonArray();
                c.add(egg.getColor(0) & 0xFFFFFF);
                c.add(egg.getColor(1) & 0xFFFFFF);
                o.add("egg", c);
            }
            return o;
        }).deepCopy();
    }

    private static double dist(Entity e, int x, int z) {
        double dx = e.getX() - x, dz = e.getZ() - z;
        return dx * dx + dz * dz;
    }

    /** Removes one mob (never a player). Server thread. */
    static String remove(MinecraftServer server, String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad mob id");
        }
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e == null) continue;
            if (e instanceof PlayerEntity) throw new IllegalArgumentException("That's a player");
            String name = e.hasCustomName() ? e.getCustomName().getString() : e.getType().getName().getString();
            e.discard();
            return "Removed " + name;
        }
        throw new IllegalArgumentException("That mob is already gone");
    }
}
