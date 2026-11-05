package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.MapColor;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every item and wave-able mob in the pack, for the app's pickers.
 * Names come from the game's English text plus each mod's own lang file
 * (a dedicated server normally only knows vanilla names). Icons are served
 * from mod jars when they include a texture; vanilla textures aren't on servers.
 */
final class Catalog {
    private static volatile String json;

    private Catalog() {}

    /** Builds once (on the server thread) and reuses the result. */
    static synchronized String json() {
        if (json == null) json = build();
        return json;
    }

    private static String build() {
        Map<String, String> modLang = new HashMap<>();
        Map<String, Path> textures = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            try {
                for (Path root : mod.getRootPaths()) scanAssets(root.resolve("assets"), modLang, textures);
            } catch (Throwable ignored) {
                // built-in pseudo mods have no files
            }
        }

        Language lang = Language.getInstance();
        JsonArray items = new JsonArray();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("name", name(item.getTranslationKey(), id, lang, modLang));
            o.addProperty("max", item.getMaxCount());
            if (item instanceof BlockItem bi) {
                try {
                    MapColor mc = bi.getBlock().getDefaultMapColor();
                    if (mc != null && mc.color != 0) o.addProperty("color", mc.color);
                } catch (Throwable ignored) {
                    // some modded blocks need a world to pick a color
                }
            }
            items.add(o);
        }

        JsonArray mobs = new JsonArray();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (!Challenges.allowedMob(type)) continue;
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("name", name(type.getTranslationKey(), id, lang, modLang));
            mobs.add(o);
        }

        JsonObject root = new JsonObject();
        root.add("items", items);
        root.add("mobs", mobs);
        EverwynWatch.LOG.info("Everwyn Watch catalog: {} items, {} mobs", items.size(), mobs.size());
        return root.toString();
    }

    private static String name(String key, Identifier id, Language lang, Map<String, String> modLang) {
        if (modLang.containsKey(key)) return modLang.get(key);
        if (lang.hasTranslation(key)) return lang.get(key);
        // fall back to the id: "create:brass_ingot" -> "Brass Ingot"
        StringBuilder sb = new StringBuilder();
        for (String part : id.getPath().split("[_/]")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static void scanAssets(Path assets, Map<String, String> modLang, Map<String, Path> textures) {
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> namespaces = Files.list(assets)) {
            namespaces.filter(Files::isDirectory).forEach(nsDir -> {
                String ns = nsDir.getFileName().toString().replace("/", "");
                if (ns.equals("minecraft")) return; // vanilla text is already loaded
                Path langFile = nsDir.resolve("lang").resolve("en_us.json");
                if (Files.isRegularFile(langFile)) {
                    try (Reader r = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
                        JsonElement el = JsonParser.parseReader(r);
                        if (el.isJsonObject()) {
                            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                                if (e.getValue().isJsonPrimitive()) modLang.putIfAbsent(e.getKey(), e.getValue().getAsString());
                            }
                        }
                    } catch (Exception ignored) {
                        // broken lang file; names fall back to ids
                    }
                }
            });
        } catch (Exception ignored) {
            // unreadable mod
        }
    }

}
