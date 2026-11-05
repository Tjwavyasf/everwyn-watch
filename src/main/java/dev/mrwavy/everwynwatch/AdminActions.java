package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Admin buttons from the app. Always runs on the server thread. */
final class AdminActions {
    private AdminActions() {}

    /** Returns a short result message; throws IllegalArgumentException for bad input. */
    static String run(Services s, String actor, JsonObject req) {
        MinecraftServer server = s.server;
        EventLog events = s.events;
        Markers markers = s.markers;
        Challenges challenges = s.challenges;
        DeathVault vault = s.vault;
        String action = str(req, "action");
        String result = switch (action) {
            case "account_add" -> {
                String token = s.accounts.add(str(req, "name"), str(req, "role"));
                yield "TOKEN:" + token;
            }
            case "account_remove" -> s.accounts.remove(str(req, "name"));
            case "clear_items" -> {
                Integer cx = req.has("cx") ? (int) num(req, "cx") : null, cz = req.has("cz") ? (int) num(req, "cz") : null;
                int n = PerfMonitor.clearItems(server, req.has("dim") ? str(req, "dim") : null, cx, cz);
                yield "Removed " + n + " dropped item" + (n == 1 ? "" : "s");
            }
            case "clear_hostiles" -> {
                int n = PerfMonitor.clearHostiles(server, str(req, "dim"), (int) num(req, "cx"), (int) num(req, "cz"));
                yield "Removed " + n + " hostile mob" + (n == 1 ? "" : "s") + " (named and tamed ones are kept)";
            }
            case "ledger_rollback" -> s.ledger.rollback(str(req, "dim"), (int) num(req, "x"), (int) num(req, "y"), (int) num(req, "z"),
                    (int) num(req, "radius"), (int) num(req, "hours"), req.has("player") ? str(req, "player") : null);
            case "schedule_add" -> s.scheduler.add(req);
            case "schedule_remove" -> s.scheduler.remove(str(req, "id"));
            case "schedule_run" -> s.scheduler.runNow(str(req, "id"));
            case "leaderboard_public" -> {
                s.cfg.publicLeaderboard = req.get("on").getAsBoolean();
                s.cfg.save();
                yield s.cfg.publicLeaderboard ? "Public leaderboard is on" : "Public leaderboard is off";
            }
            case "discord_settings" -> {
                WatchConfig cfg = s.cfg;
                if (req.has("webhook")) {
                    String url = str(req, "webhook").trim();
                    if (!url.isEmpty() && !url.matches("https://(discord|discordapp|canary\\.discord|ptb\\.discord)\\.com/api/webhooks/.+"))
                        throw new IllegalArgumentException("That doesn't look like a Discord webhook link");
                    cfg.discordWebhook = url;
                }
                if (req.has("chat")) cfg.discordChat = req.get("chat").getAsBoolean();
                if (req.has("joins")) cfg.discordJoins = req.get("joins").getAsBoolean();
                if (req.has("deaths")) cfg.discordDeaths = req.get("deaths").getAsBoolean();
                if (req.has("events")) cfg.discordEvents = req.get("events").getAsBoolean();
                if (req.has("server")) cfg.discordServer = req.get("server").getAsBoolean();
                cfg.save();
                yield "Discord settings saved";
            }
            case "discord_test" -> {
                if (s.cfg.discordWebhook == null || s.cfg.discordWebhook.isEmpty()) throw new IllegalArgumentException("Paste a webhook link first");
                Discord.send("test", ":white_check_mark: Everwyn Watch is connected to this channel.", null, null);
                yield "Test message sent to Discord";
            }
            case "kill" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.kill();
                yield "Killed " + p.getGameProfile().getName();
            }
            case "smite" -> {
                ServerPlayerEntity p = player(server, req, "player");
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(p.getWorld());
                if (bolt == null) throw new IllegalArgumentException("Couldn't make lightning");
                bolt.refreshPositionAfterTeleport(p.getPos());
                p.getWorld().spawnEntity(bolt);
                yield "Smote " + p.getGameProfile().getName() + " with lightning";
            }
            case "feed" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(20f);
                yield "Fed " + p.getGameProfile().getName();
            }
            case "clear_effects" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.clearStatusEffects();
                yield "Cleared effects on " + p.getGameProfile().getName();
            }
            case "clear_inventory" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.getInventory().clear();
                p.playerScreenHandler.sendContentUpdates();
                yield "Cleared " + p.getGameProfile().getName() + "'s inventory";
            }
            case "give" -> {
                ServerPlayerEntity p = player(server, req, "player");
                List<ItemStack> stacks = Challenges.stacks(req.has("items") && req.get("items").isJsonArray() ? req.getAsJsonArray("items") : null);
                if (stacks.isEmpty()) throw new IllegalArgumentException("Pick at least one item");
                for (ItemStack st : stacks) p.getInventory().offerOrDrop(st);
                p.playerScreenHandler.sendContentUpdates();
                yield "Gave " + p.getGameProfile().getName() + " " + stacks.size() + " stack" + (stacks.size() == 1 ? "" : "s");
            }
            case "to_spawn" -> {
                ServerPlayerEntity p = player(server, req, "player");
                ServerWorld ow = server.getOverworld();
                BlockPos sp = Challenges.findSpot(ow, ow.getSpawnPos().getX(), ow.getSpawnPos().getY(), ow.getSpawnPos().getZ());
                p.teleport(ow, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYaw(), p.getPitch());
                yield "Sent " + p.getGameProfile().getName() + " to spawn";
            }
            case "ban" -> {
                String name = str(req, "player");
                ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
                GameProfile profile = online != null ? online.getGameProfile() : profileByName(server, name);
                if (profile == null) throw new IllegalArgumentException("Never seen a player called " + name);
                String reason = req.has("text") && !str(req, "text").isBlank() ? limit(str(req, "text")) : "Banned by an admin";
                int hours = req.has("hours") ? (int) num(req, "hours") : 0;
                Date expires = hours > 0 ? new Date(System.currentTimeMillis() + hours * 3_600_000L) : null;
                server.getPlayerManager().getUserBanList().add(new BannedPlayerEntry(profile, new Date(), "Everwyn Watch", expires, reason));
                if (online != null) online.networkHandler.disconnect(Text.literal("You are banned from this server.\nReason: " + reason
                        + (expires != null ? "\nUntil: " + expires : "")));
                yield "Banned " + profile.getName() + (hours > 0 ? " for " + hours + "h" : "") + ": " + reason;
            }
            case "unban" -> {
                String name = str(req, "player");
                BannedPlayerList list = server.getPlayerManager().getUserBanList();
                GameProfile profile = profileByName(server, name);
                if (profile == null || !list.contains(profile)) throw new IllegalArgumentException(name + " isn't banned");
                list.remove(profile);
                yield "Unbanned " + profile.getName();
            }
            case "time" -> {
                ServerWorld w = world(server, req.has("dim") ? str(req, "dim") : "minecraft:overworld");
                if (w == null) throw new IllegalArgumentException("Unknown dimension");
                long target = switch (str(req, "value")) {
                    case "day" -> 1000;
                    case "noon" -> 6000;
                    case "night" -> 13000;
                    case "midnight" -> 18000;
                    default -> throw new IllegalArgumentException("Unknown time");
                };
                long now = w.getTimeOfDay();
                long next = now - Math.floorMod(now, 24000) + target;
                if (next < now) next += 24000;
                for (ServerWorld any : server.getWorlds()) any.setTimeOfDay(next);
                yield "Time set to " + str(req, "value");
            }
            case "weather" -> {
                ServerWorld w = server.getOverworld();
                switch (str(req, "value")) {
                    case "clear" -> w.setWeather(12000, 0, false, false);
                    case "rain" -> w.setWeather(0, 6000, true, false);
                    case "thunder" -> w.setWeather(0, 6000, true, true);
                    default -> throw new IllegalArgumentException("Unknown weather");
                }
                yield "Weather set to " + str(req, "value");
            }
            case "restore_death" -> vault.restore(str(req, "id"), !req.has("xp") || req.get("xp").getAsBoolean());
            case "notify_settings" -> {
                WatchConfig cfg = EverwynWatch.config();
                if (req.has("enabled")) cfg.notificationsEnabled = req.get("enabled").getAsBoolean();
                if (req.has("join")) cfg.notifyJoin = req.get("join").getAsBoolean();
                if (req.has("firstJoin")) cfg.notifyFirstJoin = req.get("firstJoin").getAsBoolean();
                if (req.has("leave")) cfg.notifyLeave = req.get("leave").getAsBoolean();
                if (req.has("death")) cfg.notifyDeath = req.get("death").getAsBoolean();
                if (req.has("challenge")) cfg.notifyChallenge = req.get("challenge").getAsBoolean();
                if (req.has("lag")) cfg.notifyLag = req.get("lag").getAsBoolean();
                if (req.has("server")) cfg.notifyServer = req.get("server").getAsBoolean();
                cfg.save();
                yield "Notification settings saved";
            }
            case "notify_test" -> {
                Notifier.send("test", "Everwyn Watch", "Notifications are working! You'll get alerts from " + EverwynWatch.config().serverName + " here.", "white_check_mark");
                yield "Test notification sent";
            }
            case "package" -> challenges.dropPackage(
                    req.has("name") ? str(req, "name") : "Care package", str(req, "dim"),
                    (int) Math.floor(num(req, "x")), (int) Math.floor(num(req, "y")), (int) Math.floor(num(req, "z")),
                    req.has("items") && req.get("items").isJsonArray() ? req.getAsJsonArray("items") : null,
                    !req.has("announce") || req.get("announce").getAsBoolean(),
                    req.has("share") && req.get("share").getAsBoolean());
            case "wave_create" -> challenges.createWave(req);
            case "wave_cancel" -> challenges.cancel(str(req, "id"));
            case "waypoint_add" -> {
                String name = waypointName(str(req, "name"));
                String dim = str(req, "dim");
                if (world(server, dim) == null) throw new IllegalArgumentException("Unknown dimension");
                int x = (int) Math.floor(num(req, "x")), y = (int) Math.floor(num(req, "y")), z = (int) Math.floor(num(req, "z"));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096) {
                    throw new IllegalArgumentException("Coordinates out of range");
                }
                int color = req.has("color") ? (int) num(req, "color") : 10;
                markers.addWaypoint(name, dim, x, y, z, color);
                yield "Added waypoint " + name;
            }
            case "waypoint_delete" -> {
                JsonObject w = markers.waypoint(str(req, "id"));
                markers.removeWaypoint(str(req, "id"));
                yield "Deleted waypoint " + w.get("name").getAsString();
            }
            case "waypoint_share" -> {
                JsonObject w = markers.waypoint(str(req, "id"));
                String line = xaeroLine(w, server);
                String to = req.has("to") ? str(req, "to") : "all";
                int sent = 0;
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (to.equals("all") || p.getGameProfile().getName().equals(to)) {
                        p.sendMessage(Text.literal(line));
                        sent++;
                    }
                }
                if (sent == 0) throw new IllegalArgumentException("Nobody online to send it to");
                yield "Shared waypoint " + w.get("name").getAsString() + (to.equals("all") ? " with everyone" : " with " + to);
            }
            case "broadcast" -> {
                String text = limit(str(req, "text"));
                server.getPlayerManager().broadcast(
                        Text.literal("[Server] ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(Formatting.WHITE)), false);
                yield "Broadcast sent";
            }
            case "message" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.sendMessage(Text.literal("[Admin] ").formatted(Formatting.GOLD)
                        .append(Text.literal(limit(str(req, "text"))).formatted(Formatting.WHITE)));
                yield "Message sent to " + p.getGameProfile().getName();
            }
            case "kick" -> {
                ServerPlayerEntity p = player(server, req, "player");
                String reason = req.has("text") ? limit(str(req, "text")) : "Kicked by an admin";
                p.networkHandler.disconnect(Text.literal(reason));
                yield "Kicked " + p.getGameProfile().getName();
            }
            case "heal" -> {
                ServerPlayerEntity p = player(server, req, "player");
                p.setHealth(p.getMaxHealth());
                p.getHungerManager().setFoodLevel(20);
                p.extinguish();
                yield "Healed " + p.getGameProfile().getName();
            }
            case "gamemode" -> {
                ServerPlayerEntity p = player(server, req, "player");
                GameMode mode = GameMode.byName(str(req, "mode"), null);
                if (mode == null) throw new IllegalArgumentException("Unknown gamemode");
                p.changeGameMode(mode);
                yield p.getGameProfile().getName() + " is now in " + mode.getName();
            }
            case "tp_to_player" -> {
                ServerPlayerEntity p = player(server, req, "player");
                ServerPlayerEntity target = player(server, req, "target");
                p.teleport((ServerWorld) target.getWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                yield "Teleported " + p.getGameProfile().getName() + " to " + target.getGameProfile().getName();
            }
            case "tp_coords" -> {
                ServerPlayerEntity p = player(server, req, "player");
                double x = num(req, "x"), y = num(req, "y"), z = num(req, "z");
                ServerWorld world = (ServerWorld) p.getWorld();
                if (req.has("dim")) {
                    String dim = str(req, "dim");
                    world = null;
                    for (ServerWorld w : server.getWorlds()) {
                        if (w.getRegistryKey().getValue().toString().equals(dim)) world = w;
                    }
                    if (world == null) throw new IllegalArgumentException("Unknown dimension");
                }
                if (y < world.getBottomY() - 64 || y > world.getTopY() + 512 || Math.abs(x) > 3.0e7 || Math.abs(z) > 3.0e7) {
                    throw new IllegalArgumentException("Coordinates out of range");
                }
                p.teleport(world, x, y, z, p.getYaw(), p.getPitch());
                yield "Teleported " + p.getGameProfile().getName() + " to " + (int) x + " " + (int) y + " " + (int) z;
            }
            default -> throw new IllegalArgumentException("Unknown action");
        };
        EverwynWatch.LOG.info("[Everwyn Watch] {}: {}", actor, result.startsWith("TOKEN:") ? "created a login" : result);
        if (!result.startsWith("TOKEN:")) events.add("admin", actor, result);
        return result;
    }

    /**
     * Builds the chat line Xaero's Minimap turns into an "[Add]" button, e.g.
     * xaero-waypoint:Base:B:100:64:-200:10:false:0:Internal_overworld_waypoints
     */
    static String xaeroLine(JsonObject w, MinecraftServer server) {
        String name = w.get("name").getAsString();
        String initial = name.replaceAll("[^A-Za-z0-9]", "");
        initial = initial.isEmpty() ? "W" : initial.substring(0, 1).toUpperCase();
        String dimId = w.get("dim").getAsString();
        String path = dimId.contains(":") ? dimId.substring(dimId.indexOf(':') + 1) : dimId;
        String sep = EverwynWatch.config().xaeroUnderscoreFormat ? "_" : "-";
        String dim = "Internal" + sep + path.replace("_", sep).replace("/", sep) + sep + "waypoints";
        return "xaero-waypoint:" + name + ":" + initial + ":"
                + w.get("x").getAsInt() + ":" + w.get("y").getAsInt() + ":" + w.get("z").getAsInt() + ":"
                + w.get("color").getAsInt() + ":false:0:" + dim;
    }

    private static String waypointName(String s) {
        s = s.replaceAll("[\\p{Cntrl}:]", " ").trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Give the waypoint a name");
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private static ServerWorld world(MinecraftServer server, String dim) {
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) return w;
        }
        return null;
    }

    static GameProfile profileByName(MinecraftServer server, String name) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) return online.getGameProfile();
        if (server.getUserCache() == null) return null;
        Optional<GameProfile> p = server.getUserCache().findByName(name);
        return p.orElse(null);
    }

    private static ServerPlayerEntity player(MinecraftServer server, JsonObject req, String field) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(str(req, field));
        if (p == null) throw new IllegalArgumentException("That player isn't online");
        return p;
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) throw new IllegalArgumentException("Missing " + key);
        return o.get(key).getAsString();
    }

    private static double num(JsonObject o, String key) {
        try {
            double d = Double.parseDouble(str(o, key));
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    private static String limit(String s) {
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Message is empty");
        return s.length() > 256 ? s.substring(0, 256) : s;
    }
}
