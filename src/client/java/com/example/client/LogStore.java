package com.example.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Every log line, full text, appended to config/ai_builder/logs/yyyy-MM-dd.log (Logs tab). */
public final class LogStore {

    private static final Path DIR = Path.of("config", "ai_builder", "logs");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LINES = 3000;

    private LogStore() {
    }

    public static synchronized void append(String message) {
        try {
            Files.createDirectories(DIR);
            String text = "[" + LocalTime.now().format(TIME) + "] " + message.replace("\r", "") + "\n";
            Files.writeString(DIR.resolve(LocalDate.now().format(DAY) + ".log"), text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never break the mod.
        }
    }

    /** Days that have a log, newest first ("2026-10-23"). */
    public static List<String> days() {
        List<String> days = new ArrayList<>();
        if (!Files.isDirectory(DIR)) {
            return days;
        }
        try (Stream<Path> files = Files.list(DIR)) {
            files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".log"))
                .map(n -> n.substring(0, n.length() - 4))
                .forEach(days::add);
        } catch (IOException ignored) {
            return days;
        }
        days.sort(Collections.reverseOrder());
        return days;
    }

    public static List<String> read(String day) {
        try {
            List<String> lines = Files.readAllLines(DIR.resolve(day + ".log"), StandardCharsets.UTF_8);
            return lines.size() > MAX_LINES ? lines.subList(lines.size() - MAX_LINES, lines.size()) : lines;
        } catch (IOException exception) {
            return List.of();
        }
    }
}
