package dev.mrwavy.everwynwatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Push notifications through ntfy (free app for iPhone/Android): the mod posts a
 * message to your private topic and the ntfy app shows it as a notification.
 */
final class Notifier {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Notifier() {}

    /** kind: join, firstJoin, leave, death, challenge, test */
    static void send(String kind, String title, String message, String tags) {
        WatchConfig cfg = EverwynWatch.config();
        if (cfg == null || cfg.ntfyTopic == null || cfg.ntfyTopic.isBlank()) return;
        if (!kind.equals("test")) {
            if (!cfg.notificationsEnabled) return;
            boolean on = switch (kind) {
                case "join" -> cfg.notifyJoin;
                case "firstJoin" -> cfg.notifyFirstJoin;
                case "leave" -> cfg.notifyLeave;
                case "death" -> cfg.notifyDeath;
                case "challenge" -> cfg.notifyChallenge;
                default -> false;
            };
            if (!on) return;
        }
        try {
            String server = cfg.ntfyServer == null || cfg.ntfyServer.isBlank() ? "https://ntfy.sh" : cfg.ntfyServer.replaceAll("/+$", "");
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(server + "/" + cfg.ntfyTopic))
                    .timeout(Duration.ofSeconds(15))
                    .header("Title", ascii(title))
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8));
            if (tags != null && !tags.isBlank()) req.header("Tags", tags);
            if (cfg.dashboardUrl != null && cfg.dashboardUrl.startsWith("http")) req.header("Click", cfg.dashboardUrl);
            HTTP.sendAsync(req.build(), HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null) EverwynWatch.LOG.debug("Notification failed", err);
                else if (res.statusCode() >= 400) EverwynWatch.LOG.warn("Notification rejected by ntfy: HTTP {}", res.statusCode());
            });
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Notification failed", e);
        }
    }

    /** HTTP headers must be plain text, so drop anything fancy from the title. */
    private static String ascii(String s) {
        String out = s.replaceAll("[^\\x20-\\x7E]", "").trim();
        return out.isEmpty() ? "Everwyn" : out;
    }
}
