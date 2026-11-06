package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Single Fabric JAR upload. Never modifies live mods until validation succeeds. */
final class SingleModUpload {
    private static final long LIMIT = 50L * 1024 * 1024;
    private static final String NAME = "[a-zA-Z0-9_.-]{1,120}\\.jar";
    private SingleModUpload() {}

    static synchronized String install(InputStream data, String filename) throws IOException {
        if (PackInstaller.busy()) throw new IllegalArgumentException("A modpack install is running. Try again later.");
        if (filename == null || !filename.matches(NAME) || filename.contains(".."))
            throw new IllegalArgumentException("Choose a .jar mod file (letters, numbers, - and _).");
        Path game = FabricLoader.getInstance().getGameDir();
        Path backupRoot = game.resolve("everwyn-mod-backups");
        Files.createDirectories(backupRoot);
        Path tmp = Files.createTempFile(backupRoot, "incoming-", ".tmp");
        try {
            long total = 0;
            try (InputStream in = data; var out = Files.newOutputStream(tmp)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > LIMIT) throw new IllegalArgumentException("Mod is over 50 MB.");
                    out.write(buffer, 0, n);
                }
            }
            String id = modId(tmp);
            if ("everwyn-watch".equals(id))
                throw new IllegalArgumentException("Update Everwyn Watch using Bisect Files, not through its own upload page.");
            Path mods = game.resolve("mods");
            Files.createDirectories(mods);
            List<Path> old = new ArrayList<>();
            try (var files = Files.newDirectoryStream(mods, "*.jar")) {
                for (Path entry : files) {
                    if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry)) continue;
                    if (id.equals(tryModId(entry))) old.add(entry);
                }
            }
            // Never overwrite an unrelated jar. Choose a deterministic, safe filename for this mod id.
            String targetName = "ew-upload-" + id + ".jar";
            Path target = mods.resolve(targetName);
            if (Files.exists(target) && !old.contains(target))
                throw new IllegalArgumentException("The destination filename is already used by another mod.");
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path backup = backupRoot.resolve(stamp + "-" + id);
            Files.createDirectories(backup);
            List<Path> moved = new ArrayList<>();
            try {
                for (Path previous : old) {
                    Files.move(previous, backup.resolve(previous.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    moved.add(previous);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                for (Path previous : moved) {
                    try { Files.move(backup.resolve(previous.getFileName()), previous, StandardCopyOption.REPLACE_EXISTING); }
                    catch (IOException restoreError) { EverwynWatch.LOG.error("Failed to restore mod {}", previous, restoreError); }
                }
                throw e;
            }
            return "Uploaded " + id + ". Backed up " + old.size() + " previous JAR(s). Restart from Bisect to load it. Check compatibility and dependencies before restarting.";
        } finally { Files.deleteIfExists(tmp); }
    }

    private static String tryModId(Path file) {
        try { return modId(file); } catch (Exception ignored) { return null; }
    }

    private static String modId(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry meta = zip.getEntry("fabric.mod.json");
            if (meta == null || meta.getSize() > 262144) throw new IllegalArgumentException("This is not a Fabric mod JAR.");
            JsonObject obj;
            try (InputStream in = zip.getInputStream(meta)) {
                byte[] bytes = in.readNBytes(262145);
                if (bytes.length > 262144) throw new IllegalArgumentException("Mod metadata is too large.");
                obj = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            String id = obj.get("id").getAsString();
            if (!id.matches("[a-z][a-z0-9_-]{1,63}"))
                throw new IllegalArgumentException("Mod has an invalid ID.");
            if (obj.has("environment") && "client".equals(obj.get("environment").getAsString()))
                throw new IllegalArgumentException("This is a client-only mod. Install it on players' computers instead.");
            return id;
        } catch (java.util.zip.ZipException ex) {
            throw new IllegalArgumentException("This file isn't a valid JAR archive.");
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) throw ex;
            throw new IllegalArgumentException("Could not read fabric.mod.json.");
        }
    }
}
