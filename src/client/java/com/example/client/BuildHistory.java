package com.example.client;

import com.example.ai.build.BuildRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finished builds, one JSON file each in config/ai_builder/history, with top/front preview PNGs
 * next to it (handy outside the game too). In a world, the server also sends everything anyone
 * made there (the shared 보관함); those are kept in memory and listed together with my own.
 */
public final class BuildHistory {

    private static final Path DIR = Path.of("config", "ai_builder", "history");
    private static List<BuildRecord> cache;
    /** Designs the server shared (everyone's), by id, while in a world. */
    private static final java.util.Map<String, BuildRecord> SHARED = new java.util.LinkedHashMap<>();

    private BuildHistory() {
    }

    public static synchronized void save(BuildRecord record) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(record.id + ".json"), record.toJson(), StandardCharsets.UTF_8);
            BuildPreview.of(record).writePngs(DIR.resolve(record.id + "_top.png"), DIR.resolve(record.id + "_front.png"));
        } catch (IOException exception) {
            AiClientState.addLog("[History] Could not write " + DIR + ": " + exception.getMessage());
        }
        cache = null;
    }

    public static synchronized List<BuildRecord> list() {
        if (cache != null) {
            return cache;
        }
        List<BuildRecord> records = new ArrayList<>();
        if (Files.isDirectory(DIR)) {
            try (Stream<Path> files = Files.list(DIR)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                    try {
                        records.add(BuildRecord.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException ignored) {
                        // Skip files that are not build records.
                    }
                }
            } catch (IOException ignored) {
                // Empty list.
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (BuildRecord r : records) {
            ids.add(r.id);
        }
        for (BuildRecord r : SHARED.values()) {
            if (ids.add(r.id)) {
                records.add(r);
            }
        }
        records.sort(Comparator.comparingLong((BuildRecord r) -> r.createdAt).reversed());
        cache = records;
        return cache;
    }

    /** The server sent a design (someone made it, or I joined). */
    public static synchronized void addShared(BuildRecord record) {
        SHARED.put(record.id, record);
        cache = null;
    }

    /** Leaving the world: forget the others' designs (mine stay on disk). */
    public static synchronized void clearShared() {
        SHARED.clear();
        cache = null;
    }

    /**
     * Delete for everyone: here, and (when the world has the mod) on the server, which tells
     * every player to drop it too.
     */
    public static void delete(BuildRecord record) {
        removeHere(record.id);
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
            com.example.network.AiNetworkPayloads.HistoryDeleteC2SPayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.example.network.AiNetworkPayloads.HistoryDeleteC2SPayload(record.id));
        }
    }

    /** Drop a design from this game (the file and the shared copy). */
    public static synchronized void removeHere(String id) {
        if (id == null || !id.matches("[0-9a-z_]{1,40}")) {
            return;
        }
        BuildRecord record = SHARED.remove(id);
        if (record == null) {
            for (BuildRecord r : list()) {
                if (r.id.equals(id)) {
                    record = r;
                }
            }
        }
        if (record != null) {
            deleteFiles(record);
        }
        cache = null;
    }

    private static synchronized void deleteFiles(BuildRecord record) {
        for (String suffix : new String[] { ".json", "_top.png", "_front.png" }) {
            try {
                Files.deleteIfExists(DIR.resolve(record.id + suffix));
            } catch (IOException ignored) {
                // Best effort.
            }
        }
        BuildPreview.forget(record);
        cache = null;
    }
}
