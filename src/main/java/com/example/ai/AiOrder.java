package com.example.ai;

import com.google.gson.Gson;

/**
 * One order to the AI 공방, as the order screen sends it (a network packet, not a chat command, so
 * nothing is cut at 256 characters): the description, the effect fields, the mode and options,
 * and for "고쳐서 주문" the earlier design's script and description. Everything from a client is
 * cut to safe lengths here before it is used.
 */
public final class AiOrder {

    public static final int MAX_PROMPT = 300;
    public static final int MAX_EFFECT = 60;
    public static final int MAX_SCRIPT = 200_000;

    private static final Gson GSON = new Gson();

    public String prompt = "";
    /** "build", "object", "gear:<kind>" or "gear:<kind>:pixel". */
    public String mode = "build";
    public boolean web;
    public String fxHit = "";
    public String fxIdle = "";
    public String motion = "";
    /** 고쳐서 주문: the record being changed (its id, script and first description). */
    public String baseId = "";
    public String baseScript = "";
    public String basePrompt = "";

    public boolean isRevision() {
        return !baseScript.isBlank();
    }

    public AiOrder cleaned() {
        prompt = cut(prompt, MAX_PROMPT);
        fxHit = cut(fxHit, MAX_EFFECT);
        fxIdle = cut(fxIdle, MAX_EFFECT);
        motion = com.example.content.GearMotions.normalize(cut(motion, 20));
        baseId = cut(baseId, 40);
        baseScript = baseScript == null ? "" : baseScript.length() > MAX_SCRIPT ? "" : baseScript;
        basePrompt = cut(basePrompt, MAX_PROMPT * 3);
        String m = mode == null ? "" : mode.trim();
        if (m.startsWith("gear:")) {
            String[] parts = m.split(":");
            String kind = com.example.content.GearKind.of(parts.length > 1 ? parts[1] : "").id();
            mode = "gear:" + kind + (m.endsWith(":pixel") ? ":pixel" : "");
        } else {
            mode = "object".equals(m) ? "object" : "build";
        }
        return this;
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public byte[] toGzip() {
        return WorkJournal.gzip(toJson());
    }

    /** From a packet (bounded, so a bad one cannot use up memory); null if it is not an order. */
    public static AiOrder fromGzip(byte[] data) {
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            return fromJson(new String(in.readNBytes(MAX_SCRIPT * 4), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException exception) {
            return null;
        }
    }

    public static AiOrder fromJson(String json) {
        try {
            AiOrder order = GSON.fromJson(json, AiOrder.class);
            return order == null ? null : order.cleaned();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
