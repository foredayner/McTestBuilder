package com.example.content;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Attack motions a sword can be ordered with (공격 모션). Only the look of the swing changes
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
    public static String of(ItemStack stack) {
        NbtComponent data = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return "";
        }
        return normalize(data.copyNbt().getString(KEY));
    }
}
