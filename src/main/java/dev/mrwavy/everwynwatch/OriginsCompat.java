package dev.mrwavy.everwynwatch;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Reads a player's origin (Human, Half-Elf, Elf, Goblin...) if the Origins mod is installed.
 * Uses reflection so this mod still works fine without Origins.
 */
final class OriginsCompat {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("origins");
    private static boolean broken = false;
    private static Object componentKey;
    private static Method keyGet;

    private OriginsCompat() {}

    static String origin(ServerPlayerEntity player) {
        if (!PRESENT || broken) return null;
        try {
            if (componentKey == null) {
                Class<?> mc = Class.forName("io.github.apace100.origins.registry.ModComponents");
                componentKey = mc.getField("ORIGIN").get(null);
                keyGet = componentKey.getClass().getMethod("get", Object.class);
            }
            Object component = keyGet.invoke(componentKey, player);
            Object origins = component.getClass().getMethod("getOrigins").invoke(component);
            if (!(origins instanceof Map<?, ?> map)) return null;
            StringJoiner names = new StringJoiner(", ");
            for (Object origin : map.values()) {
                if (origin == null) continue;
                String id = String.valueOf(origin.getClass().getMethod("getIdentifier").invoke(origin));
                if (id.endsWith(":empty")) continue;
                String label = id;
                try {
                    Object n = origin.getClass().getMethod("getName").invoke(origin);
                    if (n instanceof Text t) label = t.getString();
                } catch (Exception ignored) {
                    // fall back to the id
                }
                names.add(label);
            }
            String result = names.toString();
            return result.isEmpty() ? null : result;
        } catch (Throwable t) {
            broken = true;
            EverwynWatch.LOG.warn("Everwyn Watch could not read Origins data; origins won't show in the app", t);
            return null;
        }
    }
}
