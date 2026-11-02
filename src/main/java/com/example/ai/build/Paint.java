package com.example.ai.build;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A painted piece: instead of a block's texture, an object or equipment piece can be one smooth
 * colour, written where the block name goes: {@code '#c0392b'}, or see-through with an opacity in
 * percent: {@code '#88ccff/50'}. Opacity is kept in four steps (25, 50, 75, 100) so objects
 * (drawn by the game's display entities) and equipment look the same.
 */
public final class Paint {

    private static final Pattern TEXT = Pattern.compile("^#([0-9a-f]{6}|[0-9a-f]{3})(?:\\s*/\\s*(\\d{1,3})\\s*%?)?$");
    /** The stored form: "#rrggbb" or "#rrggbb/25|50|75". */
    private static final Pattern STORED = Pattern.compile("^#[0-9a-f]{6}(/(25|50|75))?$");

    public static final String HELP = "a colour is written '#rrggbb' (e.g. '#c0392b'), or see-through '#rrggbb/50' "
        + "(opacity 25, 50 or 75 percent)";

    private Paint() {
    }

    /** Written like a colour (starts with '#'). */
    public static boolean is(String block) {
        return block != null && block.trim().startsWith("#");
    }

    /** The stored form of a colour the AI wrote, or null when it is not a valid colour. */
    public static String parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = TEXT.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return null;
        }
        String hex = m.group(1);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        int opacity = m.group(2) == null ? 100 : step(Integer.parseInt(m.group(2)));
        return "#" + hex + (opacity >= 100 ? "" : "/" + opacity);
    }

    /** Nearest of 25, 50, 75, 100. */
    static int step(int percent) {
        int p = Math.max(1, Math.min(100, percent));
        if (p < 38) {
            return 25;
        }
        if (p < 63) {
            return 50;
        }
        return p < 88 ? 75 : 100;
    }

    /** A stored colour (data from a saved design or the network). */
    public static boolean valid(String block) {
        return block != null && STORED.matcher(block).matches();
    }

    /** 0xRRGGBB of a stored colour. */
    public static int rgb(String block) {
        try {
            return Integer.parseInt(block.substring(1, 7), 16);
        } catch (RuntimeException exception) {
            return 0x808080;
        }
    }

    /** 25, 50, 75 or 100. */
    public static int opacity(String block) {
        int slash = block == null ? -1 : block.indexOf('/');
        if (slash < 0) {
            return 100;
        }
        try {
            return step(Integer.parseInt(block.substring(slash + 1)));
        } catch (RuntimeException exception) {
            return 100;
        }
    }

    /** 0xAARRGGBB. */
    public static int argb(String block) {
        int alpha = Math.round(opacity(block) * 255 / 100.0F);
        return alpha << 24 | rgb(block);
    }

    /** The item an object's painted piece shows: one per opacity step (its texture carries the opacity). */
    public static String itemId(String block) {
        int o = opacity(block);
        return "minecraft-ai-build-assistant:paint" + (o >= 100 ? "" : "_" + o);
    }
}
