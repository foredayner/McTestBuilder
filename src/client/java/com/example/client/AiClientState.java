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
    /** What the AI 공방 makes: 0 building, 1 object, 2 equipment. */
    private static int mode = 0;
    private static com.example.content.GearKind gearKind = com.example.content.GearKind.SWORD;
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
        List<String> shown = new java.util.ArrayList<>();
        for (String line : lines) {
            shown.add(stripTag(line));
        }
        String text = String.join("\n", shown);
        Entry previous = ENTRIES.peekLast();
        // Indented lines continue the previous message (e.g. the plan shown for approval).
        if (lines.get(0).startsWith(" ") && previous != null && previous.speaker() != Speaker.ME) {
            ENTRIES.removeLast();
            ENTRIES.add(new Entry(previous.speaker(), previous.text() + "\n" + text));
            return;
        }
        if (first.startsWith("[Request]")) {
            speaker = Speaker.ME;
            text = String.join("\n", lines).trim().substring("[Request]".length()).trim();
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

    /** "[AI] Complete!" -> "Complete!" for the cards (the log file keeps the tags). */
    private static String stripTag(String line) {
        String t = line.stripLeading();
        String indent = line.substring(0, line.length() - t.length());
        for (String tag : new String[] { "[AI] ", "[Waiting...] ", "[History] ", "[Settings] ", "[Web] " }) {
            if (t.startsWith(tag)) {
                return indent + t.substring(tag.length());
            }
        }
        if (t.startsWith("[Review] ")) {
            return indent + "검토: " + t.substring("[Review] ".length());
        }
        return line;
    }

    /** The AI 공방 starts empty every time the window is opened (the 작업 일지 keeps everything). */
    public static void clearConversation() {
        synchronized (LOGS) {
            ENTRIES.clear();
        }
    }

    public static List<Entry> getEntries() {
        synchronized (LOGS) {
            return List.copyOf(ENTRIES);
        }
    }

    private static List<com.example.ai.build.ObjectService.Nearby> nearby = List.of();

    public static void setNearby(String json) {
        try {
            com.example.ai.build.ObjectService.Nearby[] list =
                new com.google.gson.Gson().fromJson(json, com.example.ai.build.ObjectService.Nearby[].class);
            nearby = list == null ? List.of() : List.of(list);
        } catch (RuntimeException exception) {
            nearby = List.of();
        }
        refreshOpenScreen(true);
    }

    public static List<com.example.ai.build.ObjectService.Nearby> getNearby() {
        return nearby;
    }

    public static boolean isObjectMode() {
        return mode == 1;
    }

    public static boolean isGearMode() {
        return mode == 2;
    }

    public static int getMode() {
        return mode;
    }

    /** 건물 -> 소품 -> 장비 -> 건물. */
    public static void cycleMode() {
        mode = (mode + 1) % 3;
        refreshOpenScreen(true);
    }

    public static void setMode(int newMode) {
        mode = Math.floorMod(newMode, 3);
        refreshOpenScreen(true);
    }

    public static com.example.content.GearKind getGearKind() {
        return gearKind;
    }

    /** Swords: the attack motion picked in the order ("" = normal swing). */
    private static String motion = "";

    public static String getMotion() {
        return motion;
    }

    public static void cycleMotion() {
        motion = com.example.content.GearMotions.next(motion);
        refreshOpenScreen(true);
    }

    public static void cycleGearKind() {
        com.example.content.GearKind[] all = com.example.content.GearKind.values();
        gearKind = all[(gearKind.ordinal() + 1) % all.length];
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
        if (!AiBuilderConfig.get().provider.supportsWebSearch()) {
            return;
        }
        sessionWebSearch = !sessionWebSearch;
        refreshOpenScreen(true);
    }

    public static boolean willUseWebSearch() {
        AiBuilderConfig config = AiBuilderConfig.get();
        return config.provider.supportsWebSearch()
            && (config.openAiWebSearchEnabled || sessionWebSearch);
    }

    public static boolean isWebSearchAvailable() {
        return AiBuilderConfig.get().provider.supportsWebSearch();
    }

    public static void sendPromptText(String prompt) {
        if (prompt == null) {
            return;
        }

        String trimmed = prompt.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (willUseWebSearch() && AiBuilderConfig.get().provider == AiProvider.OPENAI
            && !OpenAiWebSearchSupport.isModelSupported(AiBuilderConfig.get().modelName)) {
            addLog("[AI] Error: " + OpenAiWebSearchSupport.unsupportedModelMessage(AiBuilderConfig.get().modelName));
            return;
        }

        String flag = mode == 1 ? "--object " : mode == 2 ? "--gear=" + gearKind.id() + " " : "";
        if (mode == 2 && gearKind == com.example.content.GearKind.SWORD && !motion.isEmpty()) {
            flag += "--motion=" + motion + " ";
        }
        String effects = "";
        if (mode == 2) {
            String hit = clean(fxHit);
            String idle = clean(fxIdle);
            effects = (!gearKind.worn() && !hit.isEmpty() ? "||hit:" + hit : "") + (idle.isEmpty() ? "" : "||idle:" + idle);
        }
        ClientAiBuildNetworking.sendAiBuildRequest(trimmed.replace("||", "|") + effects, willUseWebSearch(), flag);
    }

    /** Effect fields of the equipment order (empty = no such effect). */
    private static String fxHit = "";
    private static String fxIdle = "";

    public static void setEffects(String hit, String idle) {
        fxHit = hit == null ? "" : hit;
        fxIdle = idle == null ? "" : idle;
    }

    public static String getFxHit() {
        return fxHit;
    }

    public static String getFxIdle() {
        return fxIdle;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("||", "|").replace("\n", " ").trim();
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
