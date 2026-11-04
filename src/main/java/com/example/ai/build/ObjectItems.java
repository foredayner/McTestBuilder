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
    /** On a furniture preset item: which library prop (PropLibrary id). */
    public static final String PROP_KEY = "aib_prop";
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

    /** A furniture preset from the prop library as an item (소품 프리셋). */
    public static ItemStack propStack(PropLibrary.Def def) {
        ItemStack stack = new ItemStack(AibContent.OBJECT_ITEM);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(PROP_KEY, def.id());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(def.name()).setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.WHITE)));
        Style lore = Style.EMPTY.withItalic(false).withColor(Formatting.GRAY);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("소품 프리셋 · " + def.w() + "칸").setStyle(lore))));
        return stack;
    }

    /** How a preset is set down (for its item's text and the preset list). */
    public static String howToPlace(PropLibrary.Def def) {
        return switch (def.mount()) {
            case "window" -> "창문(2×2)이나 벽을 우클릭하면 그 창을 덮어요";
            case "wall" -> "벽 옆면을 우클릭하면 벽에 걸려요";
            case "ceiling" -> "천장 아랫면을 우클릭하면 매달려요";
            case "top" -> "탁자 윗면을 우클릭하면 그 위에 놓여요";
            default -> "블록 윗면을 우클릭하면 그 위에 놓여요";
        };
    }

    /** The library prop on a preset item, or null. */
    public static PropLibrary.Def propOf(ItemStack stack) {
        if (stack == null || AibContent.OBJECT_ITEM == null || !stack.isOf(AibContent.OBJECT_ITEM)) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? null : PropLibrary.get(data.copyNbt().getString(PROP_KEY));
    }

    /**
     * Right-click with a preset: where it goes depends on how it is mounted (on the top of a block,
     * on a wall's side, under a ceiling, over a window). Its front looks at the player, or out of the
     * wall. False (with a hint) when that face does not suit it.
     */
    public static boolean useProp(ServerPlayerEntity player, ItemStack stack, BlockPos clicked, net.minecraft.util.math.Direction side,
                                  net.minecraft.util.math.Vec3d hit) {
        PropLibrary.Def def = propOf(stack);
        if (def == null) {
            return false;
        }
        if (!com.example.ai.api.MinecraftAI.canExecuteBuildCommands(player)) {
            player.sendMessage(Text.literal("치트(OP 권한 2)가 켜져 있어야 놓을 수 있어요").formatted(Formatting.RED), true);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        boolean onWall = side.getAxis().isHorizontal();
        String mount = def.mount();
        boolean wallMount = "wall".equals(mount) || "window".equals(mount);
        if (wallMount != onWall || "ceiling".equals(mount) != (side == net.minecraft.util.math.Direction.DOWN)
            || (!wallMount && !"ceiling".equals(mount) && side != net.minecraft.util.math.Direction.UP)) {
            player.sendMessage(Text.literal(howToPlace(def)).formatted(Formatting.GRAY), true);
            return false;
        }
        String facing;
        BlockPos at;
        double y = 0;
        if (wallMount) {
            // In the block in front of the clicked wall, its front looking out of the wall.
            facing = side.asString();
            boolean alongX = side.getAxis() == net.minecraft.util.math.Direction.Axis.Z;
            BlockPos start = clicked;
            if ("window".equals(mount)) {
                // Over the window: from its lowest pane, starting at the left of the 2 panes around the click.
                BlockPos prev = alongX ? clicked.west() : clicked.north();
                if (glass(world, clicked) && glass(world, prev)) {
                    start = prev;
                }
                for (int i = 0; i < 4 && glass(world, start) && glass(world, start.down()); i++) {
                    start = start.down();
                }
            } else {
                // On the wall at its own height above the floor in front of the wall.
                start = clicked;
                for (int i = 0; i < 6 && !world.getBlockState(start.offset(side).down()).isSolidBlock(world, start.offset(side).down()); i++) {
                    start = start.down();
                }
            }
            at = start.offset(side);
        } else if ("ceiling".equals(mount)) {
            facing = facingToward(player);
            at = clicked.down();
        } else {
            facing = facingToward(player);
            at = clicked.up();
            y = Math.min(0, hit.y - at.getY()); // a slab or carpet: stand on its top
            // Wide props are centred on the clicked block.
            int back = (def.w() - 1) / 2;
            at = "north".equals(facing) || "south".equals(facing) ? at.west(back) : at.north(back);
        }
        BuildPlan plan = new BuildPlan();
        plan.props.add(new BuildPlan.Prop(def.id(), 0, y, 0, facing));
        com.example.ai.api.MinecraftAI.executePlanAsServer(world, plan, at, 0, null, def.name(), List.of());
        world.playSound(null, at, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.9F);
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        return true;
    }

    private static boolean glass(ServerWorld world, BlockPos pos) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).getPath();
        return id.contains("glass");
    }

    /** The way a prop's front looks so that it faces the player. */
    private static String facingToward(ServerPlayerEntity player) {
        return player.getHorizontalFacing().getOpposite().asString();
    }

    /** The library prop a placed hitbox belongs to, or null. */
    public static PropLibrary.Def propId(Entity entity) {
        for (String t : entity.getCommandTags()) {
            if (t.startsWith(com.example.ai.api.MinecraftAI.PROP_TAG)) {
                return PropLibrary.get(t.substring(com.example.ai.api.MinecraftAI.PROP_TAG.length()));
            }
        }
        return null;
    }

    /** A lamp's hidden light blocks go with it. */
    public static void clearLights(ServerWorld world, List<Entity> removed) {
        for (Entity e : removed) {
            if (!(e instanceof SolidHitboxEntity)) {
                continue;
            }
            PropLibrary.Def def = propId(e);
            if (def == null || def.light() <= 0) {
                continue;
            }
            Box box = e.getBoundingBox().expand(1.5);
            for (BlockPos p : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
                if (world.getBlockState(p).isOf(net.minecraft.block.Blocks.LIGHT)) {
                    world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
        }
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
        if (ObjectBuilds.building(tag)) {
            player.sendMessage(Text.literal("아직 만드는 중이에요. 다 나타난 뒤에 캐 주세요.").formatted(Formatting.GRAY), true);
            return;
        }
        String id = savedId(target);
        PropLibrary.Def prop = propId(target);
        if (tag == null || (id == null && prop == null)) {
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
        clearLights(world, all);
        world.playSound(null, BlockPos.ofFloored(x, y, z), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
        if (!drop) {
            return;
        }
        BuildRecord record = id == null ? null : BlueprintStore.load(world.getServer(), id);
        ItemStack back = record != null ? stackFor(record) : prop != null ? propStack(prop) : null;
        if (back != null) {
            ItemEntity item = new ItemEntity(world, x, y + 0.25, z, back);
            item.setToDefaultPickupDelay();
            world.spawnEntity(item);
        }
    }
}
