package com.example.ai.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ready-made effects, each a few layers that start one after another (a flash, then a ring, then
 * sparks): a solid base the AI can pick by name and adjust (colours, size, sound) instead of
 * building every effect from nothing.
 */
public final class EffectPresets {

    /** One preset: when it is for, what it looks like (for the AI) and its layers (ai.effect settings). */
    public record Preset(String when, String description, List<Map<String, Object>> layers) {
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    private EffectPresets() {
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }

    private static void add(String name, String when, String description, List<Map<String, Object>> layers) {
        PRESETS.put(name, new Preset(when, description, layers));
    }

    static {
        // ---- attacks (weapons when swung; bows where the arrow lands)
        add("fire_slash", "attack", "a burning blade band, falling sparks and real flames on the target", List.of(
            m("form", "swipe", "colors", List.of("#ffffff", "#ffc040", "#ff3000"), "size", 1.4, "arc", 170, "width", 0.45,
                "plane", "side", "start", "weapon", "ticks", 9, "sound", "entity.blaze.shoot", "volume", 0.6),
            m("shape", "slash", "look", "streak", "colors", List.of("#ffffa0", "#ff6000"), "size", 1.3, "count", 36, "plane", "side",
                "start", "weapon", "moves", List.of("scatter", "fade"), "speed", 4, "gravity", 5, "drag", 0.3, "vary", 0.6, "ticks", 16,
                "delay", 2),
            m("shape", "burst", "particle", "flame", "size", 0.5, "count", 18, "start", "target", "moves", List.of("scatter"),
                "speed", 1.5, "ticks", 10, "delay", 3)));
        add("lightning", "attack", "a bolt of light on the target with crackling sparks and a ring over the ground", List.of(
            m("form", "pillar", "colors", List.of("#ffffff", "#a0d0ff"), "size", 0.6, "width", 0.25, "start", "target", "ticks", 8,
                "sound", "entity.lightning_bolt.thunder", "volume", 0.5, "pitch", 1.4, "shake", 0.5),
            m("shape", "bolt", "look", "glow", "color", "#e0f0ff", "size", 1.2, "count", 40, "start", "target",
                "moves", List.of("flicker", "fade"), "ticks", 8),
            m("shape", "burst", "particle", "electric_spark", "size", 0.8, "count", 24, "start", "target", "moves", List.of("scatter"),
                "speed", 2, "ticks", 10, "delay", 1),
            m("form", "shockwave", "color", "#a0d0ff", "size", 1.8, "width", 0.2, "start", "ground", "ticks", 12, "delay", 2)));
        add("holy", "attack", "a column of golden light, a turning sacred circle and rising stars", List.of(
            m("form", "pillar", "colors", List.of("#ffffff", "#ffe080"), "size", 0.8, "width", 0.35, "start", "target", "ticks", 14,
                "sound", "block.beacon.activate", "volume", 0.7, "pitch", 1.5),
            m("form", "disc", "colors", List.of("#fff8d0", "#ffd040"), "size", 1.3, "plane", "flat", "start", "ground",
                "moves", List.of("spin", "fade"), "speed", 0.3, "ticks", 20),
            m("shape", "burst", "look", "star", "colors", List.of("#ffffff", "#ffe080"), "size", 0.9, "count", 20, "start", "target",
                "moves", List.of("rise", "fade"), "speed", 1, "vary", 0.5, "turbulence", 0.3, "ticks", 24, "delay", 3)));
        add("frost", "attack", "an icy blade band, ice shards bursting off the target and snowflakes", List.of(
            m("form", "swipe", "colors", List.of("#ffffff", "#a0e8ff", "#3080ff"), "size", 1.3, "arc", 160, "width", 0.4,
                "plane", "side", "start", "weapon", "ticks", 9, "sound", "block.glass.break", "volume", 0.5, "pitch", 1.6),
            m("shape", "burst", "look", "shard", "colors", List.of("#e0f8ff", "#60b0ff"), "size", 0.7, "count", 24, "start", "target",
                "moves", List.of("scatter", "fade"), "speed", 2.5, "gravity", 3, "drag", 0.4, "vary", 0.6, "ticks", 18, "delay", 2),
            m("shape", "burst", "particle", "snowflake", "size", 0.6, "count", 16, "start", "target", "moves", List.of("scatter"),
                "speed", 1, "ticks", 12, "delay", 2)));
        add("shadow", "attack", "a dark violet blade band, a swelling cloud of darkness and drifting souls", List.of(
            m("form", "swipe", "colors", List.of("#c080ff", "#401060", "#000000"), "size", 1.4, "arc", 180, "width", 0.5,
                "plane", "side", "start", "weapon", "ticks", 10, "sound", "entity.wither.shoot", "volume", 0.4, "pitch", 0.7),
            m("shape", "burst", "look", "glow", "colors", List.of("#6020a0", "#100020"), "size", 0.8, "count", 24, "start", "target",
                "moves", List.of("expand", "fade"), "vary", 0.5, "turbulence", 0.6, "ticks", 22, "delay", 2),
            m("shape", "circle", "particle", "sculk_soul", "size", 0.6, "count", 10, "start", "target", "ticks", 10, "delay", 3)));
        add("sakura", "attack", "a pink blade band, a gust of cherry petals and little rising hearts", List.of(
            m("form", "swipe", "colors", List.of("#ffffff", "#ffc0e0", "#ff70b0"), "size", 1.3, "arc", 160, "width", 0.35,
                "plane", "side", "start", "weapon", "ticks", 10, "sound", "block.cherry_leaves.break", "volume", 0.8),
            m("shape", "burst", "particle", "cherry_leaves", "size", 0.8, "count", 20, "start", "weapon", "moves", List.of("scatter"),
                "speed", 1.5, "ticks", 14, "delay", 2),
            m("shape", "heart", "fill", true, "look", "glow", "color", "#ff90c8", "size", 0.35, "count", 30, "start", "target",
                "moves", List.of("rise", "fade"), "speed", 0.8, "ticks", 18, "delay", 4)));
        add("poison", "attack", "a sickly green cloud that swells and drifts, toxic droplets and a green ring", List.of(
            m("shape", "burst", "look", "glow", "colors", List.of("#a0ff60", "#307020"), "size", 0.7, "count", 28, "start", "target",
                "moves", List.of("expand", "fade"), "vary", 0.6, "turbulence", 0.5, "gravity", -0.4, "ticks", 26,
                "sound", "entity.slime.squish", "volume", 0.7),
            m("shape", "sphere", "particle", "dust", "colors", List.of("#80ff40", "#204010"), "size", 0.6, "count", 20,
                "start", "target", "moves", List.of("scatter"), "speed", 0.8, "ticks", 14),
            m("form", "shockwave", "color", "#70e040", "size", 1.5, "width", 0.3, "start", "ground", "ticks", 14, "delay", 2)));
        add("arcane", "attack", "a magic circle opening on the ground, turning runes and power drawn in", List.of(
            m("form", "disc", "colors", List.of("#e0b0ff", "#7020ff"), "size", 1.4, "plane", "flat", "start", "ground",
                "moves", List.of("spin", "fade"), "speed", 0.4, "ticks", 24, "sound", "entity.evoker.cast_spell", "volume", 0.7),
            m("shape", "ring", "look", "rune", "colors", List.of("#d080ff", "#6020c0"), "size", 1.0, "count", 10, "dot", 0.25,
                "plane", "flat", "start", "ground", "moves", List.of("spin", "fade"), "speed", 0.2, "ticks", 24, "delay", 2),
            m("shape", "burst", "particle", "enchant", "size", 1.0, "count", 24, "start", "target", "moves", List.of("gather"),
                "ticks", 16, "delay", 4)));
        add("wind", "attack", "a wide pale band of wind, a spiral of speed lines and puffs of air", List.of(
            m("form", "swipe", "colors", List.of("#ffffff", "#d0fff0"), "size", 1.6, "arc", 200, "width", 0.3, "plane", "side",
                "start", "weapon", "ticks", 8, "sound", "entity.player.attack.sweep", "volume", 0.9),
            m("shape", "spiral", "look", "streak", "color", "#e0fff8", "size", 1.0, "count", 30, "plane", "side", "start", "weapon",
                "moves", List.of("spin", "forward", "fade"), "speed", 3, "drag", 0.4, "vary", 0.4, "ticks", 14, "delay", 1),
            m("shape", "burst", "particle", "cloud", "size", 0.7, "count", 10, "start", "target", "moves", List.of("scatter"),
                "speed", 1, "ticks", 10, "delay", 2)));
        add("ground_smash", "attack", "a shockwave over the ground, dust thrown up and rock chips falling back (hammers)", List.of(
            m("form", "shockwave", "color", "#d0b080", "size", 2.6, "width", 0.35, "start", "ground", "ticks", 16,
                "sound", "entity.generic.explode", "volume", 0.5, "shake", 0.8),
            m("shape", "ring", "particle", "poof", "plane", "flat", "size", 1.2, "count", 20, "start", "ground",
                "moves", List.of("expand"), "ticks", 12),
            m("shape", "burst", "look", "shard", "colors", List.of("#a08060", "#504030"), "size", 0.8, "count", 24, "start", "ground",
                "moves", List.of("scatter", "fade"), "speed", 3, "gravity", 7, "vary", 0.6, "ticks", 20, "delay", 1)));
        add("water", "attack", "a flowing blue band, a splash of falling drops and a ripple", List.of(
            m("form", "swipe", "colors", List.of("#ffffff", "#80d0ff", "#2060ff"), "size", 1.4, "arc", 170, "width", 0.45,
                "plane", "side", "start", "weapon", "ticks", 10, "sound", "entity.player.splash", "volume", 0.6),
            m("shape", "slash", "look", "dot", "colors", List.of("#c0f0ff", "#3080ff"), "size", 1.3, "count", 40, "plane", "side",
                "start", "weapon", "moves", List.of("scatter", "fade"), "speed", 2, "gravity", 6, "drag", 0.2, "vary", 0.6, "ticks", 16,
                "delay", 2),
            m("form", "shockwave", "color", "#80c0ff", "size", 1.6, "width", 0.25, "start", "target", "ticks", 12, "delay", 2)));

        // ---- while held or worn
        add("flame_aura", "idle", "small real flames on the item and embers rising off it", List.of(
            m("shape", "circle", "particle", "flame", "size", 0.25, "count", 6, "anchor", "item", "ticks", 20),
            m("shape", "burst", "look", "ember", "colors", List.of("#fff0a0", "#ff8020", "#602010"), "size", 0.3, "count", 6,
                "anchor", "item", "moves", List.of("rise", "fade"), "speed", 0.6, "turbulence", 0.5, "vary", 0.5, "ticks", 24,
                "every", 6)));
        add("frost_aura", "idle", "snowflakes falling round the item and ice chips circling it", List.of(
            m("shape", "circle", "particle", "snowflake", "size", 0.3, "count", 4, "anchor", "item", "ticks", 30),
            m("shape", "circle", "look", "shard", "color", "#c0f0ff", "size", 0.35, "count", 6, "plane", "flat", "anchor", "item",
                "moves", List.of("spin"), "speed", 0.3, "ticks", 40)));
        add("holy_halo", "idle", "a golden halo over the head with little stars round it", List.of(
            m("form", "disc", "colors", List.of("#fff8d0", "#ffd040"), "size", 0.35, "plane", "flat", "anchor", "head",
                "moves", List.of("spin"), "speed", 0.2, "ticks", 40),
            m("shape", "ring", "look", "star", "color", "#fff0a0", "size", 0.45, "count", 8, "plane", "flat", "anchor", "head",
                "moves", List.of("spin"), "speed", 0.3, "ticks", 40)));
        add("shadow_wisps", "idle", "dark wisps drifting up off the item", List.of(
            m("shape", "burst", "look", "glow", "colors", List.of("#6020a0", "#100020"), "size", 0.35, "count", 6, "anchor", "item",
                "moves", List.of("rise", "fade"), "speed", 0.4, "turbulence", 0.7, "vary", 0.6, "ticks", 30, "every", 8)));
        add("sakura_petals", "idle", "cherry petals drifting down around the wearer", List.of(
            m("shape", "circle", "particle", "cherry_leaves", "size", 0.6, "count", 4, "anchor", "around", "ticks", 40)));
        add("arcane_orbit", "idle", "a ribbon of light orbiting the wearer and runes turning at the feet", List.of(
            m("form", "ribbon", "colors", List.of("#ffffff", "#a060ff"), "size", 0.6, "width", 0.5, "anchor", "body",
                "moves", List.of("orbit"), "speed", 0.5, "ticks", 14),
            m("shape", "ring", "look", "rune", "color", "#c080ff", "size", 0.6, "count", 5, "dot", 0.2, "plane", "flat",
                "anchor", "feet", "moves", List.of("spin"), "speed", 0.2, "ticks", 40)));
        add("sparkle", "idle", "twinkling little stars on the item", List.of(
            m("shape", "burst", "look", "star", "color", "#fff8c0", "size", 0.35, "count", 6, "anchor", "item",
                "moves", List.of("pulse"), "vary", 0.6, "ticks", 30)));

        // ---- bows: behind the arrow
        add("comet", "trail", "a glowing ribbon behind the arrow", List.of(
            m("form", "ribbon", "colors", List.of("#ffffff", "#40c0ff"), "size", 0.4, "width", 0.5, "ticks", 12)));
        add("fire_trail", "trail", "real flames left along the arrow's path", List.of(
            m("shape", "burst", "particle", "flame", "size", 0.1, "count", 3, "ticks", 6, "every", 1)));
        add("star_trail", "trail", "twinkling stars left along the arrow's path", List.of(
            m("shape", "burst", "look", "star", "color", "#ffd84a", "size", 0.15, "count", 6, "moves", List.of("shrink", "fade"),
                "ticks", 10, "every", 1)));
    }

    public static Map<String, Preset> all() {
        return PRESETS;
    }

    /** The prompt's list: "name (when): what it looks like". */
    public static String catalogue() {
        StringBuilder sb = new StringBuilder();
        for (var e : PRESETS.entrySet()) {
            sb.append("                  ").append(e.getKey()).append(" (").append(e.getValue().when()).append("): ")
                .append(e.getValue().description()).append('\n');
        }
        return sb.toString();
    }

    /**
     * The layers of a preset with the script's changes: colors (replace the colours of every layer),
     * scale (multiply every size), sound (replace the sound, '' for none), shake.
     */
    public static List<Spark> build(String name, Map<?, ?> changes) {
        Preset preset = PRESETS.get(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        if (preset == null) {
            throw new IllegalArgumentException("no effect preset '" + name + "'; use one of " + PRESETS.keySet());
        }
        List<?> colors = null;
        double scale = 1;
        String sound = null;
        Double shake = null;
        if (changes != null) {
            for (var e : changes.entrySet()) {
                String key = String.valueOf(e.getKey()).trim();
                Object v = e.getValue();
                switch (key) {
                    case "colors", "colours" -> {
                        if (!(v instanceof List<?> list) || list.isEmpty() || list.size() > 4) {
                            throw new IllegalArgumentException("preset colors is a list of 1-4 colours like ['#80ff80', '#206020']");
                        }
                        colors = list;
                    }
                    case "color", "colour" -> colors = List.of(v);
                    case "scale", "size" -> scale = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
                    case "sound" -> sound = String.valueOf(v);
                    case "shake" -> shake = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
                    default -> throw new IllegalArgumentException("unknown preset change '" + key + "'; use colors, scale, sound, shake");
                }
            }
        }
        scale = Math.max(0.3, Math.min(3, scale));
        List<Spark> out = new ArrayList<>();
        boolean soundSet = false;
        for (Map<String, Object> layer : preset.layers()) {
            Map<String, Object> l = new LinkedHashMap<>(layer);
            if (colors != null) {
                if (l.containsKey("colors") && colors.size() >= 2) {
                    l.put("colors", colors);
                } else {
                    l.remove("colors");
                    l.put("color", colors.get(0));
                }
            }
            if (l.get("size") instanceof Number n) {
                l.put("size", n.doubleValue() * scale);
            }
            if (sound != null && l.containsKey("sound")) {
                if (sound.isBlank()) {
                    l.remove("sound");
                    l.remove("volume");
                    l.remove("pitch");
                } else {
                    l.put("sound", sound);
                }
                soundSet = true;
            }
            if (shake != null && out.isEmpty()) {
                l.put("shake", shake);
            }
            out.add(Spark.fromMap(l));
        }
        if (sound != null && !sound.isBlank() && !soundSet && !out.isEmpty()) {
            Map<String, Object> first = new LinkedHashMap<>(preset.layers().get(0));
            first.put("sound", sound);
            out.set(0, Spark.fromMap(first));
        }
        return out;
    }

    /** What a preset is for: attack, idle or trail. */
    public static String whenOf(String name) {
        Preset p = PRESETS.get(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        return p == null ? null : p.when();
    }
}
