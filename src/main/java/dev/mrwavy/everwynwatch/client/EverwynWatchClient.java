package dev.mrwavy.everwynwatch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The player's side of spectating. Does nothing at all until the server says an
 * admin is watching; then it sends a small, low-frame-rate picture of the screen
 * until the server says stop (or the player leaves).
 */
public class EverwynWatchClient implements ClientModInitializer {
    private static final Identifier VIEW = new Identifier("everwyn-watch", "view");
    private static final Identifier FRAME = new Identifier("everwyn-watch", "frame");
    private static final int CHUNK = 30_000; // stays under Minecraft's packet size limit

    private static volatile boolean active;
    private static volatile int fps = 6, width = 480;
    private static volatile float quality = 0.55f;
    private static volatile boolean busy;
    private static long last;
    private static int frameId;
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EverwynWatch-Screen");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(VIEW, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            if (on) {
                fps = Math.max(1, Math.min(10, buf.readVarInt()));
                width = Math.max(160, Math.min(1280, buf.readVarInt()));
                quality = Math.max(0.2f, Math.min(0.9f, buf.readFloat()));
            }
            active = on;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
        ClientTickEvents.END_CLIENT_TICK.register(EverwynWatchClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (!active || busy || client.player == null || client.getFramebuffer() == null) return;
        long now = System.currentTimeMillis();
        if (now - last < 1000 / fps) return;
        last = now;
        NativeImage shot;
        try {
            shot = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        } catch (Exception e) {
            return;
        }
        busy = true;
        int id = ++frameId;
        int w = width;
        float q = quality;
        ENCODER.execute(() -> {
            try {
                byte[] jpg = encode(shot, w, q);
                int count = (jpg.length + CHUNK - 1) / CHUNK;
                for (int i = 0; i < count; i++) {
                    int from = i * CHUNK, len = Math.min(CHUNK, jpg.length - from);
                    byte[] part = new byte[len];
                    System.arraycopy(jpg, from, part, 0, len);
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(id);
                    buf.writeVarInt(i);
                    buf.writeVarInt(count);
                    buf.writeByteArray(part);
                    client.execute(() -> {
                        if (active && ClientPlayNetworking.canSend(FRAME)) ClientPlayNetworking.send(FRAME, buf);
                    });
                }
            } catch (Exception ignored) {
                // skip this frame
            } finally {
                shot.close();
                busy = false;
            }
        });
    }

    /** Shrinks the screenshot and turns it into a JPEG. */
    private static byte[] encode(NativeImage img, int maxWidth, float quality) throws Exception {
        int w = img.getWidth(), h = img.getHeight();
        int tw = Math.min(maxWidth, w), th = Math.max(1, Math.round(h * (tw / (float) w)));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[tw];
        for (int y = 0; y < th; y++) {
            int sy = (int) ((long) y * h / th);
            for (int x = 0; x < tw; x++) {
                int c = img.getColor((int) ((long) x * w / tw), sy); // ABGR
                row[x] = ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
            }
            out.setRGB(0, y, tw, 1, row, 0, tw);
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
