package dev.mrwavy.everwynwatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayDeque;

/** Rolling feed of chat, joins, leaves, deaths, advancements and admin actions. */
public final class EventLog {
    private final ArrayDeque<JsonObject> buffer = new ArrayDeque<>();
    private final int max;
    private long nextId = 1;

    public EventLog(int max) {
        this.max = Math.max(50, max);
    }

    public synchronized void add(String type, String who, String text) {
        JsonObject e = new JsonObject();
        e.addProperty("id", nextId++);
        e.addProperty("time", System.currentTimeMillis());
        e.addProperty("type", type);
        if (who != null) e.addProperty("who", who);
        e.addProperty("text", text);
        buffer.addLast(e);
        while (buffer.size() > max) buffer.pollFirst();
    }

    /** Sorts vanilla broadcast messages into feed categories using their translation key. */
    public void addGameMessage(Text message) {
        String type = "server";
        String who = null;
        if (message.getContent() instanceof TranslatableTextContent t) {
            String key = t.getKey();
            if (key.startsWith("death.")) type = "death";
            else if (key.startsWith("chat.type.advancement")) type = "advancement";
            else if (key.startsWith("multiplayer.player.joined")) type = "join";
            else if (key.equals("multiplayer.player.left")) type = "leave";
            Object[] args = t.getArgs();
            if (args.length > 0) {
                who = args[0] instanceof Text tx ? tx.getString() : String.valueOf(args[0]);
            }
        }
        add(type, who, message.getString());
    }

    /** Everything newer than {@code after}; pass 0 to get the whole buffer. */
    public synchronized JsonObject since(long after) {
        JsonArray arr = new JsonArray();
        for (JsonObject e : buffer) {
            if (e.get("id").getAsLong() > after) arr.add(e);
        }
        JsonObject out = new JsonObject();
        out.addProperty("last", nextId - 1);
        out.add("events", arr);
        return out;
    }
}
