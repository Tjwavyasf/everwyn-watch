package dev.mrwavy.everwynwatch;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mixes the voices near the player you're listening to into one quiet 16 kHz
 * stream the app can play. Fed by {@link VoicePlugin} (only when Simple Voice
 * Chat is installed). Nothing is recorded or saved; it only runs while you listen.
 */
final class VoiceBridge {
    static final int RATE = 16_000;
    private static final short[] RING = new short[RATE * 8];
    private static final long START = System.nanoTime();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static long writtenUntil = 0;

    static volatile boolean svcLoaded = false;
    static volatile MinecraftServer server;
    private static volatile UUID target;
    private static volatile long lastPoll;

    private VoiceBridge() {}

    static boolean active() {
        return target != null && System.currentTimeMillis() - lastPoll < 6000;
    }

    static UUID target() {
        return target;
    }

    /** Is this speaker close enough to the listened-to player to be heard by them? */
    static boolean inRange(UUID speaker, boolean whispering) {
        MinecraftServer srv = server;
        UUID t = target;
        if (srv == null || t == null) return false;
        if (speaker.equals(t)) return true;
        ServerPlayerEntity tp = srv.getPlayerManager().getPlayer(t), sp = srv.getPlayerManager().getPlayer(speaker);
        if (tp == null || sp == null || tp.getWorld() != sp.getWorld()) return false;
        double range = whispering ? 24 : 48;
        return tp.squaredDistanceTo(sp) <= range * range;
    }

    private static long now() {
        return (System.nanoTime() - START) * RATE / 1_000_000_000L;
    }

    private static void clearTo(long end) {
        if (end <= writtenUntil) return;
        long from = Math.max(writtenUntil, end - RING.length);
        for (long p = from; p < end; p++) RING[(int) (p % RING.length)] = 0;
        writtenUntil = end;
    }

    /** One 20 ms voice frame at 48 kHz from one speaker. */
    static synchronized void addFrame(UUID speaker, short[] pcm48k) {
        int n = pcm48k.length / 3;
        long now = now();
        long pos = NEXT.getOrDefault(speaker, 0L);
        if (pos < now - RATE / 10 || pos > now + RATE) pos = now; // fell behind or first frame: line up with "now"
        clearTo(pos + n);
        for (int i = 0; i < n; i++) {
            int avg = (pcm48k[i * 3] + pcm48k[i * 3 + 1] + pcm48k[i * 3 + 2]) / 3;
            int idx = (int) ((pos + i) % RING.length);
            int mixed = RING[idx] + avg;
            RING[idx] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
        }
        NEXT.put(speaker, pos + n);
    }

    /**
     * Web thread: audio after {@code after} (sample position), delayed slightly so
     * late frames can still be mixed in. Returns [8-byte position][16-bit samples].
     */
    static synchronized byte[] read(UUID who, long after) {
        if (!who.equals(target)) NEXT.clear();
        target = who;
        lastPoll = System.currentTimeMillis();
        long end = now() - RATE / 5;
        if (after <= 0 || after > end || after < end - RATE * 3L) after = end;
        clearTo(end);
        int count = (int) Math.min(end - after, RATE * 2L);
        ByteBuffer out = ByteBuffer.allocate(8 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.putLong(after + count);
        for (int i = 0; i < count; i++) out.putShort(RING[(int) ((after + i) % RING.length)]);
        return out.array();
    }
}
