package com.example.ai.build;

import java.util.regex.Pattern;

/**
 * Two things a weapon order can ask for that the mod does (not the script): damage numbers over
 * what it hits, and random damage (each hit rolls 0-9 for 0-100% of its damage). Found in the
 * order's words; {@link #rest} is the effect line without them, for the AI.
 */
public final class DamageTraits {

    private DamageTraits() {
    }

    static final Pattern NUMBERS = Pattern.compile(
        "(데미지|대미지|피해|딜)\\s*(량)?\\s*(숫자\\s*)?(표시|표기|띄우기|띄워|보이기|보여|출력)|damage\\s*(numbers?|popups?|text)|데미지\\s*숫자|피해\\s*숫자",
        Pattern.CASE_INSENSITIVE);
    static final Pattern RANDOM = Pattern.compile(
        "(랜덤|무작위|난수|임의)\\s*(데미지|대미지|피해|딜)|(데미지|대미지|피해)\\s*(랜덤|무작위|난수)|random\\s*damage|damage\\s*roll",
        Pattern.CASE_INSENSITIVE);

    public static boolean numbers(String text) {
        return text != null && NUMBERS.matcher(text).find();
    }

    public static boolean random(String text) {
        return text != null && RANDOM.matcher(text).find();
    }

    /** The text without the damage-number and random-damage requests (and the commas they leave). */
    public static String rest(String text) {
        if (text == null) {
            return null;
        }
        String r = RANDOM.matcher(text).replaceAll(" , ");
        r = NUMBERS.matcher(r).replaceAll(" , ");
        StringBuilder out = new StringBuilder();
        for (String part : r.split("\\s*[,，/+&]\\s*|\\s+(그리고|및|and)\\s+")) {
            String p = part.strip();
            // words left over from a request taken out ("랜덤", "표시", "와")
            if (p.isEmpty() || p.matches("(랜덤|무작위|난수|표시|표기|숫자|와|과|및|그리고|random|and|\\s)+")) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(p);
        }
        return out.toString();
    }

    /** What the roll 0-9 does to the damage: 0 = nothing, 9 = all of it. */
    public static float scale(int roll) {
        return Math.max(0, Math.min(9, roll)) / 9f;
    }
}
