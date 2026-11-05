package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Care packages (a chest of items dropped at a spot) and mob-wave challenges
 * (armed at a spot, start when a player walks in, loot chest if every wave is
 * beaten, "failed" and cleanup if a player dies, runs out of time or leaves).
 * Everything here runs on the server thread.
 */
public final class Challenges {
    static final String MOB_TAG = "everwyn_wave";
    private static final int MAX_CHALLENGES = 20;
    private static final int MAX_WAVES = 5;
    private static final int MAX_MOBS_PER_WAVE = 50;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final EventLog log;
    private final Path file;
    private final Map<String, Wave> challenges = new LinkedHashMap<>();
    private int tickCounter = 0;

    private static final class Wave {
        String id, name, dim;
        int x, y, z, radius, timeLimitMin;
        boolean announce;
        JsonArray waveDefs, loot;

        String state = "armed";
        int waveIndex;
        int waveTotal;
        long endsAt, nextWaveAt, abandonedSince;
        String runTag;
        ServerBossBar bar;
        final Set<UUID> mobs = new HashSet<>();
        final Set<UUID> participants = new HashSet<>();
    }

    public Challenges(MinecraftServer server, EventLog log, Path file) {
        this.server = server;
        this.log = log;
        this.file = file;
        load();
    }

    // ------------------------------------------------------------------ care packages

    public String dropPackage(String name, String dim, int x, int y, int z, JsonArray items, boolean announce, boolean share) {
        ServerWorld world = world(dim);
        if (world == null) throw new IllegalArgumentException("Unknown dimension");
        checkCoords(x, y, z);
        List<ItemStack> stacks = stacks(items);
        if (stacks.isEmpty()) throw new IllegalArgumentException("Pick at least one item");
        String label = cleanName(name, "Care package");

        BlockPos spot = findSpot(world, x, y, z);
        placeChest(world, spot, stacks);
        lightning(world, spot);

        String where = spot.getX() + " " + spot.getY() + " " + spot.getZ();
        if (announce) {
            broadcast(prefix().append(Text.literal("A care package dropped at ").formatted(Formatting.WHITE))
                    .append(Text.literal(label).formatted(Formatting.YELLOW))
                    .append(Text.literal(" (" + where + ")!").formatted(Formatting.WHITE)));
        }
        if (share) shareWaypoint(label, dim, spot, 6);
        log.add("event", null, "Care package dropped at " + label + " (" + where + ")");
        if (announce) Discord.send("event", ":package: A care package dropped at **" + Discord.clean(label) + "** (" + where + ")", null, null);
        return "Care package dropped at " + where;
    }

    // ------------------------------------------------------------------ challenges

    public String createWave(JsonObject req) {
        if (challenges.size() >= MAX_CHALLENGES) throw new IllegalArgumentException("Too many challenges placed (max " + MAX_CHALLENGES + ")");
        Wave w = new Wave();
        w.id = UUID.randomUUID().toString().substring(0, 8);
        w.name = cleanName(str(req, "name", "Mob wave"), "Mob wave");
        w.dim = str(req, "dim", null);
        if (w.dim == null || world(w.dim) == null) throw new IllegalArgumentException("Unknown dimension");
        w.x = (int) Math.floor(num(req, "x"));
        w.y = (int) Math.floor(num(req, "y"));
        w.z = (int) Math.floor(num(req, "z"));
        checkCoords(w.x, w.y, w.z);
        w.radius = clamp(req.has("radius") ? (int) num(req, "radius") : 16, 8, 48);
        w.timeLimitMin = clamp(req.has("timeLimit") ? (int) num(req, "timeLimit") : 10, 1, 60);
        w.announce = !req.has("announce") || req.get("announce").getAsBoolean();
        w.waveDefs = cleanWaves(req.has("waves") && req.get("waves").isJsonArray() ? req.getAsJsonArray("waves") : null);
        w.loot = req.has("loot") && req.get("loot").isJsonArray() ? req.getAsJsonArray("loot") : new JsonArray();
        if (stacks(w.loot).isEmpty()) throw new IllegalArgumentException("Add at least one loot item");

        challenges.put(w.id, w);
        save();

        boolean share = req.has("share") && req.get("share").getAsBoolean();
        if (share) shareWaypoint(w.name, w.dim, new BlockPos(w.x, w.y, w.z), 12);
        if (w.announce) {
            broadcast(prefix().append(Text.literal("A mob wave challenge appeared at ").formatted(Formatting.WHITE))
                    .append(Text.literal(w.name).formatted(Formatting.RED))
                    .append(Text.literal(" (" + w.x + " " + w.y + " " + w.z + "). Beat it for loot!").formatted(Formatting.WHITE)));
        }
        log.add("event", null, "Mob wave placed at " + w.name + " (" + w.x + " " + w.y + " " + w.z + ")");
        if (w.announce) Discord.send("event", ":crossed_swords: A mob wave challenge appeared at **" + Discord.clean(w.name) + "** (" + w.x + " " + w.y + " " + w.z + ")", null, null);
        return "Challenge placed at " + w.name + ". It starts when someone walks within " + w.radius + " blocks.";
    }

    public String cancel(String id) {
        Wave w = challenges.get(id);
        if (w == null) throw new IllegalArgumentException("That challenge is already over");
        if (w.state.equals("active")) notifyParticipants(w, prefix().append(Text.literal(w.name + " was cancelled by " + EverwynWatch.config().adminName + ".").formatted(Formatting.GRAY)));
        end(w, world(w.dim));
        String msg = "Cancelled challenge " + w.name;
        log.add("event", null, msg);
        return msg;
    }

    /** Called every server tick. */
    public void tick() {
        if (++tickCounter < 10) return;
        tickCounter = 0;
        long now = System.currentTimeMillis();
        for (Wave w : new ArrayList<>(challenges.values())) {
            ServerWorld world = world(w.dim);
            if (world == null) continue;
            try {
                tickOne(w, world, now);
            } catch (Exception e) {
                EverwynWatch.LOG.error("Everwyn Watch challenge {} crashed; cancelling it", w.name, e);
                end(w, world);
            }
        }
    }

    private void tickOne(Wave w, ServerWorld world, long now) {
        List<ServerPlayerEntity> inside = playersWithin(world, w, w.radius);

        if (w.state.equals("armed")) {
            if (!inside.isEmpty()) start(w, world, inside, now);
            return;
        }

        for (ServerPlayerEntity p : inside) {
            if (w.participants.add(p.getUuid()) && w.bar != null) w.bar.addPlayer(p);
        }

        if (now > w.endsAt) {
            fail(w, world, "Time ran out");
            return;
        }

        if (playersWithin(world, w, w.radius + 24).isEmpty()) {
            if (w.abandonedSince == 0) w.abandonedSince = now;
            else if (now - w.abandonedSince > 60_000) {
                fail(w, world, "Everyone left the area");
                return;
            }
        } else {
            w.abandonedSince = 0;
        }

        if (w.nextWaveAt > 0) {
            if (now >= w.nextWaveAt) {
                w.nextWaveAt = 0;
                spawnWave(w, world);
            }
            updateBar(w, now);
            return;
        }

        int alive = countAlive(w, world);
        updateBar(w, now);
        if (alive == 0) {
            if (w.waveIndex + 1 < w.waveDefs.size()) {
                w.waveIndex++;
                w.nextWaveAt = now + 5000;
                for (ServerPlayerEntity p : participantsOnline(w)) {
                    title(p, Text.literal("Wave cleared!").formatted(Formatting.GREEN),
                            Text.literal("Wave " + (w.waveIndex + 1) + "/" + w.waveDefs.size() + " incoming...").formatted(Formatting.YELLOW));
                }
            } else {
                complete(w, world);
            }
        }
    }

    private void start(Wave w, ServerWorld world, List<ServerPlayerEntity> inside, long now) {
        w.state = "active";
        w.waveIndex = 0;
        w.endsAt = now + w.timeLimitMin * 60_000L;
        w.abandonedSince = 0;
        w.runTag = MOB_TAG + "_" + w.id + "_" + Long.toHexString(world.getRandom().nextLong());
        w.bar = new ServerBossBar(Text.literal(w.name), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        for (ServerPlayerEntity p : inside) {
            w.participants.add(p.getUuid());
            w.bar.addPlayer(p);
            title(p, Text.literal(w.name).formatted(Formatting.RED, Formatting.BOLD),
                    Text.literal("Beat " + w.waveDefs.size() + (w.waveDefs.size() == 1 ? " wave" : " waves") + " in " + w.timeLimitMin + " min. Don't die!").formatted(Formatting.YELLOW));
        }
        if (w.announce) {
            broadcast(prefix().append(Text.literal(inside.get(0).getGameProfile().getName() + " started the challenge at ").formatted(Formatting.WHITE))
                    .append(Text.literal(w.name).formatted(Formatting.RED)).append(Text.literal("!").formatted(Formatting.WHITE)));
        }
        log.add("event", inside.get(0).getGameProfile().getName(), inside.get(0).getGameProfile().getName() + " started the challenge at " + w.name);
        spawnWave(w, world);
    }

    private void spawnWave(Wave w, ServerWorld world) {
        w.mobs.clear();
        JsonArray def = w.waveDefs.get(w.waveIndex).getAsJsonArray();
        List<ServerPlayerEntity> targets = playersWithin(world, w, w.radius + 8);
        for (JsonElement el : def) {
            JsonObject s = el.getAsJsonObject();
            EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(s.get("mob").getAsString()));
            int count = s.get("count").getAsInt();
            for (int i = 0; i < count; i++) {
                Entity e = type.create(world);
                if (!(e instanceof MobEntity mob)) {
                    if (e != null) e.discard();
                    continue;
                }
                double angle = world.getRandom().nextDouble() * Math.PI * 2;
                double dist = 3 + world.getRandom().nextDouble() * Math.max(2, w.radius * 0.6 - 3);
                int sx = w.x + (int) Math.round(Math.cos(angle) * dist);
                int sz = w.z + (int) Math.round(Math.sin(angle) * dist);
                BlockPos pos = findSpot(world, sx, w.y, sz);
                mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
                mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
                mob.setPersistent();
                mob.addCommandTag(MOB_TAG);
                mob.addCommandTag(w.runTag);
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 60, 0, false, false));
                if (!targets.isEmpty()) mob.setTarget(targets.get(world.getRandom().nextInt(targets.size())));
                w.mobs.add(mob.getUuid()); // before spawning, so the load check knows it's ours
                world.spawnEntityAndPassengers(mob);
                if (mob.isRemoved()) w.mobs.remove(mob.getUuid());
            }
        }
        w.waveTotal = w.mobs.size();
        for (ServerPlayerEntity p : participantsOnline(w)) {
            title(p, Text.literal("Wave " + (w.waveIndex + 1) + "/" + w.waveDefs.size()).formatted(Formatting.RED),
                    Text.literal(w.waveTotal + " mobs").formatted(Formatting.GRAY));
        }
    }

    private int countAlive(Wave w, ServerWorld world) {
        w.mobs.removeIf(u -> {
            Entity e = world.getEntity(u);
            return e == null || !e.isAlive();
        });
        return w.mobs.size();
    }

    private void updateBar(Wave w, long now) {
        if (w.bar == null) return;
        long left = Math.max(0, (w.endsAt - now) / 1000);
        String time = (left / 60) + ":" + String.format("%02d", left % 60);
        if (w.nextWaveAt > 0) {
            w.bar.setName(Text.literal(w.name + " - next wave soon - " + time));
            w.bar.setPercent(1f);
        } else {
            w.bar.setName(Text.literal(w.name + " - Wave " + (w.waveIndex + 1) + "/" + w.waveDefs.size()
                    + " - " + w.mobs.size() + " left - " + time));
            w.bar.setPercent(w.waveTotal == 0 ? 0f : Math.max(0f, Math.min(1f, w.mobs.size() / (float) w.waveTotal)));
        }
    }

    private void complete(Wave w, ServerWorld world) {
        BlockPos spot = findSpot(world, w.x, w.y, w.z);
        placeChest(world, spot, stacks(w.loot));
        lightning(world, spot);
        world.playSound(null, spot, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        String where = spot.getX() + " " + spot.getY() + " " + spot.getZ();
        for (ServerPlayerEntity p : participantsOnline(w)) {
            title(p, Text.literal("Challenge complete!").formatted(Formatting.GREEN, Formatting.BOLD),
                    Text.literal("Your loot chest is at " + where).formatted(Formatting.YELLOW));
        }
        MutableText msg = prefix().append(Text.literal(w.name).formatted(Formatting.GREEN))
                .append(Text.literal(" was beaten! Loot chest at " + where + ".").formatted(Formatting.WHITE));
        if (w.announce) broadcast(msg);
        else notifyParticipants(w, msg);
        log.add("event", null, "Challenge complete at " + w.name + ". Loot chest at " + where);
        Notifier.send("challenge", "Challenge beaten: " + w.name, "Loot chest at " + where, "trophy");
        Discord.send("event", ":trophy: **" + Discord.clean(w.name) + "** was beaten! Loot chest at " + where, null, null);
        end(w, world);
    }

    private void fail(Wave w, ServerWorld world, String reason) {
        for (ServerPlayerEntity p : participantsOnline(w)) {
            title(p, Text.literal("Challenge failed").formatted(Formatting.RED, Formatting.BOLD),
                    Text.literal(reason).formatted(Formatting.GRAY));
        }
        MutableText msg = prefix().append(Text.literal(w.name).formatted(Formatting.RED))
                .append(Text.literal(" failed: " + reason + ".").formatted(Formatting.WHITE));
        if (w.announce) broadcast(msg);
        else notifyParticipants(w, msg);
        log.add("event", null, "Challenge failed at " + w.name + ": " + reason);
        Notifier.send("challenge", "Challenge failed: " + w.name, reason, "skull");
        Discord.send("event", ":x: **" + Discord.clean(w.name) + "** failed: " + Discord.clean(reason), null, null);
        end(w, world);
    }

    /** Removes the challenge, its mobs and its boss bar. */
    private void end(Wave w, ServerWorld world) {
        if (world != null) {
            for (UUID u : w.mobs) {
                Entity e = world.getEntity(u);
                if (e != null) e.discard();
            }
        }
        w.mobs.clear();
        if (w.bar != null) {
            w.bar.clearPlayers();
            w.bar = null;
        }
        challenges.remove(w.id);
        save();
    }

    public void onPlayerDeath(ServerPlayerEntity player) {
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        for (Wave w : new ArrayList<>(challenges.values())) {
            if (!w.state.equals("active") || !w.dim.equals(dim)) continue;
            if (!w.participants.contains(player.getUuid())) continue;
            if (horizontalDist(player, w) <= w.radius + 24) {
                fail(w, world(w.dim), player.getGameProfile().getName() + " died");
            }
        }
    }

    /** Wave mobs left over from a crash/restart get removed when their chunk loads. */
    public void onEntityLoad(Entity entity) {
        if (!entity.getCommandTags().contains(MOB_TAG)) return;
        UUID id = entity.getUuid();
        for (Wave w : challenges.values()) {
            if (w.mobs.contains(id)) return;
        }
        server.execute(entity::discard);
    }

    /** Server is stopping: clean up running waves (they go back to waiting on next start). */
    public void shutdown() {
        for (Wave w : challenges.values()) {
            ServerWorld world = world(w.dim);
            if (world != null) {
                for (UUID u : w.mobs) {
                    Entity e = world.getEntity(u);
                    if (e != null) e.discard();
                }
            }
            w.mobs.clear();
            if (w.bar != null) w.bar.clearPlayers();
        }
        save();
    }

    public JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (Wave w : challenges.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.id);
            o.addProperty("name", w.name);
            o.addProperty("dim", w.dim);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("z", w.z);
            o.addProperty("radius", w.radius);
            o.addProperty("state", w.state);
            o.addProperty("wave", w.waveIndex + 1);
            o.addProperty("waves", w.waveDefs.size());
            o.addProperty("alive", w.mobs.size());
            o.addProperty("timeLimit", w.timeLimitMin);
            if (w.state.equals("active")) o.addProperty("endsAt", w.endsAt);
            arr.add(o);
        }
        return arr;
    }

    // ------------------------------------------------------------------ helpers

    static boolean allowedMob(EntityType<?> t) {
        return t.getSpawnGroup() == SpawnGroup.MONSTER && t.isSummonable() && t != EntityType.ENDER_DRAGON;
    }

    private static JsonArray cleanWaves(JsonArray in) {
        if (in == null || in.isEmpty()) throw new IllegalArgumentException("Add at least one mob");
        if (in.size() > MAX_WAVES) throw new IllegalArgumentException("Max " + MAX_WAVES + " waves");
        JsonArray out = new JsonArray();
        int n = 0;
        for (JsonElement waveEl : in) {
            n++;
            if (!waveEl.isJsonArray()) throw new IllegalArgumentException("Bad wave " + n);
            JsonArray wave = new JsonArray();
            int total = 0;
            for (JsonElement el : waveEl.getAsJsonArray()) {
                JsonObject s = el.getAsJsonObject();
                Identifier id = Identifier.tryParse(s.get("mob").getAsString());
                if (id == null || !Registries.ENTITY_TYPE.containsId(id)) throw new IllegalArgumentException("Unknown mob " + s.get("mob").getAsString());
                if (!allowedMob(Registries.ENTITY_TYPE.get(id))) throw new IllegalArgumentException(id + " can't be used in a wave");
                int count = clamp(s.get("count").getAsInt(), 1, 30);
                total += count;
                JsonObject c = new JsonObject();
                c.addProperty("mob", id.toString());
                c.addProperty("count", count);
                wave.add(c);
            }
            if (wave.isEmpty()) throw new IllegalArgumentException("Wave " + n + " has no mobs");
            if (total > MAX_MOBS_PER_WAVE) throw new IllegalArgumentException("Max " + MAX_MOBS_PER_WAVE + " mobs per wave");
            out.add(wave);
        }
        return out;
    }

    static List<ItemStack> stacks(JsonArray items) {
        List<ItemStack> out = new ArrayList<>();
        if (items == null) return out;
        if (items.size() > 27) throw new IllegalArgumentException("A chest holds 27 stacks");
        for (JsonElement el : items) {
            JsonObject o = el.getAsJsonObject();
            String raw = o.get("id").getAsString();
            Identifier id = Identifier.tryParse(raw);
            if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Unknown item " + raw);
            Item item = Registries.ITEM.get(id);
            if (item == Items.AIR) continue;
            int count = o.has("count") ? o.get("count").getAsInt() : 1;
            out.add(new ItemStack(item, clamp(count, 1, item.getMaxCount())));
        }
        return out;
    }

    /** Finds a free spot near (x, y, z) with solid ground below; falls back to the surface. */
    static BlockPos findSpot(ServerWorld world, int x, int y, int z) {
        world.getChunk(x >> 4, z >> 4); // make sure the chunk is loaded
        int bottom = world.getBottomY() + 1, top = world.getTopY() - 2;
        for (int d = 0; d <= 12; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int yy = y + d * sign;
                if (yy < bottom || yy > top) continue;
                BlockPos p = new BlockPos(x, yy, z);
                BlockState here = world.getBlockState(p);
                BlockState above = world.getBlockState(p.up());
                BlockState below = world.getBlockState(p.down());
                if (here.isReplaceable() && above.isReplaceable() && below.isSolidBlock(world, p.down())) return p;
                if (d == 0) break;
            }
        }
        return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private static void placeChest(ServerWorld world, BlockPos pos, List<ItemStack> stacks) {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState());
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            for (int i = 0; i < stacks.size() && i < chest.size(); i++) chest.setStack(i, stacks.get(i));
            chest.markDirty();
        }
    }

    private static void lightning(ServerWorld world, BlockPos pos) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos));
        bolt.setCosmetic(true);
        world.spawnEntity(bolt);
    }

    private void shareWaypoint(String name, String dim, BlockPos pos, int color) {
        JsonObject w = new JsonObject();
        w.addProperty("name", name);
        w.addProperty("dim", dim);
        w.addProperty("x", pos.getX());
        w.addProperty("y", pos.getY());
        w.addProperty("z", pos.getZ());
        w.addProperty("color", color);
        Text line = Text.literal(AdminActions.xaeroLine(w, server));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(line);
    }

    private List<ServerPlayerEntity> playersWithin(ServerWorld world, Wave w, int radius) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (horizontalDist(p, w) <= radius && Math.abs(p.getY() - w.y) <= radius + 16) out.add(p);
        }
        return out;
    }

    private static double horizontalDist(ServerPlayerEntity p, Wave w) {
        double dx = p.getX() - (w.x + 0.5), dz = p.getZ() - (w.z + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private List<ServerPlayerEntity> participantsOnline(Wave w) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (UUID u : w.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    private void notifyParticipants(Wave w, Text msg) {
        for (ServerPlayerEntity p : participantsOnline(w)) p.sendMessage(msg);
    }

    private static void title(ServerPlayerEntity p, Text title, Text subtitle) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private void broadcast(Text msg) {
        server.getPlayerManager().broadcast(msg, false);
    }

    private static MutableText prefix() {
        return Text.literal("[Event] ").formatted(Formatting.GOLD);
    }

    private ServerWorld world(String dim) {
        if (dim == null) return null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) return w;
        }
        return null;
    }

    private static void checkCoords(int x, int y, int z) {
        if (Math.abs(x) > 29_000_000 || Math.abs(z) > 29_000_000 || Math.abs(y) > 4096) {
            throw new IllegalArgumentException("Coordinates out of range");
        }
    }

    private static String cleanName(String s, String fallback) {
        if (s == null) return fallback;
        s = s.replaceAll("[\\p{Cntrl}:]", " ").trim();
        if (s.isEmpty()) return fallback;
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static double num(JsonObject o, String key) {
        try {
            double d = Double.parseDouble(o.get(key).getAsString());
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            return d;
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ saving

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Wave w = new Wave();
                w.id = o.get("id").getAsString();
                w.name = o.get("name").getAsString();
                w.dim = o.get("dim").getAsString();
                w.x = o.get("x").getAsInt();
                w.y = o.get("y").getAsInt();
                w.z = o.get("z").getAsInt();
                w.radius = o.get("radius").getAsInt();
                w.timeLimitMin = o.get("timeLimit").getAsInt();
                w.announce = o.get("announce").getAsBoolean();
                w.waveDefs = o.getAsJsonArray("waves");
                w.loot = o.getAsJsonArray("loot");
                challenges.put(w.id, w);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.error("Could not read {}", file, e);
        }
    }

    private void save() {
        JsonArray arr = new JsonArray();
        for (Wave w : challenges.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.id);
            o.addProperty("name", w.name);
            o.addProperty("dim", w.dim);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("z", w.z);
            o.addProperty("radius", w.radius);
            o.addProperty("timeLimit", w.timeLimitMin);
            o.addProperty("announce", w.announce);
            o.add("waves", w.waveDefs);
            o.add("loot", w.loot);
            arr.add(o);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer wr = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(arr, wr);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EverwynWatch.LOG.error("Could not save {}", file, e);
        }
    }
}
