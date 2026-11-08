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
            JsonArray tr = new JsonArray();
            for (InventoryEditor.TrinketSlot t : InventoryEditor.trinkets(online)) {
                JsonObject it = item(t.inv().getStack(t.index()), -1);
                if (it == null) it = new JsonObject();
                it.addProperty("key", t.key());
                it.addProperty("group", t.group());
                it.addProperty("slotName", t.slot());
                tr.add(it);
            }
            o.add("trinkets", tr);
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
        JsonObject it = item(s, slot);
        if (it != null) arr.add(it);
    }

    /** An item for the app: id, name, count, enchanted, durability, and what's inside it (shulkers, bundles, backpacks). */
    static JsonObject item(ItemStack s, int slot) {
        return item(s, slot, 0);
    }

    private static JsonObject item(ItemStack s, int slot, int depth) {
        if (s == null || s.isEmpty()) return null;
        JsonObject it = new JsonObject();
        if (slot >= 0) it.addProperty("slot", slot);
        it.addProperty("id", Registries.ITEM.getId(s.getItem()).toString());
        it.addProperty("name", s.getName().getString());
        it.addProperty("count", s.getCount());
        if (s.hasEnchantments()) it.addProperty("ench", true);
        if (s.isDamageable() && s.getMaxDamage() > 0) it.addProperty("dur", Math.round(100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()));
        if (s.hasCustomName()) it.addProperty("custom", true);
        if (depth == 0 && s.hasNbt()) {
            NbtList inside = contents(s.getNbt(), 0);
            if (inside != null && !inside.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (int i = 0; i < inside.size() && arr.size() < 200; i++) {
                    NbtCompound c = inside.getCompound(i);
                    JsonObject sub = item(ItemStack.fromNbt(c), c.contains("Slot") ? c.getByte("Slot") & 0xFF : i, 1);
                    if (sub != null) arr.add(sub);
                }
                if (!arr.isEmpty()) it.add("inside", arr);
            }
        }
        return it;
    }

    /**
     * Items stored inside an item: shulker boxes (BlockEntityTag.Items), bundles (Items) and most modded
     * backpacks (Items / Inventory / inventory...): any list of item compounds a few levels down.
     */
    private static NbtList contents(NbtCompound nbt, int depth) {
        if (nbt == null || depth > 3) return null;
        for (String k : nbt.getKeys()) {
            NbtElement el = nbt.get(k);
            if (el instanceof NbtList list && list.getHeldType() == NbtElement.COMPOUND_TYPE && !list.isEmpty()) {
                NbtCompound first = list.getCompound(0);
                if (first.contains("id", NbtElement.STRING_TYPE) && (first.contains("Count") || first.contains("count"))) return list;
            } else if (el instanceof NbtCompound c && !k.equals("display") && !k.equals("Enchantments")) {
                NbtList found = contents(c, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
