package com.example.client;

import com.example.client.network.AiClientNetworking;
import com.example.client.network.ClientAiBuildNetworking;
import com.example.ai.OpenAiWebSearchSupport;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;

import java.util.LinkedList;
import java.util.List;

public final class AiClientState {

    /** Who a chat card is from. */
    public enum Speaker { ME, AI, SYSTEM }

    /** One message card in the chat view. */
    public record Entry(Speaker speaker, String text) {
    }

    private static final LinkedList<String> LOGS = new LinkedList<>();
    private static final LinkedList<Entry> ENTRIES = new LinkedList<>();
    private static final int MAX_ENTRIES = 120;
    private static boolean objectMode = false;
    private static final int MAX_LOGS = 300;
    private static final int MAX_LINES_PER_MESSAGE = 8;

    private static boolean pendingApproval = false;
    private static String pendingDescription = "";
    private static boolean sessionWebSearch = false;
    private static AiPromptScreen openScreen;

    private AiClientState() {
    }

    public static void setOpenScreen(AiPromptScreen screen) {
        openScreen = screen;
    }

    public static void clearOpenScreen(AiPromptScreen screen) {
        if (openScreen == screen) {
            openScreen = null;
        }
    }

    /** Full text goes to the log file (Logs tab); the panel gets a cleaned, short version. */
    public static void addLog(String message) {
        if (message == null) {
            return;
        }
        LogStore.append(message);
        List<String> clean = cleanForDisplay(message);
        synchronized (LOGS) {
            LOGS.addAll(clean);
            while (LOGS.size() > MAX_LOGS) {
                LOGS.removeFirst();
            }
            addEntry(clean);
        }
        refreshOpenScreen(false);
    }

    /** Group lines into chat cards: my requests, AI work, and everything else from the system. */
    private static void addEntry(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        String first = lines.get(0).trim();
        Speaker speaker;
        String text = String.join("\n", lines);
        if (first.startsWith("[Request]")) {
            speaker = Speaker.ME;
            text = text.trim().substring("[Request]".length()).trim();
        } else if (first.startsWith("[AI]") || first.startsWith("[Review]") || first.startsWith("[Waiting")
            || first.startsWith("[Web]") || first.startsWith("[Security")) {
            speaker = Speaker.AI;
        } else {
            speaker = Speaker.SYSTEM;
        }
        Entry last = ENTRIES.peekLast();
        if (speaker != Speaker.ME && last != null && last.speaker() == speaker) {
            ENTRIES.removeLast();
            text = last.text() + "\n" + text;
        }
        ENTRIES.add(new Entry(speaker, text));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    public static List<Entry> getEntries() {
        synchronized (LOGS) {
            return List.copyOf(ENTRIES);
        }
    }

    public static boolean isObjectMode() {
        return objectMode;
    }

    public static void toggleObjectMode() {
        objectMode = !objectMode;
        refreshOpenScreen(true);
    }

    public static void undo() {
        AiClientNetworking.sendUndo();
    }

    /**
     * Drop carriage returns and tabs (they showed up as CR/HT boxes), stack trace lines and
     * blank lines, and keep at most a few lines of any one message.
     */
    static List<String> cleanForDisplay(String message) {
        List<String> out = new java.util.ArrayList<>();
        int dropped = 0;
        for (String raw : message.replace("\r", "").replace("\t", "  ").split("\n")) {
            String line = raw.stripTrailing();
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("at ") || t.startsWith("... ") || t.contains("java.base/") || t.startsWith("knot//")
                || t.startsWith("Caused by: java.") || t.equals("startup failed:")) {
                dropped++;
                continue;
            }
            if (out.size() >= MAX_LINES_PER_MESSAGE) {
                dropped++;
                continue;
            }
            out.add(out.isEmpty() ? line : "  " + t);
        }
        if (dropped > 0) {
            out.add("  (full text in the Logs tab)");
        }
        return out;
    }

    public static List<String> getLogs() {
        synchronized (LOGS) {
            return List.copyOf(LOGS);
        }
    }

    public static void setPendingApproval(String description) {
        pendingApproval = true;
        pendingDescription = description == null ? "" : description;
        refreshOpenScreen(true);
    }

    public static void clearPendingApproval() {
        pendingApproval = false;
        pendingDescription = "";
        refreshOpenScreen(true);
    }

    public static boolean hasPendingApproval() {
        return pendingApproval;
    }

    public static String getPendingDescription() {
        return pendingDescription;
    }

    public static boolean isSessionWebSearchEnabled() {
        return sessionWebSearch;
    }

    public static void toggleSessionWebSearch() {
        if (AiBuilderConfig.get().provider != AiProvider.OPENAI) {
            return;
        }
        sessionWebSearch = !sessionWebSearch;
        refreshOpenScreen(true);
    }

    public static boolean willUseWebSearch() {
        AiBuilderConfig config = AiBuilderConfig.get();
        return config.provider == AiProvider.OPENAI
            && (config.openAiWebSearchEnabled || sessionWebSearch);
    }

    public static boolean isWebSearchAvailable() {
        return AiBuilderConfig.get().provider == AiProvider.OPENAI;
    }

    public static void sendPromptText(String prompt) {
        if (prompt == null) {
            return;
        }

        String trimmed = prompt.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (willUseWebSearch() && !OpenAiWebSearchSupport.isModelSupported(AiBuilderConfig.get().modelName)) {
            addLog("[AI] Error: " + OpenAiWebSearchSupport.unsupportedModelMessage(AiBuilderConfig.get().modelName));
            return;
        }

        ClientAiBuildNetworking.sendAiBuildRequest(trimmed, willUseWebSearch(), objectMode);
    }

    public static void approve() {
        if (!pendingApproval) {
            return;
        }
        AiClientNetworking.sendApprovalAction(true);
    }

    public static void cancel() {
        if (!pendingApproval) {
            return;
        }
        AiClientNetworking.sendApprovalAction(false);
    }

    private static void refreshOpenScreen(boolean rebuildWidgets) {
        AiPromptScreen screen = openScreen;
        if (screen != null) {
            screen.onStateUpdated(rebuildWidgets);
        }
    }
}
