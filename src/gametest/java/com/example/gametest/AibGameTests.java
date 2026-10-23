package com.example.gametest;

import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.example.ai.build.UndoService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * Real-server checks of the build pipeline: review + dry run place nothing, placing matches the
 * script, a saved build turns with the player, undo restores the area, and objects are display
 * entities that undo removes.
 */
public class AibGameTests implements FabricGameTest {

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestException(message);
        }
    }

    private static ServerPlayerEntity opPlayer(TestContext context, BlockPos at, float yaw) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        // The GameTest server gives new operators level 0; set level 4 directly.
        player.getServer().getPlayerManager().getOpList().add(
            new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        return player;
    }

    private static final String SMALL =
        "ai.placeBox(0, 0, -4, 3, 2, 3, 'stone_bricks')\n"
            + "ai.placeBlock(1, 2, -3, 'oak_stairs[facing=north]')\n";

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dryRunPlacesNothingThenBuildThenUndo(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(3, 1, 7));
        ServerPlayerEntity player = opPlayer(context, origin, 180f); // facing north

        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        check(plan.ops.size() == 2, "expected 2 recorded commands, got " + plan.ops.size());
        check(world.getBlockState(origin.add(0, 0, -4)).isAir(), "dry run must not place blocks");

        Demo.place(player, origin, plan, 0, "small");
        check(world.getBlockState(origin.add(0, 0, -4)).isOf(Blocks.STONE_BRICKS), "box corner placed");
        BlockState stairs = world.getBlockState(origin.add(1, 2, -3));
        check(stairs.isOf(Blocks.OAK_STAIRS) && stairs.get(StairsBlock.FACING) == Direction.NORTH, "stairs face north: " + stairs);

        UndoService.undo(player);
        check(world.getBlockState(origin.add(0, 0, -4)).isAir(), "undo removed the box");
        check(world.getBlockState(origin.add(1, 2, -3)).isAir(), "undo removed the stairs");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedBuildTurnsWithThePlayer(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity player = opPlayer(context, origin, 180f);
        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        check("north".equals(plan.facing), "recorded facing north, got " + plan.facing);

        // Rebuilt by someone facing east: one quarter turn clockwise.
        int turns = BuildPlan.turnsBetween(plan.facing, "east");
        Demo.place(player, origin, plan, turns, "turned");
        check(world.getBlockState(origin.add(4, 0, 0)).isOf(Blocks.STONE_BRICKS), "box now east of the player");
        BlockState stairs = world.getBlockState(origin.add(3, 2, 1));
        check(stairs.isOf(Blocks.OAK_STAIRS) && stairs.get(StairsBlock.FACING) == Direction.EAST, "stairs turned east: " + stairs);
        UndoService.undo(player);
        check(world.getBlockState(origin.add(4, 0, 0)).isAir(), "undo after a turned rebuild");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void objectsAreDisplayEntitiesAndUndoRemovesThem(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
        ServerPlayerEntity player = opPlayer(context, origin, 0f);
        BuildPlan plan = Demo.plan(world, origin, player, Demo.APPLE);
        check(plan.parts.size() == 22, "sphere (20) + stem + leaf = 22 parts, got " + plan.parts.size());
        check(plan.ops.isEmpty(), "objects place no blocks");

        BuildRecord record = Demo.place(player, origin, plan, 0, "apple");
        String tag = "aib_" + record.id;
        Box area = new Box(origin).expand(6);
        int count = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains(tag)).size();
        check(count == 22, "22 display entities summoned, got " + count);

        var solid = world.getEntitiesByClass(com.example.content.SolidHitboxEntity.class, area, e -> e.getCommandTags().contains(tag));
        check(!solid.isEmpty() && solid.size() <= 60, "solid hitboxes follow the shape, got " + solid.size());
        var hitboxes = solid.stream().filter(e -> e.getCommandTags().contains("aib_obj")).toList();
        check(hitboxes.size() == 1, "one hitbox carries the object's name, got " + hitboxes.size());
        check(hitboxes.get(0).getCustomName() != null && "apple".equals(hitboxes.get(0).getCustomName().getString()),
            "hitbox carries the title");
        var listed = com.example.ai.build.ObjectService.nearby(player);
        check(listed.size() == 1 && listed.get(0).tag.equals(tag) && listed.get(0).parts == 22, "nearby list finds it");

        // Delete key path: remove by the hitbox entity id.
        com.example.ai.build.ObjectService.removeByEntity(player, hitboxes.get(0).getId());
        context.waitAndRun(2, () -> {
            int left = world.getEntitiesByClass(net.minecraft.entity.Entity.class, area, e -> e.getCommandTags().contains(tag)).size();
            check(left == 0, "removing the object took every part and the hitbox, " + left + " left");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void objectsCanBePlacedBigger(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 1, 1));
        ServerPlayerEntity player = opPlayer(context, origin, 0f); // facing south
        BuildPlan plan = Demo.plan(world, origin, player, Demo.APPLE);
        BuildRecord record = BuildRecord.create("apple", "TEST", "script", plan);

        BuildRecord big = com.example.ai.build.RebuildService.withScale(player, record, 300);
        check(big != null && big != record, "x3 is allowed for a small apple");
        check(big.id.endsWith("_x300") && big.scale == 3, "scaled copy has its own id and scale: " + big.id);
        check(record.plan.parts.get(0).sx * 3 == big.plan.parts.get(0).sx, "parts are three times bigger");
        double[] a = record.plan.partBounds();
        double[] b = big.plan.partBounds();
        check(Math.abs(b[1] - a[1]) < 1e-6, "still on the ground: " + a[1] + " -> " + b[1]);
        check(Math.abs(b[2] - a[2]) < 1e-6, "near (north) edge keeps its gap: " + a[2] + " -> " + b[2]);
        check(Math.abs((b[4] - b[1]) - 3 * (a[4] - a[1])) < 1e-4, "three times taller");
        check(com.example.ai.build.RebuildService.withScale(player, record, 5000) == null, "x50 is refused");

        Demo.place(player, origin, big.plan, 0, "big apple");
        Box area = new Box(origin).expand(8);
        var hitbox = world.getEntitiesByClass(com.example.content.SolidHitboxEntity.class, area, e -> true);
        check(!hitbox.isEmpty(), "solid hitboxes placed");
        Box solid = hitbox.stream().map(net.minecraft.entity.Entity::getBoundingBox).reduce(Box::union).get();
        check(solid.getLengthY() > 1.5, "hitboxes grew with the object: " + solid);
        var parts = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains("aib"));
        check(parts.size() == 22, "22 parts, got " + parts.size());
        for (var part : parts) {
            // The culling box is display data, not the entity's own size: read it back as NBT.
            var nbt = part.writeNbt(new net.minecraft.nbt.NbtCompound());
            check(nbt.getFloat("width") > 0.2f && nbt.getFloat("height") > 0.2f, "display culling box is set: " + nbt.getFloat("width"));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reviewRejectsBadScriptsBeforeRunning(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerPlayerEntity player = opPlayer(context, origin, 0f);
        try {
            Demo.plan(world, origin, player, "ai.placeBox(0,0,0,'stone')");
            throw new GameTestException("a wrong argument count should be rejected");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("placeBox got 4"), expected.getMessage());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blueprintPaperBuildsWhereClicked(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(4, 1, 6));
        ServerPlayerEntity player = opPlayer(context, origin, 180f); // facing north
        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        BuildRecord record = BuildRecord.create("blueprint", "TEST", "script", plan);
        com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;

        com.example.ai.build.BlueprintStore.give(player, record);
        net.minecraft.item.ItemStack paper = net.minecraft.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (com.example.ai.build.BlueprintStore.idOf(player.getInventory().getStack(i)) != null) {
                paper = player.getInventory().getStack(i);
            }
        }
        check(!paper.isEmpty(), "the player got a blueprint paper");
        check(record.id.equals(com.example.ai.build.BlueprintStore.idOf(paper)), "paper points at the record");

        BlockPos target = origin.add(0, 0, -2);
        check(com.example.ai.build.BlueprintStore.use(player, paper, target.down(), Direction.NORTH), "side click handled");
        check(world.getBlockState(target).isAir(), "clicking the side of a block builds nothing");
        check(com.example.ai.build.BlueprintStore.use(player, paper, target.down(), Direction.UP), "blueprint use handled");
        // The middle of the box's near (south) edge lands on the target; the box runs north from it.
        check(world.getBlockState(target).isOf(Blocks.STONE_BRICKS), "near edge sits on the clicked spot");
        check(world.getBlockState(target.add(-1, 0, -2)).isOf(Blocks.STONE_BRICKS), "far corner north of it");
        check(world.getBlockState(target.add(0, 0, 1)).isAir(), "nothing behind the clicked spot");
        check(!paper.isEmpty(), "creative players keep the paper");
        UndoService.undo(player);
        check(world.getBlockState(target).isAir(), "undo removes a blueprint build too");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void objectItemSitsOnTheClickedBlockAndMinesBack(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
        ServerPlayerEntity player = opPlayer(context, origin, 0f); // facing south
        BuildRecord record = BuildRecord.create("apple", "TEST", "script", Demo.plan(world, origin, player, Demo.APPLE));
        record.icon = new int[16 * 16];
        record.icon[200] = 0xFFCC2222;

        com.example.ai.build.BlueprintStore.give(player, record);
        net.minecraft.item.ItemStack item = net.minecraft.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (com.example.ai.build.ObjectItems.idOf(player.getInventory().getStack(i)) != null) {
                item = player.getInventory().getStack(i);
            }
        }
        check(!item.isEmpty() && item.isOf(com.example.content.AibContent.OBJECT_ITEM), "an object comes as an object item");
        check(com.example.ai.build.BlueprintStore.idOf(item) == null, "not a blueprint paper");
        var data = item.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA).copyNbt();
        check(data.getIntArray("aib_icon").length == 256 && data.getIntArray("aib_icon")[200] == 0xFFCC2222, "the icon picture rides on the item");

        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        BlockPos clicked = origin.add(0, -1, 3);
        int before = item.getCount();
        check(com.example.ai.build.ObjectItems.use(player, item, clicked), "placed from the item");
        check(item.getCount() == before - 1, "survival uses up one item");
        net.minecraft.item.ItemStack placedFrom = item;

        context.waitAndRun(60, () -> {
            Box area = new Box(clicked).expand(4);
            var boxes = world.getEntitiesByClass(com.example.content.SolidHitboxEntity.class, area, e -> true);
            check(!boxes.isEmpty(), "solid hitboxes placed");
            Box all = boxes.stream().map(net.minecraft.entity.Entity::getBoundingBox).reduce(Box::union).get();
            check(Math.abs(all.minY - (clicked.getY() + 1)) < 0.05, "sits on top of the clicked block: " + all.minY);
            check(Math.abs(all.getCenter().x - (clicked.getX() + 0.5)) < 0.15
                && Math.abs(all.getCenter().z - (clicked.getZ() + 0.5)) < 0.15, "centred over the clicked block: " + all.getCenter());
            check(!world.getEntityCollisions(player, all.contract(0.02)).isEmpty(), "the object is solid");
            check(boxes.stream().allMatch(b -> b.getCommandTags().contains("aibid_" + record.id)), "hitboxes know which item to drop");

            com.example.ai.build.ObjectItems.breakObject(player, boxes.get(0).getId());
            context.waitAndRun(2, () -> {
                int left = world.getEntitiesByClass(net.minecraft.entity.Entity.class, area,
                    e -> e.getCommandTags().contains("aib")).size();
                check(left == 0, "mining removed every part and hitbox, " + left + " left");
                var drops = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, area, e -> true);
                check(drops.size() == 1 && record.id.equals(com.example.ai.build.ObjectItems.idOf(drops.get(0).getStack())),
                    "mining drops the object item, got " + drops.size());
                check(placedFrom.getCount() == before - 1, "count unchanged");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedBuildsShowTheirFrontToThePlayer(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(1, 1, 4));
        ServerPlayerEntity player = opPlayer(context, origin, 0f); // designed facing south: door on the near side
        BuildPlan plan = Demo.plan(world, origin, player, Demo.HOUSE);
        check("south".equals(plan.facing), "designed facing south, got " + plan.facing);
        BuildRecord record = BuildRecord.create("house", "TEST", "script", plan);
        com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;

        player.setYaw(-90f); // now facing east
        com.example.ai.build.RebuildService.placeInFront(player, record);
        BlockState door = world.getBlockState(origin.add(4, 0, 0));
        check(door.isOf(Blocks.OAK_DOOR), "door on the side nearest the player, got " + door);
        check(door.get(net.minecraft.block.DoorBlock.FACING) == Direction.WEST, "door faces back toward the player: " + door);
        check(world.getBlockState(origin.add(4, 1, 3)).isOf(Blocks.STONE_BRICKS), "the house is east of the player");
        UndoService.undo(player);
        context.complete();
    }
}
