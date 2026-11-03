package com.example.content;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Attack motions a held weapon or tool (not a bow) can be ordered with (공격 모션). Only the look of the swing changes
 * (drawn by the client, in first and third person); damage, reach and attack speed stay vanilla.
 */
public final class GearMotions {

    /** Stored on the item. */
    public static final String KEY = "aib_motion";
    /** "" = the normal swing. */
    public static final List<String> ALL = List.of("", "slash", "smash", "thrust", "spin");
    private static final List<String> KOREAN = List.of("기본", "가로베기", "내려찍기", "찌르기", "회전베기");

    private GearMotions() {
    }

    /** Which equipment takes an ordered attack motion: every held weapon and tool except bows. */
    public static boolean applies(GearKind kind) {
        return kind != null && !kind.worn() && kind != GearKind.BOW;
    }

    public static String normalize(String motion) {
        String m = motion == null ? "" : motion.trim().toLowerCase(java.util.Locale.ROOT);
        return ALL.contains(m) ? m : "";
    }

    public static String korean(String motion) {
        return KOREAN.get(ALL.indexOf(normalize(motion)));
    }

    public static String next(String motion) {
        return ALL.get((ALL.indexOf(normalize(motion)) + 1) % ALL.size());
    }

    /** The motion stored on a stack, or "". */
    /** Where in a motion the blade strikes (0-1): its effects start there. */
    public static final float STRIKE = 0.44F;

    /**
     * How long a motion takes (ticks), by the weapon's weight: short blades are quick, hammers and
     * long weapons (2 blocks or more) slow and heavy.
     */
    public static float ticks(GearKind kind, double length) {
        if (kind == GearKind.HAMMER || length >= 2.0) {
            return 16;
        }
        if (length > 0 && length < 1.0) {
            return 9;
        }
        return 12;
    }

    /** The ticks from the start of the swing to the strike. */
    public static int strikeTicks(GearKind kind, double length) {
        return Math.max(1, Math.round(ticks(kind, length) * STRIKE));
    }

    public static String of(ItemStack stack) {
        NbtComponent data = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return "";
        }
        return normalize(data.copyNbt().getString(KEY));
    }
}
