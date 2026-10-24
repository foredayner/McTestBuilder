package com.example.ai.build;

import com.example.content.AibContent;
import com.example.content.GearKind;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Equipment as items: the stack carries the whole model (gzipped parts), so it looks right on any
 * client, in any world, even after it is deleted from 보관함.
 */
public final class GearItems {

    public static final String KEY = "aib_gear";
    public static final String MODEL = "aib_model";
    /** Largest model a gear item may carry (gzipped bytes). */
    public static final int MAX_MODEL_BYTES = 60_000;

    private GearItems() {
    }

    public static GearKind kindOf(BuildRecord record) {
        return GearKind.of(record.gear);
    }

    /** Only the parts, gzipped: what the item's renderer needs. */
    public static byte[] modelBytes(BuildPlan plan) {
        BuildRecord model = new BuildRecord();
        model.kind = "gear";
        model.plan = new BuildPlan();
        model.plan.facing = plan.facing;
        model.plan.parts = new java.util.ArrayList<>(plan.parts);
        model.plan.anims = plan.anims == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(plan.anims);
        model.plan.aura = plan.aura;
        return model.toGzip();
    }

    public static ItemStack stackFor(BuildRecord record) {
        GearKind kind = kindOf(record);
        ItemStack stack = new ItemStack(AibContent.GEAR.get(kind));
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, record.id);
        byte[] model = modelBytes(record.plan);
        if (model.length <= MAX_MODEL_BYTES) {
            nbt.putByteArray(MODEL, model);
        }
        GearEffects.write(nbt, GearEffects.HIT, record.plan.hit);
        if (record.plan.moreHits != null) {
            for (int i = 0; i < Math.min(2, record.plan.moreHits.size()); i++) {
                GearEffects.write(nbt, GearEffects.HIT + (i + 2), record.plan.moreHits.get(i));
            }
        }
        GearEffects.write(nbt, GearEffects.IDLE, record.plan.idle);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(RebuildService.title(record))
            .setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.AQUA)));
        Style lore = Style.EMPTY.withItalic(false).withColor(Formatting.GRAY);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("AI 장비 · " + kind.korean + " (철 " + kind.korean + " 성능)").setStyle(lore))));
        return stack;
    }

    /** Decode the model on a gear stack, or null. */
    public static BuildPlan modelOf(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        byte[] bytes = data.copyNbt().getByteArray(MODEL);
        if (bytes.length == 0) {
            return null;
        }
        try {
            return BuildRecord.fromGzip(bytes).plan;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
