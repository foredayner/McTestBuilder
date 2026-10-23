package com.example.ai.build;

import com.example.content.AibContent;
import com.example.content.SolidHitboxEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Objects as items: handing one out, setting it down on a block, and mining it back.
 *
 * <p>The object itself is saved with the world (ai_builder/blueprints/<id>.json, like
 * blueprints); the item holds its id and a 16 x 16 picture for the icon. A placed object's
 * hitboxes carry "aibid_<id>" so mining it gives the same item back.
 */
public final class ObjectItems {

    public static final String KEY = "aib_object";
    public static final String ICON = "aib_icon";
    public static final String ID_TAG = "aibid_";
    private static final Pattern ID = Pattern.compile("^[0-9a-z_]{1,40}$");
    /** How far (from the eyes) a player may mine an object. */
    private static final double REACH = 7;

    private ObjectItems() {
    }

    public static ItemStack stackFor(BuildRecord record) {
        ItemStack stack = new ItemStack(AibContent.OBJECT_ITEM);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, record.id);
        if (record.icon != null && record.icon.length == 16 * 16) {
            nbt.putIntArray(ICON, record.icon);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(RebuildService.title(record))
            .setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.WHITE)));
        double[] b = record.plan.partBounds();
        Style lore = Style.EMPTY.withItalic(false).withColor(Formatting.GRAY);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal(String.format(Locale.ROOT, "AI 소품 · 크기 %.1f × %.1f × %.1f", b[3] - b[0], b[4] - b[1], b[5] - b[2])).setStyle(lore),
            Text.literal("블록 윗면을 우클릭하면 그 위에 놓여요").setStyle(lore),
            Text.literal("돌처럼 캐면 다시 아이템이 돼요").setStyle(lore))));
        return stack;
    }

    /** The saved object id on an object item, or null. */
    public static String idOf(ItemStack stack) {
        if (stack == null || AibContent.OBJECT_ITEM == null || !stack.isOf(AibContent.OBJECT_ITEM)) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyNbt().getString(KEY);
        return ID.matcher(id).matches() ? id : null;
    }

    /** Right-click on the top of {@code clicked}: set the object down there. True if placed. */
    public static boolean use(ServerPlayerEntity player, ItemStack stack, BlockPos clicked) {
        String id = idOf(stack);
        if (id == null) {
            return false;
        }
        BuildRecord record = BlueprintStore.load(player.getServer(), id);
        if (record == null || !record.plan.isObject() || RebuildService.validate(record.plan) != null) {
            player.sendMessage(Text.literal("이 소품의 내용을 이 월드에서 찾을 수 없어요").formatted(Formatting.RED), true);
            return false;
        }
        RebuildService.placeObjectOn(player, record, clicked);
        player.getServerWorld().playSound(null, clicked.up(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.9F);
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        return true;
    }

    /** The saved object a placed hitbox belongs to, or null (objects placed before items existed). */
    public static String savedId(Entity entity) {
        for (String t : entity.getCommandTags()) {
            if (t.startsWith(ID_TAG)) {
                String id = t.substring(ID_TAG.length());
                return ID.matcher(id).matches() ? id : null;
            }
        }
        return null;
    }

    /**
     * The player finished mining an object (the client times it like stone). Removes every part
     * and hitbox and drops the object item, except in creative mode (like blocks).
     */
    public static void breakObject(ServerPlayerEntity player, int entityId) {
        breakObject(player, entityId, !player.isCreative());
    }

    /** drop: whether the item comes out (not in creative). */
    public static void breakObject(ServerPlayerEntity player, int entityId, boolean drop) {
        ServerWorld world = player.getServerWorld();
        Entity target = world.getEntityById(entityId);
        if (!(target instanceof SolidHitboxEntity) || target.isRemoved() || player.isSpectator()) {
            return;
        }
        if (target.getBoundingBox().squaredMagnitude(player.getEyePos()) > REACH * REACH) {
            return;
        }
        String tag = ObjectService.objectTag(target);
        String id = savedId(target);
        if (tag == null || id == null) {
            player.sendMessage(Text.literal("이 소품은 캐서 가져갈 수 없어요 (Delete 키로 지울 수 있어요)").formatted(Formatting.GRAY), true);
            return;
        }
        List<Entity> all = world.getEntitiesByClass(Entity.class, target.getBoundingBox().expand(48),
            e -> e.getCommandTags().contains(tag));
        double x = 0, y = Double.MAX_VALUE, z = 0;
        int n = 0;
        for (Entity e : all) {
            if (e instanceof SolidHitboxEntity) {
                x += e.getX();
                z += e.getZ();
                y = Math.min(y, e.getY());
                n++;
            }
            e.discard();
        }
        if (n == 0) {
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        } else {
            x /= n;
            z /= n;
        }
        world.playSound(null, BlockPos.ofFloored(x, y, z), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
        if (!drop) {
            return;
        }
        BuildRecord record = BlueprintStore.load(world.getServer(), id);
        if (record != null) {
            ItemEntity drop = new ItemEntity(world, x, y + 0.25, z, stackFor(record));
            drop.setToDefaultPickupDelay();
            world.spawnEntity(drop);
        }
    }
}
