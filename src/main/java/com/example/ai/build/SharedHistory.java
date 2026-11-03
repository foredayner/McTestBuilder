package com.example.ai.build;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Everything anyone made on this server, kept with the world (world/ai_builder/history), so the
 * 보관함 shows every player's designs. Players get the list when they join and each new design as
 * it is finished; anyone who may use the AI 공방 can delete one for everybody.
 */
public final class SharedHistory {

    /** Players get at most this many (the newest) when they join. */
    public static final int SYNC_LIMIT = 150;
    private static final Pattern ID = Pattern.compile("^[0-9a-z_]{1,40}$");

    private SharedHistory() {
    }

    private static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("ai_builder").resolve("history");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static synchronized void save(MinecraftServer server, BuildRecord record) {
        if (server == null || !validId(record.id)) {
            return;
        }
        try {
            Files.createDirectories(dir(server));
            Files.writeString(dir(server).resolve(record.id + ".json"), record.toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.out.println("[AI] Could not keep the design in the world: " + exception.getMessage());
        }
    }

    /** Newest first, at most {@code limit}. */
    public static synchronized List<BuildRecord> list(MinecraftServer server, int limit) {
        List<BuildRecord> out = new ArrayList<>();
        Path dir = server == null ? null : dir(server);
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> all = Files.list(dir)) {
            all.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        } catch (IOException exception) {
            return out;
        }
        // Ids are the creation time: newest first without opening every file.
        files.sort(Comparator.comparing((Path p) -> sortKey(p.getFileName().toString())).reversed());
        for (Path file : files) {
            if (out.size() >= limit) {
                break;
            }
            try {
                out.add(BuildRecord.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException ignored) {
                // Not a build record.
            }
        }
        return out;
    }

    private static String sortKey(String name) {
        String id = name.substring(0, name.length() - 5);
        return "0".repeat(Math.max(0, 20 - id.length())) + id;
    }

    /** One kept design, or null. */
    public static synchronized BuildRecord get(MinecraftServer server, String id) {
        if (server == null || !validId(id)) {
            return null;
        }
        Path file = dir(server).resolve(id + ".json");
        try {
            return Files.isRegularFile(file) ? BuildRecord.fromJson(Files.readString(file, StandardCharsets.UTF_8)) : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** True when it was there. */
    public static synchronized boolean delete(MinecraftServer server, String id) {
        if (server == null || !validId(id)) {
            return false;
        }
        try {
            return Files.deleteIfExists(dir(server).resolve(id + ".json"));
        } catch (IOException exception) {
            return false;
        }
    }
}
