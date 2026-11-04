package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

/** Admin buttons from the app. Always runs on the server thread. */
final class AdminActions {
    private AdminActions() {}

    /** Returns a short result message; throws IllegalArgumentException for bad input. */
    static String run(MinecraftServer server, EventLog events, Markers markers, Challenges challenges, JsonObject req) {
        String action = str(req, "action");
        String result = switch (action) {
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
        EverwynWatch.LOG.info("[Everwyn Watch] dashboard action: {}", result);
        events.add("admin", null, result);
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
