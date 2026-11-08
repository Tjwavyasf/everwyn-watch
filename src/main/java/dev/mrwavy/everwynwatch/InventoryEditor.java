package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moves items between any two places from the app, like dragging in the game's inventory screen:
 * a player's inventory or ender chest (online, or offline through their save file), their trinket slots
 * (Trinkets mod, online), a chest/barrel/any container block, or a death backup (copy only).
 * Server thread.
 */
final class InventoryEditor {
    record TrinketSlot(String key, String group, String slot, int index, Inventory inv) {}

    /** One slot somewhere. */
    private interface Slot {
        ItemStack get();
        void set(ItemStack s);
        default boolean copyOnly() { return false; }
        String label();
    }

    private InventoryEditor() {}

    // ------------------------------------------------------------------ trinkets (optional mod)

    /** Every trinket slot of an online player, or none if Trinkets isn't installed. */
    @SuppressWarnings("unchecked")
    static List<TrinketSlot> trinkets(LivingEntity player) {
        List<TrinketSlot> out = new ArrayList<>();
        try {
            Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Optional<?> comp = (Optional<?>) api.getMethod("getTrinketComponent", LivingEntity.class).invoke(null, player);
            if (comp.isEmpty()) return out;
            Object c = comp.get();
            Map<String, Map<String, ?>> groups = (Map<String, Map<String, ?>>) c.getClass().getMethod("getInventory").invoke(c);
            for (Map.Entry<String, Map<String, ?>> g : groups.entrySet()) {
                for (Map.Entry<String, ?> sl : g.getValue().entrySet()) {
                    if (!(sl.getValue() instanceof Inventory inv)) continue;
                    for (int i = 0; i < inv.size(); i++) out.add(new TrinketSlot(g.getKey() + "/" + sl.getKey() + "/" + i, g.getKey(), sl.getKey(), i, inv));
                }
            }
        } catch (ClassNotFoundException notInstalled) {
            // no Trinkets on this server
        } catch (Throwable e) {
            EverwynWatch.LOG.debug("Couldn't read trinkets", e);
        }
        return out;
    }

    // ------------------------------------------------------------------ containers on the map

    /** A container block's contents (chest, barrel, hopper, modded...). Double chests count as one. */
    static JsonObject container(ServerWorld world, int x, int y, int z) {
        BlockPos pos = findContainer(world, x, y, z);
        Inventory inv = blockInventory(world, pos);
        JsonObject o = new JsonObject();
        o.addProperty("y", pos.getY()); // where it really is (the map only knows roughly)
        o.addProperty("name", world.getBlockState(pos).getBlock().getName().getString());
        o.addProperty("size", inv.size());
        JsonArray items = new JsonArray();
        for (int i = 0; i < inv.size(); i++) {
            JsonObject it = Inventories.item(inv.getStack(i), i);
            if (it != null) items.add(it);
        }
        o.add("items", items);
        return o;
    }

    /** The nearest container straight above or below the spot (the 2D map doesn't know heights). */
    private static BlockPos findContainer(ServerWorld world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) throw new IllegalArgumentException("That spot isn't loaded. Have a player go near it.");
        for (int d = 0; d <= 12; d++) {
            for (int sign : d == 0 ? new int[]{1} : new int[]{-1, 1}) {
                BlockPos p = new BlockPos(x, y + d * sign, z);
                if (world.getBlockEntity(p) instanceof Inventory) return p;
            }
        }
        throw new IllegalArgumentException("There's no chest or container there");
    }

    private static Inventory blockInventory(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) throw new IllegalArgumentException("That spot isn't loaded. Have a player go near it.");
        BlockState st = world.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock chest) {
            Inventory inv = ChestBlock.getInventory(chest, st, world, pos, true);
            if (inv != null) return inv;
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Inventory inv) return inv;
        throw new IllegalArgumentException("There's no chest or container there");
    }

    // ------------------------------------------------------------------ offline players

    /** An offline player's save file, loaded once per move and written back after. */
    private static final class Offline {
        final Path file;
        final NbtCompound root;
        boolean dirty;

        Offline(Path file) throws Exception {
            this.file = file;
            this.root = NbtIo.readCompressed(file.toFile());
        }

        NbtList list(String key) {
            if (!root.contains(key, NbtElement.LIST_TYPE)) root.put(key, new NbtList());
            return root.getList(key, NbtElement.COMPOUND_TYPE);
        }

        ItemStack get(String key, int slotByte) {
            for (NbtElement e : list(key)) {
                NbtCompound c = (NbtCompound) e;
                if (c.getByte("Slot") == (byte) slotByte) return ItemStack.fromNbt(c);
            }
            return ItemStack.EMPTY;
        }

        void set(String key, int slotByte, ItemStack s) {
            NbtList l = list(key);
            for (int i = l.size() - 1; i >= 0; i--) if (l.getCompound(i).getByte("Slot") == (byte) slotByte) l.remove(i);
            if (!s.isEmpty()) {
                NbtCompound c = s.writeNbt(new NbtCompound());
                c.putByte("Slot", (byte) slotByte);
                l.add(c);
            }
            dirty = true;
        }

        void save() throws Exception {
            if (!dirty) return;
            Path tmp = file.resolveSibling(file.getFileName() + ".everwyn.tmp");
            NbtIo.writeCompressed(root, tmp.toFile());
            Files.copy(file, file.resolveSibling(file.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** App slot numbers: 0-35 main (0-8 hotbar), 36-39 armor (feet first), 40 offhand. In the save file: 0-35, 100-103, -106. */
    private static int fileSlot(int appSlot) {
        if (appSlot >= 0 && appSlot < 36) return appSlot;
        if (appSlot >= 36 && appSlot <= 39) return 100 + (appSlot - 36);
        if (appSlot == 40) return -106;
        throw new IllegalArgumentException("Bad slot");
    }

    // ------------------------------------------------------------------ moving

    /** Moves count items (0 = the whole stack) from one slot to another; swaps if they don't stack. */
    static String move(Services s, JsonObject fromRef, JsonObject toRef, int count) throws Exception {
        Map<String, Offline> files = new HashMap<>();
        Slot from = resolve(s, fromRef, files);
        Slot to = resolve(s, toRef, files);
        ItemStack src = from.get(), dst = to.get();
        if (src.isEmpty()) throw new IllegalArgumentException("That slot is empty");
        int n = count <= 0 ? src.getCount() : Math.min(count, src.getCount());
        String what = n + " " + src.getName().getString();
        if (dst.isEmpty()) {
            to.set(src.copyWithCount(n));
            if (!from.copyOnly()) from.set(n >= src.getCount() ? ItemStack.EMPTY : src.copyWithCount(src.getCount() - n));
        } else if (ItemStack.canCombine(src, dst)) {
            int room = dst.getMaxCount() - dst.getCount();
            if (room <= 0) throw new IllegalArgumentException("That stack is full");
            int k = Math.min(room, n);
            to.set(dst.copyWithCount(dst.getCount() + k));
            if (!from.copyOnly()) from.set(k >= src.getCount() ? ItemStack.EMPTY : src.copyWithCount(src.getCount() - k));
            what = k + " " + src.getName().getString();
        } else {
            if (from.copyOnly()) throw new IllegalArgumentException("Put it in an empty slot (that slot already has something)");
            if (n != src.getCount()) throw new IllegalArgumentException("Split stacks only into an empty slot");
            to.set(src.copy());
            from.set(dst.copy());
            what = src.getName().getString() + " ⇄ " + dst.getName().getString();
        }
        for (Offline f : files.values()) f.save();
        return "Moved " + what + " from " + from.label() + " to " + to.label();
    }

    /** Takes a stack (or count of it) from one slot and puts it into a player's inventory, wherever it fits. */
    static String giveTo(Services s, JsonObject fromRef, String toName, int count) throws Exception {
        Map<String, Offline> files = new HashMap<>();
        Slot from = resolve(s, fromRef, files);
        ItemStack src = from.get();
        if (src.isEmpty()) throw new IllegalArgumentException("That slot is empty");
        int n = count <= 0 ? src.getCount() : Math.min(count, src.getCount());
        ItemStack moving = src.copyWithCount(n);
        ServerPlayerEntity target = s.server.getPlayerManager().getPlayer(toName);
        String where;
        if (target != null) {
            target.getInventory().offerOrDrop(moving); // anything that doesn't fit drops at their feet
            target.playerScreenHandler.sendContentUpdates();
            where = target.getGameProfile().getName() + "'s inventory";
        } else {
            JsonObject probe = new JsonObject();
            probe.addProperty("kind", "player");
            probe.addProperty("name", toName);
            probe.addProperty("part", "main");
            int free = -1;
            for (int i = 0; i < 36 && free < 0; i++) {
                probe.addProperty("slot", i);
                if (resolve(s, probe, files).get().isEmpty()) free = i;
            }
            if (free < 0) throw new IllegalArgumentException(toName + "'s inventory is full");
            probe.addProperty("slot", free);
            resolve(s, probe, files).set(moving);
            where = toName + "'s inventory (offline)";
        }
        if (!from.copyOnly()) from.set(n >= src.getCount() ? ItemStack.EMPTY : src.copyWithCount(src.getCount() - n));
        for (Offline f : files.values()) f.save();
        return "Put " + n + " " + src.getName().getString() + " in " + where;
    }

    /** Deletes a stack (or count of it). */
    static String delete(Services s, JsonObject ref, int count) throws Exception {
        Map<String, Offline> files = new HashMap<>();
        Slot sl = resolve(s, ref, files);
        if (sl.copyOnly()) throw new IllegalArgumentException("That's a backup; nothing to delete");
        ItemStack st = sl.get();
        if (st.isEmpty()) throw new IllegalArgumentException("That slot is empty");
        int n = count <= 0 ? st.getCount() : Math.min(count, st.getCount());
        sl.set(n >= st.getCount() ? ItemStack.EMPTY : st.copyWithCount(st.getCount() - n));
        for (Offline f : files.values()) f.save();
        return "Deleted " + n + " " + st.getName().getString() + " from " + sl.label();
    }

    private static Slot resolve(Services s, JsonObject ref, Map<String, Offline> files) throws Exception {
        String kind = ref.has("kind") ? ref.get("kind").getAsString() : "";
        MinecraftServer server = s.server;
        switch (kind) {
            case "player" -> {
                String name = ref.get("name").getAsString();
                String part = ref.has("part") ? ref.get("part").getAsString() : "main";
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
                if (p != null) return onlineSlot(p, part, ref);
                if ("trinket".equals(part)) throw new IllegalArgumentException("Trinket slots can only be changed while they're online");
                GameProfile profile = AdminActions.profileByName(server, name);
                if (profile == null || profile.getId() == null) throw new IllegalArgumentException("Never seen a player called " + name);
                Path file = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(profile.getId() + ".dat");
                if (!Files.exists(file)) throw new IllegalArgumentException("No save file for " + name);
                Offline off = files.get(file.toString());
                if (off == null) {
                    off = new Offline(file);
                    files.put(file.toString(), off);
                }
                final Offline f = off;
                int slot = ref.get("slot").getAsInt();
                if ("ender".equals(part)) {
                    if (slot < 0 || slot > 26) throw new IllegalArgumentException("Bad slot");
                    return slot(() -> f.get("EnderItems", slot), st -> f.set("EnderItems", slot, st), name + "'s ender chest (offline)");
                }
                int fs = fileSlot(slot);
                return slot(() -> f.get("Inventory", fs), st -> f.set("Inventory", fs, st), name + " (offline)");
            }
            case "block" -> {
                String dim = ref.get("dim").getAsString();
                ServerWorld world = null;
                for (ServerWorld w : server.getWorlds()) if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
                if (world == null) throw new IllegalArgumentException("Unknown world");
                BlockPos pos = new BlockPos(ref.get("x").getAsInt(), ref.get("y").getAsInt(), ref.get("z").getAsInt());
                Inventory inv = blockInventory(world, pos);
                int slot = ref.get("slot").getAsInt();
                if (slot < 0 || slot >= inv.size()) throw new IllegalArgumentException("Bad slot");
                return slot(() -> inv.getStack(slot), st -> { inv.setStack(slot, st); inv.markDirty(); },
                        "the container at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            }
            case "death" -> {
                ItemStack st = s.vault.stackAt(ref.get("id").getAsString(), ref.get("slot").getAsInt());
                return new Slot() {
                    public ItemStack get() { return st.copy(); }
                    public void set(ItemStack x) { throw new IllegalArgumentException("Death backups can't be changed"); }
                    public boolean copyOnly() { return true; }
                    public String label() { return "a death backup"; }
                };
            }
            default -> throw new IllegalArgumentException("Unknown place");
        }
    }

    private static Slot onlineSlot(ServerPlayerEntity p, String part, JsonObject ref) {
        String name = p.getGameProfile().getName();
        Runnable sync = () -> {
            p.getInventory().markDirty();
            p.playerScreenHandler.sendContentUpdates();
            if (p.currentScreenHandler != null) p.currentScreenHandler.sendContentUpdates();
        };
        switch (part) {
            case "ender" -> {
                Inventory inv = p.getEnderChestInventory();
                int slot = ref.get("slot").getAsInt();
                if (slot < 0 || slot >= inv.size()) throw new IllegalArgumentException("Bad slot");
                return slot(() -> inv.getStack(slot), st -> { inv.setStack(slot, st); sync.run(); }, name + "'s ender chest");
            }
            case "trinket" -> {
                String key = ref.get("key").getAsString();
                for (TrinketSlot t : trinkets(p)) {
                    if (t.key().equals(key)) return slot(() -> t.inv().getStack(t.index()), st -> { t.inv().setStack(t.index(), st); t.inv().markDirty(); sync.run(); }, name + "'s " + t.slot() + " slot");
                }
                throw new IllegalArgumentException("That trinket slot doesn't exist");
            }
            default -> {
                int slot = ref.get("slot").getAsInt();
                if (slot < 0 || slot > 40) throw new IllegalArgumentException("Bad slot");
                return slot(() -> p.getInventory().getStack(slot), st -> { p.getInventory().setStack(slot, st); sync.run(); }, name);
            }
        }
    }

    private static Slot slot(java.util.function.Supplier<ItemStack> get, java.util.function.Consumer<ItemStack> set, String label) {
        return new Slot() {
            public ItemStack get() { return get.get(); }
            public void set(ItemStack s) { set.accept(s); }
            public String label() { return label; }
        };
    }
}
