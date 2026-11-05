package dev.mrwavy.everwynwatch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;

/**
 * Logins. The token in the config is the owner (you). Helpers get their own
 * tokens with limited powers, and every action is written to an audit log.
 */
final class Accounts {
    enum Role { OWNER, ADMIN, HELPER, VIEWER }

    record Account(String name, Role role) {}

    /** What a helper may do. Admins and the owner may do everything (except managing logins: owner only). */
    static final Set<String> HELPER_ACTIONS = Set.of(
            "message", "broadcast", "heal", "feed", "clear_effects", "tp_to_player", "tp_coords",
            "to_spawn", "kick", "waypoint_add", "waypoint_share");
    static final Set<String> OWNER_ONLY = Set.of("account_add", "account_remove");

    private final WatchConfig cfg;
    private final Path auditFile;
    private final Deque<JsonObject> audit = new ArrayDeque<>();

    Accounts(WatchConfig cfg, Path auditFile) {
        this.cfg = cfg;
        this.auditFile = auditFile;
        loadAudit();
    }

    /** Who this token belongs to, or null. */
    synchronized Account auth(String token) {
        if (token == null || token.isEmpty()) return null;
        byte[] given = token.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(given, cfg.token.getBytes(StandardCharsets.UTF_8))) return new Account("Owner", Role.OWNER);
        for (WatchConfig.AccountEntry a : cfg.accounts) {
            if (a.token != null && MessageDigest.isEqual(given, a.token.getBytes(StandardCharsets.UTF_8))) {
                return new Account(a.name, role(a.role));
            }
        }
        return null;
    }

    static boolean allowed(Role role, String action) {
        if (OWNER_ONLY.contains(action)) return role == Role.OWNER;
        return switch (role) {
            case OWNER, ADMIN -> true;
            case HELPER -> HELPER_ACTIONS.contains(action);
            case VIEWER -> false;
        };
    }

    static JsonObject describe(Account a) {
        JsonObject me = new JsonObject();
        me.addProperty("name", a.name());
        me.addProperty("role", a.role().name().toLowerCase(Locale.ROOT));
        JsonArray can = new JsonArray();
        if (a.role() == Role.OWNER || a.role() == Role.ADMIN) can.add("*");
        else if (a.role() == Role.HELPER) HELPER_ACTIONS.forEach(can::add);
        me.add("can", can);
        return me;
    }

    /** Creates a login and returns its token (shown once). */
    synchronized String add(String name, String roleName) {
        name = name == null ? "" : name.replaceAll("[^A-Za-z0-9_ .-]", "").trim();
        if (name.isEmpty() || name.length() > 24) throw new IllegalArgumentException("Give the login a short name (letters and numbers)");
        if (name.equalsIgnoreCase("owner")) throw new IllegalArgumentException("Pick a different name");
        for (WatchConfig.AccountEntry a : cfg.accounts) {
            if (a.name.equalsIgnoreCase(name)) throw new IllegalArgumentException("There's already a login called " + name);
        }
        Role role = role(roleName);
        if (role == Role.OWNER) role = Role.ADMIN;
        if (cfg.accounts.size() >= 30) throw new IllegalArgumentException("Too many logins");
        WatchConfig.AccountEntry e = new WatchConfig.AccountEntry();
        e.name = name;
        e.role = role.name().toLowerCase(Locale.ROOT);
        e.token = randomToken();
        e.created = System.currentTimeMillis();
        cfg.accounts.add(e);
        cfg.save();
        return e.token;
    }

    synchronized String remove(String name) {
        boolean removed = cfg.accounts.removeIf(a -> a.name.equalsIgnoreCase(name));
        if (!removed) throw new IllegalArgumentException("No login called " + name);
        cfg.save();
        return "Removed login " + name;
    }

    synchronized JsonArray list() {
        JsonArray arr = new JsonArray();
        for (WatchConfig.AccountEntry a : cfg.accounts) {
            JsonObject o = new JsonObject();
            o.addProperty("name", a.name);
            o.addProperty("role", a.role);
            o.addProperty("created", a.created);
            arr.add(o);
        }
        return arr;
    }

    synchronized void record(Account who, String action, String result) {
        JsonObject e = new JsonObject();
        e.addProperty("time", System.currentTimeMillis());
        e.addProperty("who", who.name());
        e.addProperty("role", who.role().name().toLowerCase(Locale.ROOT));
        e.addProperty("action", action);
        e.addProperty("result", result);
        audit.addLast(e);
        while (audit.size() > 1000) audit.pollFirst();
        saveAudit();
    }

    synchronized JsonArray auditLog(int max) {
        JsonArray arr = new JsonArray();
        int skip = Math.max(0, audit.size() - max);
        int i = 0;
        for (JsonObject e : audit) {
            if (i++ >= skip) arr.add(e);
        }
        return arr;
    }

    private static Role role(String s) {
        if (s == null) return Role.VIEWER;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "admin" -> Role.ADMIN;
            case "helper" -> Role.HELPER;
            case "owner" -> Role.OWNER;
            default -> Role.VIEWER;
        };
    }

    private static String randomToken() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private void loadAudit() {
        if (!Files.exists(auditFile)) return;
        try (Reader r = Files.newBufferedReader(auditFile, StandardCharsets.UTF_8)) {
            for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) audit.addLast(el.getAsJsonObject());
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not read {}", auditFile, e);
        }
    }

    private void saveAudit() {
        JsonArray arr = new JsonArray();
        audit.forEach(arr::add);
        try {
            Files.createDirectories(auditFile.getParent());
            try (Writer w = Files.newBufferedWriter(auditFile, StandardCharsets.UTF_8)) {
                new Gson().toJson(arr, w);
            }
        } catch (Exception e) {
            EverwynWatch.LOG.warn("Could not save {}", auditFile, e);
        }
    }
}
