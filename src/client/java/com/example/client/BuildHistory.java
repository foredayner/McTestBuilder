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
 * next to it (handy outside the game too).
 */
public final class BuildHistory {

    private static final Path DIR = Path.of("config", "ai_builder", "history");
    private static List<BuildRecord> cache;

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
        records.sort(Comparator.comparingLong((BuildRecord r) -> r.createdAt).reversed());
        cache = records;
        return cache;
    }

    public static synchronized void delete(BuildRecord record) {
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
