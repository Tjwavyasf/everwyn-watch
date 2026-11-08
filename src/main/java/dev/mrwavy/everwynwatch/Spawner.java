package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.UUID;

/** Spawning mobs from the app, and taming pets for a chosen player. Server thread. */
final class Spawner {
    private static volatile String catalog;

    private Spawner() {}

    /** Every mob that can be spawned (vanilla and modded), with whether it can be tamed. Built once. */
    static synchronized String catalog(MinecraftServer server) {
        if (catalog != null) return catalog;
        ServerWorld world = server.getOverworld();
        JsonArray out = new JsonArray();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (!type.isSummonable() || type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) continue;
            if (type.getSpawnGroup() == SpawnGroup.MISC && type != EntityType.VILLAGER && type != EntityType.IRON_GOLEM
                    && type != EntityType.SNOW_GOLEM && type != EntityType.WANDERING_TRADER) continue;
            Entity e;
            try {
                e = type.create(world); // never added to the world, just looked at
            } catch (Throwable t) {
                continue;
            }
            if (!(e instanceof MobEntity mob)) continue;
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            String name = type.getName().getString();
            if (name.contains(".") || name.isBlank()) name = pretty(id.getPath());
            o.addProperty("name", name);
            o.addProperty("cat", mob instanceof Monster ? "hostile" : "animal");
            if (canTame(mob)) o.addProperty("tame", true);
            if (mob instanceof PassiveEntity) o.addProperty("baby", true);
            out.add(o);
        }
        catalog = out.toString();
        return catalog;
    }

    static boolean canTame(Entity e) {
        return e instanceof TameableEntity || e instanceof AbstractHorseEntity;
    }

    /** Spawns count mobs at a spot. owner: tame them for this player (blank = don't tame). */
    static String spawn(MinecraftServer server, ServerWorld world, int x, int y, int z, String typeId, int count, String owner, String name, boolean baby) {
        Identifier id = Identifier.tryParse(typeId == null ? "" : typeId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) throw new IllegalArgumentException("Unknown mob");
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.PLAYER) throw new IllegalArgumentException("That one can't be spawned from the app");
        if (!world.isChunkLoaded(x >> 4, z >> 4)) throw new IllegalArgumentException("That spot isn't loaded. Spawn near a player.");
        count = Math.max(1, Math.min(20, count));
        UUID ownerId = owner == null || owner.isBlank() ? null : ownerUuid(server, owner);
        BlockPos pos = new BlockPos(x, y, z);
        int made = 0;
        for (int i = 0; i < count; i++) {
            Entity e = type.create(world);
            if (e == null) throw new IllegalArgumentException("Minecraft couldn't make that mob");
            double ox = count > 1 ? (world.random.nextDouble() - 0.5) * Math.min(4, count) : 0;
            double oz = count > 1 ? (world.random.nextDouble() - 0.5) * Math.min(4, count) : 0;
            e.refreshPositionAndAngles(x + 0.5 + ox, y, z + 0.5 + oz, world.random.nextFloat() * 360f, 0);
            if (e instanceof MobEntity mob) {
                mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.COMMAND, null, null);
                if (ownerId != null) {
                    if (!canTame(mob)) throw new IllegalArgumentException(type.getName().getString() + " can't be tamed");
                    tameFor(mob, ownerId);
                }
                if (baby && mob instanceof PassiveEntity p) p.setBaby(true);
            }
            if (name != null && !name.isBlank()) {
                e.setCustomName(Text.literal(name.length() > 32 ? name.substring(0, 32) : name));
                if (e instanceof MobEntity mob) mob.setPersistent();
            }
            world.spawnEntityAndPassengers(e);
            made++;
        }
        String what = type.getName().getString();
        return "Spawned " + made + " " + what + (made == 1 ? "" : "s") + (ownerId != null ? " tamed for " + owner : "");
    }

    /** Tames an existing mob (or gives a pet to someone else). */
    static String tame(MinecraftServer server, String uuid, String owner) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad mob id");
        }
        UUID ownerId = ownerUuid(server, owner);
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e == null) continue;
            if (e instanceof PlayerEntity || !canTame(e)) throw new IllegalArgumentException("That mob can't be tamed");
            tameFor((MobEntity) e, ownerId);
            return (e.hasCustomName() ? e.getCustomName().getString() : e.getType().getName().getString()) + " now belongs to " + owner;
        }
        throw new IllegalArgumentException("That mob is gone");
    }

    private static void tameFor(MobEntity mob, UUID owner) {
        if (mob instanceof TameableEntity t) {
            t.setOwnerUuid(owner);
            t.setTamed(true);
        } else if (mob instanceof AbstractHorseEntity h) {
            h.setOwnerUuid(owner);
            h.setTame(true);
        }
        mob.setPersistent();
    }

    /** Who owns a tamed mob, by name, or null. */
    static String ownerName(MinecraftServer server, Entity e) {
        UUID id = e instanceof TameableEntity t && t.isTamed() ? t.getOwnerUuid()
                : e instanceof AbstractHorseEntity h && h.isTame() ? h.getOwnerUuid() : null;
        if (id == null) return null;
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return server.getUserCache() == null ? null : server.getUserCache().getByUuid(id).map(GameProfile::getName).orElse(null);
    }

    private static UUID ownerUuid(MinecraftServer server, String name) {
        if (name == null || !name.trim().matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Enter the owner's Minecraft name");
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name.trim());
        if (online != null) return online.getUuid();
        GameProfile p = AdminActions.profileByName(server, name.trim());
        if (p == null || p.getId() == null) throw new IllegalArgumentException("No player called " + name + " has played here");
        return p.getId();
    }

    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("[_/]")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        return sb.toString().trim();
    }
}
