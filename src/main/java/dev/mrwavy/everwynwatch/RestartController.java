package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Owner-requested countdown; process restart must be provided by the host supervisor. */
final class RestartController {
    private final Services s;
    private long stopAt;
    private int lastSeconds = 11;
    private boolean initiated;
    RestartController(Services services) { this.s = services; }

    void begin() {
        if (!s.cfg.hostRestartsOnStop)
            throw new IllegalArgumentException("Enable hostRestartsOnStop in everwyn-watch.json only after verifying Bisect restarts Minecraft after /stop. Otherwise use the Bisect Restart button.");
        if (initiated || stopAt != 0) throw new IllegalArgumentException("A restart countdown is already running.");
        stopAt = System.currentTimeMillis() + 10000;
        lastSeconds = 11;
        s.events.add("admin", null, "Server restart requested (10-second countdown)");
        message("Server restarts in 10 seconds! Save your work.");
    }

    JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", s.cfg.hostRestartsOnStop);
        o.addProperty("restartAt", stopAt);
        o.addProperty("active", stopAt > 0);
        return o;
    }

    void tick() {
        if (stopAt == 0 || initiated) return;
        int left = Math.max(0, (int)Math.ceil((stopAt - System.currentTimeMillis()) / 1000.0));
        if (left < lastSeconds) {
            lastSeconds = left;
            if (left == 5 || left == 3 || left == 2 || left == 1) message("Server restarts in " + left + " second" + (left == 1 ? "!" : "s!"));
        }
        if (left == 0) {
            initiated = true;
            message("Restarting now. Saving the world...");
            s.events.add("event", null, "Restart countdown finished; requesting graceful shutdown");
            // The host panel/supervisor, NOT this JVM, must start a new process.
            s.server.stop(false);
        }
    }

    private void message(String value) {
        s.server.getPlayerManager().broadcast(Text.literal("[Server] ").formatted(Formatting.RED)
            .append(Text.literal(value).formatted(Formatting.YELLOW)), false);
    }
}
