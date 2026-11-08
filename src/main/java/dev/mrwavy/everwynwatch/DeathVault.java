package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps a copy of every player's inventory from the moment they die (taken before
 * YIGD or vanilla drops anything), so it can be given back later even if the grave
 * despawned, was looted, or was destroyed. Saved in <world>/everwyn-watch/deaths.json.
 */
final class DeathVault {
    private static final Gson GSON = new Gson();

    private final MinecraftServer server;
    private final WatchConfig cfg;
    private final Path file;
    private final List<JsonObject> deaths = new ArrayList<>(); // oldest first
    private final Map<UUID, JsonObject> pending = new HashMap<>();
    /** A copy of each online player's inventory, refreshed every second, in case the copy taken at death is missed. */
    private record Backup(long at, List<ItemStack> stacks, int xp, Map<String, ItemStack> trinkets) {}
    /** Trinket slots in a death record are numbered from here in the app (after the normal 41 slots). */
    static final int TRINKET_BASE = 1000;
    private final Map<UUID, Backup> lastKnown = new HashMap<>();
    private int backupTicks;
    private static final long FRESH_MS = 10_000;
    private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EverwynWatch-DeathSaver");
        t.setDaemon(true);
        return t;
    });

    DeathVault(MinecraftServer server, WatchConfig cfg) {
        this.server = server;
        this.cfg = cfg;
        this.file = server.getSavePath(WorldSavePath.ROOT).resolve("everwyn-watch").resolve("deaths.json");
        load();
    }

    /** Called right before a player dies (their items are still on them). */
    void snapshot(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) stacks.add(inv.getStack(i));
        JsonObject d = record(p, stacks, p.experienceLevel, trinketsOf(p, false));
        d.addProperty("capturedAt", System.currentTimeMillis());
        pending.put(p.getUuid(), d);
    }

    /** Server tick: once a second, keep a fresh copy of every living online player's inventory. */
    void tick() {
        if (++backupTicks < 20) return;
        backupTicks = 0;
        long now = System.currentTimeMillis();
        java.util.Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            online.add(p.getUuid());
            if (!p.isAlive() || p.isSpectator()) continue;
            PlayerInventory inv = p.getInventory();
            List<ItemStack> copy = new ArrayList<>(inv.size());
            for (int i = 0; i < inv.size(); i++) copy.add(inv.getStack(i).copy());
            lastKnown.put(p.getUuid(), new Backup(now, copy, p.experienceLevel, trinketsOf(p, true)));
        }
        lastKnown.keySet().retainAll(online);
    }

    /** The player's trinkets (Trinkets mod), by slot key. Empty when the mod isn't installed. */
    private static Map<String, ItemStack> trinketsOf(ServerPlayerEntity p, boolean copy) {
        Map<String, ItemStack> out = new java.util.LinkedHashMap<>();
        for (InventoryEditor.TrinketSlot t : InventoryEditor.trinkets(p)) {
            ItemStack st = t.inv().getStack(t.index());
            if (!st.isEmpty()) out.put(t.key(), copy ? st.copy() : st);
        }
        return out;
    }

    private JsonObject record(ServerPlayerEntity p, List<ItemStack> stacks, int xp, Map<String, ItemStack> trinkets) {
        JsonObject d = new JsonObject();
        d.addProperty("player", p.getGameProfile().getName());
        d.addProperty("uuid", p.getUuidAsString());
        d.addProperty("dim", p.getWorld().getRegistryKey().getValue().toString());
        d.addProperty("x", (int) Math.floor(p.getX()));
        d.addProperty("y", (int) Math.floor(p.getY()));
        d.addProperty("z", (int) Math.floor(p.getZ()));
        d.addProperty("xp", xp);
        JsonArray slots = new JsonArray();
        int count = 0;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                slots.add(JsonNull.INSTANCE);
            } else {
                slots.add(s.writeNbt(new NbtCompound()).asString());
                count++;
            }
        }
        d.add("slots", slots);
        if (trinkets != null && !trinkets.isEmpty()) {
            JsonArray tr = new JsonArray();
            for (Map.Entry<String, ItemStack> e : trinkets.entrySet()) {
                JsonObject t = new JsonObject();
                t.addProperty("key", e.getKey());
                t.addProperty("nbt", e.getValue().writeNbt(new NbtCompound()).asString());
                tr.add(t);
                count++;
            }
            d.add("trinkets", tr);
        }
        d.addProperty("items", count);
        return d;
    }

    /** One stack from a death record (app slot number; trinkets from TRINKET_BASE). */
    synchronized ItemStack stackAt(String id, int slot) {
        JsonObject d = find(id);
        if (slot >= TRINKET_BASE) {
            JsonArray tr = d.has("trinkets") ? d.getAsJsonArray("trinkets") : new JsonArray();
            int i = slot - TRINKET_BASE;
            if (i >= tr.size()) return ItemStack.EMPTY;
            return stack(tr.get(i).getAsJsonObject().get("nbt"));
        }
        JsonArray slots = d.getAsJsonArray("slots");
        return slot >= 0 && slot < slots.size() ? stack(slots.get(slot)) : ItemStack.EMPTY;
    }

    /**
     * Called after the death really happened. Never drops a death: uses the copy from the moment of
     * death, else the copy from up to a second before (another mod emptied the inventory first, or the
     * death skipped the "before death" event), else records the death without items.
     */
    void commit(ServerPlayerEntity p, String message) {
        long now = System.currentTimeMillis();
        JsonObject d = pending.remove(p.getUuid());
        if (d != null && now - d.get("capturedAt").getAsLong() > FRESH_MS) d = null; // left over from a totem save
        Backup b = lastKnown.remove(p.getUuid());
        boolean backupUsable = b != null && now - b.at() <= FRESH_MS && b.stacks().stream().anyMatch(s -> !s.isEmpty());
        String source = "death";
        if ((d == null || d.get("items").getAsInt() == 0) && backupUsable) {
            d = record(p, b.stacks(), b.xp(), b.trinkets());
            source = "backup";
        } else if (d == null) {
            d = record(p, List.of(), p.experienceLevel, null);
            source = "none";
        }
        d.remove("capturedAt");
        d.addProperty("source", source);
        d.addProperty("id", Long.toString(System.currentTimeMillis(), 36) + Integer.toString(p.getRandom().nextInt(1296), 36));
        d.addProperty("time", System.currentTimeMillis());
        d.addProperty("message", message);
        d.addProperty("keepInventory", p.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY));
        d.addProperty("restored", 0);
        deaths.add(d);
        int limit = Math.max(20, cfg.deathHistoryLimit);
        while (deaths.size() > limit) deaths.remove(0);
        save();
        if (source.equals("none")) {
            EverwynWatch.LOG.warn("Everwyn Watch: {} died but their items couldn't be copied (no copy from the moment of death or the second before)", p.getGameProfile().getName());
        } else {
            EverwynWatch.LOG.info("Everwyn Watch saved {}'s death: {} item stacks ({})", p.getGameProfile().getName(), d.get("items").getAsInt(),
                    source.equals("death") ? "copied at death" : "copied a moment before death; another mod moved the items first");
        }
    }

    /** Drops a stale snapshot if the player survived (e.g. totem of undying). */
    void survived(ServerPlayerEntity p) {
        pending.remove(p.getUuid());
    }

    /** Short list for the app, newest first. */
    JsonArray summaries() {
        JsonArray out = new JsonArray();
        for (int i = deaths.size() - 1; i >= 0; i--) {
            JsonObject d = deaths.get(i);
            JsonObject o = new JsonObject();
            for (String k : new String[]{"id", "player", "uuid", "dim", "x", "y", "z", "xp", "items", "time", "message", "restored", "keepInventory", "source"}) {
                if (d.has(k)) o.add(k, d.get(k));
            }
            out.add(o);
        }
        return out;
    }

    /** Full details with the item list. Runs on the server thread. */
    JsonObject detail(String id) {
        JsonObject d = find(id);
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonElement> e : d.entrySet()) {
            if (!e.getKey().equals("slots") && !e.getKey().equals("trinkets")) o.add(e.getKey(), e.getValue());
        }
        JsonArray items = new JsonArray();
        JsonArray slots = d.getAsJsonArray("slots");
        for (int i = 0; i < slots.size(); i++) {
            JsonObject it = Inventories.item(stack(slots.get(i)), i);
            if (it != null) items.add(it);
        }
        if (d.has("trinkets")) { // listed after the normal slots, numbered from TRINKET_BASE
            JsonArray tr = d.getAsJsonArray("trinkets");
            for (int i = 0; i < tr.size(); i++) {
                JsonObject t = tr.get(i).getAsJsonObject();
                JsonObject it = Inventories.item(stack(t.get("nbt")), TRINKET_BASE + i);
                if (it == null) continue;
                String[] k = t.get("key").getAsString().split("/");
                it.addProperty("trinket", k.length > 1 ? k[1] : t.get("key").getAsString());
                items.add(it);
            }
        }
        o.add("itemList", items);
        o.addProperty("online", server.getPlayerManager().getPlayer(UUID.fromString(d.get("uuid").getAsString())) != null);
        o.addProperty("grave", graveStatus(d));
        o.addProperty("yigd", FabricLoader.getInstance().isModLoaded("yigd"));
        return o;
    }

    /**
     * Puts the items back into the slots they were in. Anything the player is
     * holding in those slots now is moved to a free slot or dropped at their feet,
     * so nothing they have now gets deleted.
     */
    String restore(String id, boolean xp, java.util.Set<Integer> pick) {
        JsonObject d = find(id);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(d.get("uuid").getAsString()));
        if (p == null) throw new IllegalArgumentException(d.get("player").getAsString() + " has to be online to get their items back");
        PlayerInventory inv = p.getInventory();
        JsonArray slots = d.getAsJsonArray("slots");
        List<ItemStack> displaced = new ArrayList<>();
        int restored = 0;
        JsonArray done = d.has("restoredSlots") ? d.getAsJsonArray("restoredSlots") : new JsonArray();
        for (int i = 0; i < slots.size(); i++) {
            if (pick != null && !pick.contains(i)) continue;
            ItemStack saved = stack(slots.get(i));
            if (saved.isEmpty()) continue;
            boolean seen = false;
            for (JsonElement e : done) if (e.getAsInt() == i) seen = true;
            if (!seen) done.add(i);
            if (i < inv.size()) {
                ItemStack current = inv.getStack(i);
                if (!current.isEmpty()) displaced.add(current.copy());
                inv.setStack(i, saved);
            } else {
                displaced.add(saved);
            }
            restored++;
        }
        // trinkets go back into their own slots (anything already there is moved aside too)
        if (d.has("trinkets")) {
            JsonArray tr = d.getAsJsonArray("trinkets");
            List<InventoryEditor.TrinketSlot> slotsNow = InventoryEditor.trinkets(p);
            for (int i = 0; i < tr.size(); i++) {
                if (pick != null && !pick.contains(TRINKET_BASE + i)) continue;
                JsonObject t = tr.get(i).getAsJsonObject();
                ItemStack saved = stack(t.get("nbt"));
                if (saved.isEmpty()) continue;
                String key = t.get("key").getAsString();
                InventoryEditor.TrinketSlot target = slotsNow.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
                if (target != null) {
                    ItemStack current = target.inv().getStack(target.index());
                    if (!current.isEmpty()) displaced.add(current.copy());
                    target.inv().setStack(target.index(), saved);
                    target.inv().markDirty();
                } else {
                    displaced.add(saved); // that slot doesn't exist now: give it to them
                }
                restored++;
            }
        }
        for (ItemStack s : displaced) inv.offerOrDrop(s);
        inv.markDirty();
        p.playerScreenHandler.sendContentUpdates();
        int xpLevels = d.has("xp") ? d.get("xp").getAsInt() : 0;
        boolean gaveXp = false;
        if (xp && xpLevels > p.experienceLevel) {
            p.addExperienceLevels(xpLevels - p.experienceLevel);
            gaveXp = true;
        }
        if (restored == 0) throw new IllegalArgumentException("None of those slots had items");
        d.add("restoredSlots", done);
        d.addProperty("restored", d.get("restored").getAsInt() + 1);
        d.addProperty("restoredAt", System.currentTimeMillis());
        save();
        p.sendMessage(Text.literal("[Everwyn] ").formatted(Formatting.GOLD)
                .append(Text.literal(cfg.adminName + " gave back the items from your death.").formatted(Formatting.GREEN)));
        return "Gave " + d.get("player").getAsString() + " back " + restored + " stack" + (restored == 1 ? "" : "s")
                + (gaveXp ? " and " + xpLevels + " XP levels" : "") + (displaced.isEmpty() ? "" : " (moved " + displaced.size() + " of their current items aside)");
    }

    /** "present" if a YIGD grave block is still near the death spot, "gone", or "unknown" (area not loaded). */
    private String graveStatus(JsonObject d) {
        if (!FabricLoader.getInstance().isModLoaded("yigd")) return "unknown";
        ServerWorld world = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(d.get("dim").getAsString())) world = w;
        }
        if (world == null) return "unknown";
        int x = d.get("x").getAsInt(), y = d.get("y").getAsInt(), z = d.get("z").getAsInt();
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return "unknown";
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!world.getChunkManager().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                for (int dy = -6; dy <= 6; dy++) {
                    BlockState st = world.getBlockState(pos.set(x + dx, y + dy, z + dz));
                    Identifier id = Registries.BLOCK.getId(st.getBlock());
                    if (id.getNamespace().equals("yigd")) return "present";
                }
            }
        }
        return "gone";
    }

    private JsonObject find(String id) {
        for (JsonObject d : deaths) {
            if (d.get("id").getAsString().equals(id)) return d;
        }
        throw new IllegalArgumentException("That death record no longer exists");
    }

    private static ItemStack stack(JsonElement el) {
        if (el == null || el.isJsonNull()) return ItemStack.EMPTY;
        try {
            return ItemStack.fromNbt(StringNbtReader.parse(el.getAsString()));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) deaths.add(el.getAsJsonObject());
        } catch (Exception e) {
            EverwynWatch.LOG.error("Could not read {}", file, e);
        }
    }

    private void save() {
        JsonArray arr = new JsonArray();
        deaths.forEach(arr::add);
        String json = GSON.toJson(arr);
        saver.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling("deaths.json.tmp");
                try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    w.write(json);
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                EverwynWatch.LOG.error("Could not save {}", file, e);
            }
        });
    }
}
