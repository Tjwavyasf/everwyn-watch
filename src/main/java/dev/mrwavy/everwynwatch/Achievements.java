package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A player's advancements ("achievements"), grouped by tab (Story, Nether, The End, Adventure, Husbandry,
 * and modded tabs). Live for online players; read from world/advancements/<uuid>.json for offline ones.
 * Server thread.
 */
final class Achievements {
    private Achievements() {}

    static JsonObject get(MinecraftServer server, String name) throws Exception {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        Map<String, Long> offlineDone = null;
        String shownName = name;
        if (online == null) {
            GameProfile profile = AdminActions.profileByName(server, name);
            if (profile == null || profile.getId() == null) throw new IllegalArgumentException("Never seen a player called " + name);
            shownName = profile.getName();
            offlineDone = readFile(server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(profile.getId() + ".json"));
        } else {
            shownName = online.getGameProfile().getName();
        }
        Map<String, JsonObject> tabs = new LinkedHashMap<>();
        int total = 0, done = 0;
        for (Advancement adv : server.getAdvancementLoader().getAdvancements()) {
            AdvancementDisplay d = adv.getDisplay();
            if (d == null) continue; // recipes and other hidden bookkeeping
            Advancement root = adv.getRoot();
            AdvancementDisplay rd = root.getDisplay();
            if (rd == null) continue;
            String tabId = root.getId().toString();
            JsonObject tab = tabs.computeIfAbsent(tabId, k -> {
                JsonObject t = new JsonObject();
                t.addProperty("id", k);
                t.addProperty("title", rd.getTitle().getString());
                t.addProperty("icon", Registries.ITEM.getId(rd.getIcon().getItem()).toString());
                t.add("items", new JsonArray());
                t.addProperty("total", 0);
                t.addProperty("done", 0);
                return t;
            });
            boolean isDone;
            long when = 0;
            float pct = 0;
            if (online != null) {
                AdvancementProgress p = online.getAdvancementTracker().getProgress(adv);
                isDone = p.isDone();
                pct = p.getProgressBarPercentage();
                Date first = p.getEarliestProgressObtainDate();
                if (isDone && first != null) when = first.getTime();
            } else {
                Long t = offlineDone.get(adv.getId().toString());
                isDone = t != null;
                if (t != null) when = t;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", adv.getId().toString());
            boolean secret = d.isHidden() && !isDone;
            o.addProperty("title", secret ? "???" : d.getTitle().getString());
            o.addProperty("desc", secret ? "A secret advancement" : d.getDescription().getString());
            o.addProperty("icon", Registries.ITEM.getId(d.getIcon().getItem()).toString());
            o.addProperty("frame", d.getFrame().getId());
            o.addProperty("done", isDone);
            if (when > 0) o.addProperty("time", when);
            if (!isDone && pct > 0 && pct < 1) o.addProperty("pct", Math.round(pct * 100));
            if (secret) o.addProperty("secret", true);
            tab.getAsJsonArray("items").add(o);
            tab.addProperty("total", tab.get("total").getAsInt() + 1);
            if (isDone) tab.addProperty("done", tab.get("done").getAsInt() + 1);
            total++;
            if (isDone) done++;
        }
        JsonObject out = new JsonObject();
        out.addProperty("name", shownName);
        out.addProperty("online", online != null);
        out.addProperty("total", total);
        out.addProperty("done", done);
        JsonArray arr = new JsonArray();
        tabs.values().forEach(arr::add);
        out.add("tabs", arr);
        return out;
    }

    /** Finished advancements from a player's save file: id -> when (ms). */
    private static Map<String, Long> readFile(Path file) {
        Map<String, Long> out = new HashMap<>();
        if (!Files.exists(file)) return out;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject a = e.getValue().getAsJsonObject();
                if (!a.has("done") || !a.get("done").getAsBoolean()) continue;
                long when = 0;
                if (a.has("criteria") && a.get("criteria").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> c : a.getAsJsonObject("criteria").entrySet()) {
                        try {
                            long t = fmt.parse(c.getValue().getAsString()).getTime();
                            if (when == 0 || t < when) when = t;
                        } catch (Exception ignored) {
                            // odd date
                        }
                    }
                }
                out.put(e.getKey(), when);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.debug("Couldn't read advancements {}", file, e);
        }
        return out;
    }
}
