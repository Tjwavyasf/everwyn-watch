package dev.mrwavy.everwynwatch;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live view of a player's screen. The app asks for frames; while someone is
 * watching, the player's game (with this mod installed) sends a small JPEG a few
 * times a second. When nobody has asked for a few seconds it stops on its own.
 * The player sees "You feel like you're being watched..." while it's on.
 */
final class ScreenViewer {
    static final Identifier VIEW = new Identifier("everwyn-watch", "view");
    static final Identifier FRAME = new Identifier("everwyn-watch", "frame");

    /** fps, width, quality for low / medium / high */
    static final int[][] PRESETS = {{4, 360, 45}, {6, 480, 55}, {8, 720, 62}};

    private static final class Partial {
        int id = -1, count, got;
        byte[][] parts;
    }

    private static final class Watch {
        volatile long lastPoll;
        volatile int preset = 1;
        int sentPreset = -1;
    }

    record Frame(byte[] jpeg, long at) {}

    private static final Map<UUID, Partial> PARTIAL = new ConcurrentHashMap<>();
    private static final Map<UUID, Frame> LATEST = new ConcurrentHashMap<>();
    private static final Map<UUID, Watch> WATCHING = new ConcurrentHashMap<>();
    private static int ticks;

    private ScreenViewer() {}

    /** Called once at startup, before anyone joins. */
    static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(FRAME, (server, player, handler, buf, sender) -> {
            try {
                onChunk(player.getUuid(), buf);
            } catch (Exception e) {
                EverwynWatch.LOG.debug("Bad screen frame from {}", player.getGameProfile().getName(), e);
            }
        });
    }

    private static void onChunk(UUID player, PacketByteBuf buf) {
        if (!WATCHING.containsKey(player)) return; // nobody asked
        int id = buf.readVarInt(), index = buf.readVarInt(), count = buf.readVarInt();
        byte[] data = buf.readByteArray(40_000);
        if (count < 1 || count > 64 || index < 0 || index >= count) return;
        Partial p = PARTIAL.computeIfAbsent(player, k -> new Partial());
        synchronized (p) {
            if (p.id != id) {
                p.id = id;
                p.count = count;
                p.got = 0;
                p.parts = new byte[count][];
            }
            if (p.parts[index] == null) {
                p.parts[index] = data;
                p.got++;
            }
            if (p.got == p.count) {
                int len = 0;
                for (byte[] part : p.parts) len += part.length;
                if (len > 2_000_000) return;
                byte[] all = new byte[len];
                int off = 0;
                for (byte[] part : p.parts) {
                    System.arraycopy(part, 0, all, off, part.length);
                    off += part.length;
                }
                LATEST.put(player, new Frame(all, System.currentTimeMillis()));
                p.id = -1;
            }
        }
    }

    /** Does this player's game have the mod (so it can send its screen)? */
    static boolean hasMod(ServerPlayerEntity p) {
        return ServerPlayNetworking.canSend(p, VIEW);
    }

    /** Web thread: the app wants the latest picture. Keeps the stream alive. */
    static Frame poll(UUID player, int preset) {
        Watch w = WATCHING.computeIfAbsent(player, k -> new Watch());
        w.lastPoll = System.currentTimeMillis();
        w.preset = Math.max(0, Math.min(PRESETS.length - 1, preset));
        Frame f = LATEST.get(player);
        return f != null && System.currentTimeMillis() - f.at() < 5000 ? f : null;
    }

    /** Server thread: start/stop streams and show the "being watched" hint. */
    static void tick(MinecraftServer server) {
        if (++ticks % 10 != 0) return;
        long now = System.currentTimeMillis();
        boolean hint = ticks % 40 == 0;
        for (Map.Entry<UUID, Watch> e : WATCHING.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            Watch w = e.getValue();
            if (p == null) {
                WATCHING.remove(e.getKey());
                LATEST.remove(e.getKey());
                continue;
            }
            if (now - w.lastPoll > 6000) {
                if (hasMod(p)) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeBoolean(false);
                    ServerPlayNetworking.send(p, VIEW, buf);
                }
                WATCHING.remove(e.getKey());
                LATEST.remove(e.getKey());
                continue;
            }
            if (hasMod(p) && w.sentPreset != w.preset) {
                int[] pr = PRESETS[w.preset];
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(true);
                buf.writeVarInt(pr[0]);
                buf.writeVarInt(pr[1]);
                buf.writeFloat(pr[2] / 100f);
                ServerPlayNetworking.send(p, VIEW, buf);
                w.sentPreset = w.preset;
            }
            if (hint) watchedHint(p);
        }
        // Listening to someone's voice also counts as watching them
        if (hint && VoiceBridge.active()) {
            UUID t = VoiceBridge.target();
            if (t != null && !WATCHING.containsKey(t)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(t);
                if (p != null) watchedHint(p);
            }
        }
    }

    static boolean isWatched(UUID player) {
        Watch w = WATCHING.get(player);
        return w != null && System.currentTimeMillis() - w.lastPoll < 6000;
    }

    private static void watchedHint(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("You feel like you're being watched...").formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }
}
