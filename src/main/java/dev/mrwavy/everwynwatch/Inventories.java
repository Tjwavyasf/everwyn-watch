package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A player's inventory and ender chest, live if they're online, or read from their
 * save file if they're offline. Slots: 0-35 main (0-8 hotbar), 36-39 armor (feet first), 40 offhand.
 */
final class Inventories {
    private Inventories() {}

    /** Server thread. */
    static JsonObject get(MinecraftServer server, String name) throws Exception {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        JsonObject o = new JsonObject();
        JsonArray items = new JsonArray(), ender = new JsonArray();
        if (online != null) {
            PlayerInventory inv = online.getInventory();
            for (int i = 0; i < inv.size(); i++) add(items, i, inv.getStack(i));
            EnderChestInventory ec = online.getEnderChestInventory();
            for (int i = 0; i < ec.size(); i++) add(ender, i, ec.getStack(i));
            o.addProperty("name", online.getGameProfile().getName());
            o.addProperty("uuid", online.getUuidAsString());
            o.addProperty("online", true);
            o.addProperty("dim", online.getWorld().getRegistryKey().getValue().toString());
            o.addProperty("x", (int) Math.floor(online.getX()));
            o.addProperty("y", (int) Math.floor(online.getY()));
            o.addProperty("z", (int) Math.floor(online.getZ()));
            o.addProperty("xp", online.experienceLevel);
        } else {
            GameProfile profile = AdminActions.profileByName(server, name);
            if (profile == null) throw new IllegalArgumentException("Never seen a player called " + name);
            Path file = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(profile.getId() + ".dat");
            if (!Files.exists(file)) throw new IllegalArgumentException("No save file for " + profile.getName());
            NbtCompound nbt = NbtIo.readCompressed(file.toFile());
            NbtList inv = nbt.getList("Inventory", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < inv.size(); i++) {
                NbtCompound c = inv.getCompound(i);
                int slot = c.getByte("Slot");
                int mapped = slot >= 0 && slot < 36 ? slot : slot >= 100 && slot <= 103 ? 36 + (slot - 100) : slot == -106 ? 40 : -1;
                if (mapped >= 0) add(items, mapped, ItemStack.fromNbt(c));
            }
            NbtList ec = nbt.getList("EnderItems", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < ec.size(); i++) {
                NbtCompound c = ec.getCompound(i);
                add(ender, c.getByte("Slot") & 0xFF, ItemStack.fromNbt(c));
            }
            o.addProperty("name", profile.getName());
            o.addProperty("uuid", profile.getId().toString());
            o.addProperty("online", false);
            o.addProperty("dim", nbt.getString("Dimension"));
            NbtList pos = nbt.getList("Pos", NbtElement.DOUBLE_TYPE);
            if (pos.size() == 3) {
                o.addProperty("x", (int) Math.floor(pos.getDouble(0)));
                o.addProperty("y", (int) Math.floor(pos.getDouble(1)));
                o.addProperty("z", (int) Math.floor(pos.getDouble(2)));
            }
            o.addProperty("xp", nbt.getInt("XpLevel"));
            o.addProperty("lastSeen", Files.getLastModifiedTime(file).toMillis());
        }
        o.add("items", items);
        o.add("ender", ender);
        return o;
    }

    private static void add(JsonArray arr, int slot, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        JsonObject it = new JsonObject();
        it.addProperty("slot", slot);
        it.addProperty("id", Registries.ITEM.getId(s.getItem()).toString());
        it.addProperty("name", s.getName().getString());
        it.addProperty("count", s.getCount());
        if (s.hasEnchantments()) it.addProperty("ench", true);
        if (s.isDamageable() && s.getMaxDamage() > 0) it.addProperty("dur", Math.round(100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()));
        arr.add(it);
    }
}
