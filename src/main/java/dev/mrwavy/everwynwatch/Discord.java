package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Posts server chat and events into a Discord channel through a webhook. */
final class Discord {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EverwynWatch-Discord");
        t.setDaemon(true);
        return t;
    });

    private Discord() {}

    /** kind: chat, join, death, event, server. username/uuid make it look like the player said it. */
    static void send(String kind, String text, String username, String uuid) {
        WatchConfig cfg = EverwynWatch.config();
        if (cfg == null || cfg.discordWebhook == null || !cfg.discordWebhook.startsWith("https://")) return;
        boolean on = switch (kind) {
            case "chat" -> cfg.discordChat;
            case "join" -> cfg.discordJoins;
            case "death" -> cfg.discordDeaths;
            case "event" -> cfg.discordEvents;
            case "server", "test" -> cfg.discordServer || kind.equals("test");
            default -> false;
        };
        if (!on) return;
        String url = cfg.discordWebhook;
        JsonObject body = new JsonObject();
        body.addProperty("content", text.length() > 1900 ? text.substring(0, 1900) : text);
        body.addProperty("username", username != null ? username : cfg.serverName);
        if (uuid != null) body.addProperty("avatar_url", "https://mc-heads.net/avatar/" + uuid + "/64");
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray()); // never ping @everyone etc.
        body.add("allowed_mentions", mentions);
        String json = body.toString();
        QUEUE.execute(() -> {
            try {
                HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429) Thread.sleep(3000);
                else if (res.statusCode() >= 400) EverwynWatch.LOG.warn("Discord webhook said HTTP {}: {}", res.statusCode(), res.body());
                Thread.sleep(700); // stay under Discord's rate limit
            } catch (Exception e) {
                EverwynWatch.LOG.debug("Discord post failed", e);
            }
        });
    }

    /** Escape Discord formatting in player text. */
    static String clean(String s) {
        return s.replaceAll("([*_~`|>])", "\\\\$1");
    }
}
