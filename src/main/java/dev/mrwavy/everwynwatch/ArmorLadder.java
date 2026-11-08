package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a straight, weakest-to-strongest armor ladder for the RPG Level mod from every armor item on the
 * server (vanilla and every mod): leather gets the lowest level, Metallurgium the highest, everything else in
 * between by real armor strength. "Apply" backs up config/rpglevel/restrictions, removes the old armor
 * equip locks from every restriction file, and writes the ladder into restrictions/minecraft/defense.json.
 */
final class ArmorLadder {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Set_(String material, List<String> items, double score) {}

    private ArmorLadder() {}

    static Path rpgDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rpglevel");
    }

    /** Every armor material with its pieces and a strength score (protection + toughness + knockback, for a full set). */
    private static List<Set_> sets() {
        Map<ArmorMaterial, List<ArmorItem>> byMat = new IdentityHashMap<>();
        for (Item item : Registries.ITEM) {
            if (item instanceof ArmorItem a) byMat.computeIfAbsent(a.getMaterial(), k -> new ArrayList<>()).add(a);
        }
        List<Set_> out = new ArrayList<>();
        for (Map.Entry<ArmorMaterial, List<ArmorItem>> e : byMat.entrySet()) {
            double sum = 0;
            List<String> ids = new ArrayList<>();
            for (ArmorItem a : e.getValue()) {
                sum += a.getProtection() + a.getToughness() + 10 * a.getMaterial().getKnockbackResistance();
                ids.add(Registries.ITEM.getId(a).toString());
            }
            double perSet = sum / e.getValue().size() * 4; // compare sets fairly even if a material has fewer pieces
            String name;
            try {
                name = e.getKey().getName();
            } catch (Throwable t) {
                name = ids.get(0);
            }
            ids.sort(String::compareTo);
            out.add(new Set_(name, ids, Math.round(perSet * 100) / 100.0));
        }
        out.sort((a, b) -> Double.compare(a.score(), b.score()));
        return out;
    }

    /** The ladder: [{material, items, score, level}], weakest first. spacing "even" (equal steps) or "strength". */
    static JsonObject preview(int lo, int hi, String spacing) {
        List<Set_> sets = sets();
        if (sets.isEmpty()) throw new IllegalArgumentException("No armor found");
        lo = Math.max(0, lo);
        hi = Math.max(lo, hi);
        double floor = sets.get(0).score(), top = sets.get(sets.size() - 1).score();
        for (Set_ s : sets) {
            String m = s.material().toLowerCase();
            if (m.endsWith("leather") || m.equals("leather")) floor = s.score();
            if (m.contains("metallurgium")) top = s.score();
        }
        // distinct strengths from leather up to Metallurgium, for even steps
        List<Double> steps = new ArrayList<>();
        for (Set_ s : sets) if (s.score() >= floor && s.score() <= top && !steps.contains(s.score())) steps.add(s.score());
        JsonArray arr = new JsonArray();
        for (Set_ s : sets) {
            int level;
            if (s.score() <= floor) level = lo;
            else if (s.score() >= top) level = hi;
            else if ("strength".equals(spacing)) level = lo + (int) Math.round((s.score() - floor) / (top - floor) * (hi - lo));
            else level = lo + (int) Math.round((double) steps.indexOf(s.score()) / Math.max(1, steps.size() - 1) * (hi - lo));
            JsonObject o = new JsonObject();
            o.addProperty("material", s.material());
            o.addProperty("score", s.score());
            o.addProperty("level", level);
            JsonArray items = new JsonArray();
            s.items().forEach(items::add);
            o.add("items", items);
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.add("sets", arr);
        out.addProperty("found", Files.isDirectory(rpgDir().resolve("restrictions")));
        out.addProperty("maxLevel", defenseMax());
        return out;
    }

    /** The Defense skill's max level from config/rpglevel/skills/defense.json (50 if it can't be read). */
    static int defenseMax() {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(rpgDir().resolve("skills").resolve("defense.json"))).getAsJsonObject();
            return o.get("maxLevel").getAsInt();
        } catch (Exception e) {
            return 50;
        }
    }

    /** Writes the ladder into the RPG Level config (with a backup first). Web thread. */
    static synchronized String apply(int lo, int hi, String spacing) throws Exception {
        Path dir = rpgDir().resolve("restrictions");
        if (!Files.isDirectory(dir)) throw new IllegalArgumentException("Couldn't find config/rpglevel/restrictions on the server");
        JsonArray ladder = preview(lo, hi, spacing).getAsJsonArray("sets");
        Set<String> armorIds = new LinkedHashSet<>();
        TreeMap<Integer, List<String>> byLevel = new TreeMap<>();
        for (JsonElement el : ladder) {
            JsonObject s = el.getAsJsonObject();
            int level = s.get("level").getAsInt();
            for (JsonElement id : s.getAsJsonArray("items")) {
                armorIds.add(id.getAsString());
                byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(id.getAsString());
            }
        }
        return rewrite(dir, armorIds, byLevel) + " (" + lo + "–" + hi + "). Restart the server so RPG Level loads it.";
    }

    /** Backs up the restrictions folder, removes the given armor from old equip locks, writes the ladder. */
    static String rewrite(Path dir, Set<String> armorIds, TreeMap<Integer, List<String>> byLevel) throws Exception {
        // 1. back up the whole restrictions folder
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = dir.resolveSibling("restrictions_backup_before_everwyn_ladder_" + stamp + ".zip");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        try (OutputStream os = Files.newOutputStream(backup); ZipOutputStream zip = new ZipOutputStream(os)) {
            for (Path f : files) {
                zip.putNextEntry(new ZipEntry("restrictions/" + dir.relativize(f).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(f));
                zip.closeEntry();
            }
        }
        // 2. take the armor pieces out of the old equip locks everywhere
        int removed = 0, changedFiles = 0;
        for (Path f : files) {
            if (!f.toString().endsWith(".json")) continue;
            JsonElement root;
            try {
                root = JsonParser.parseString(Files.readString(f));
            } catch (Exception bad) {
                continue; // leave files we can't read alone
            }
            if (!root.isJsonArray()) continue;
            JsonArray out = new JsonArray();
            boolean changed = false;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) { out.add(el); continue; }
                JsonObject e = el.getAsJsonObject();
                boolean equip = false;
                if (e.has("actions") && e.get("actions").isJsonArray()) for (JsonElement a : e.getAsJsonArray("actions")) if ("equip_item".equals(a.getAsString())) equip = true;
                if (!equip || !e.has("itemIds") || !e.get("itemIds").isJsonArray()) { out.add(e); continue; }
                JsonArray keep = new JsonArray();
                for (JsonElement id : e.getAsJsonArray("itemIds")) {
                    if (armorIds.contains(id.getAsString())) { removed++; changed = true; } else keep.add(id);
                }
                if (keep.isEmpty() && !e.has("blockIds") && !e.has("blockTags")) continue; // nothing left in it
                e.add("itemIds", keep);
                out.add(e);
            }
            if (changed) {
                Files.writeString(f, PRETTY.toJson(out), StandardCharsets.UTF_8);
                changedFiles++;
            }
        }
        // 3. write the ladder into restrictions/minecraft/defense.json
        Path defense = dir.resolve("minecraft").resolve("defense.json");
        JsonArray def = new JsonArray();
        if (Files.exists(defense)) {
            JsonElement r = JsonParser.parseString(Files.readString(defense));
            if (r.isJsonArray()) def = r.getAsJsonArray();
        }
        for (Map.Entry<Integer, List<String>> e : byLevel.entrySet()) {
            JsonObject entry = new JsonObject();
            JsonArray actions = new JsonArray();
            actions.add("equip_item");
            entry.add("actions", actions);
            JsonArray ids = new JsonArray();
            e.getValue().forEach(ids::add);
            entry.add("itemIds", ids);
            JsonObject req = new JsonObject();
            req.addProperty("rpglevel:defense", e.getKey());
            entry.add("requirements", req);
            def.add(entry);
        }
        Files.createDirectories(defense.getParent());
        Files.writeString(defense, PRETTY.toJson(def), StandardCharsets.UTF_8);
        return "Armor ladder written: " + armorIds.size() + " armor pieces in " + byLevel.size() + " levels. Took " + removed
                + " old armor locks out of " + changedFiles + " files. Backup: " + backup.getFileName();
    }
}
