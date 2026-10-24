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
        int n = plan.parts.size();
        check(n >= 6 && n <= 120, "a smooth sphere + stem + leaf is a few dozen merged parts, got " + n);
        check(plan.parts.stream().anyMatch(p -> p.block.equals("minecraft:lime_concrete") && p.yaw == 30), "the exact leaf piece stays as it is");
        check(plan.ops.isEmpty(), "objects place no blocks");

        BuildRecord record = Demo.place(player, origin, plan, 0, "apple");
        String tag = "aib_" + record.id;
        Box area = new Box(origin).expand(6);
        int count = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains(tag)).size();
        check(count == n, n + " display entities summoned, got " + count);

        var solid = world.getEntitiesByClass(com.example.content.SolidHitboxEntity.class, area, e -> e.getCommandTags().contains(tag));
        check(!solid.isEmpty() && solid.size() <= 60, "solid hitboxes follow the shape, got " + solid.size());
        var hitboxes = solid.stream().filter(e -> e.getCommandTags().contains("aib_obj")).toList();
        check(hitboxes.size() == 1, "one hitbox carries the object's name, got " + hitboxes.size());
        check(hitboxes.get(0).getCustomName() != null && "apple".equals(hitboxes.get(0).getCustomName().getString()),
            "hitbox carries the title");
        var listed = com.example.ai.build.ObjectService.nearby(player);
        // Other tests place objects close by at the same time: look for this one.
        check(listed.stream().anyMatch(o -> o.tag.equals(tag) && o.parts == n && "apple".equals(o.name)), "nearby list finds it");

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
        check(parts.size() == big.plan.parts.size(), big.plan.parts.size() + " parts, got " + parts.size());
        for (var part : parts) {
            // The culling box is display data, not the entity's own size: read it back as NBT.
            var nbt = part.writeNbt(new net.minecraft.nbt.NbtCompound());
            check(nbt.getFloat("width") > 0.2f && nbt.getFloat("height") > 0.2f, "display culling box is set: " + nbt.getFloat("width"));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void promptExamplesPassTheReviewAndDryRun(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerPlayerEntity player = opPlayer(context, origin, 90f); // facing west: objects still use their own frame
        for (String example : new String[] {
            com.example.ai.AiController.EXAMPLE_DUCK, com.example.ai.AiController.EXAMPLE_LAMP, com.example.ai.AiController.EXAMPLE_CHAIR }) {
            BuildPlan plan = Demo.plan(world, origin, player, example);
            check(plan.ops.isEmpty() && !plan.parts.isEmpty(), "an object");
            check(plan.parts.size() <= BuildPlan.MAX_PARTS, "within the part limit: " + plan.parts.size());
            check("south".equals(plan.facing), "objects are designed facing south (+Z), got " + plan.facing);
            double[] b = plan.partBounds();
            check(Math.abs((b[0] + b[3]) / 2) < 0.2, "centred on x = 0: " + b[0] + ".." + b[3]);
            check(b[1] > -0.05 && b[1] < 0.1, "stands on y = 0: " + b[1]);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gearIsAnItemWithItsModelAndIronStats(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerPlayerEntity player = opPlayer(context, origin, 0f);
        for (String example : new String[] { com.example.ai.AiController.EXAMPLE_SWORD, com.example.ai.AiController.EXAMPLE_AXE }) {
            BuildPlan plan = Demo.plan(world, origin, player, example, true);
            String problem = com.example.ai.AiController.gearProblem(plan);
            check(problem == null, "example fits the hand: " + problem);
        }
        BuildPlan sword = Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_SWORD);
        BuildRecord record = BuildRecord.create("long sword", "TEST", "script", sword);
        record.kind = "gear";
        record.gear = "sword";
        com.example.ai.build.BlueprintStore.give(player, record);
        net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() instanceof com.example.content.GearItem) {
                stack = player.getInventory().getStack(i);
            }
        }
        check(!stack.isEmpty(), "the player got the equipment item");
        check(((com.example.content.GearItem) stack.getItem()).kind() == com.example.content.GearKind.SWORD, "a sword");
        BuildPlan model = com.example.ai.build.GearItems.modelOf(stack);
        check(model != null && model.parts.size() == sword.parts.size(), "the item carries the whole model");
        check(stack.getMaxDamage() == new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD).getMaxDamage(), "iron sword durability");
        check(stack.contains(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS), "it has attack damage");
        check(stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS), "it counts as a sword (enchantments)");
        check(stack.getMaxCount() == 1, "does not stack");
        // Worn equipment: the examples stay on the head / torso; a helmet is a head item with iron armour.
        BuildPlan helmet = Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_HELMET, true);
        check(com.example.ai.AiController.gearProblem(helmet, com.example.content.GearKind.HELMET) == null,
            "helmet example fits: " + com.example.ai.AiController.gearProblem(helmet, com.example.content.GearKind.HELMET));
        check(helmet.parts.size() <= BuildPlan.MAX_PARTS, "mask within the part limit: " + helmet.parts.size());
        check(helmet.parts.stream().anyMatch(q -> Math.abs(q.sx * 16 - Math.round(q.sx * 16)) > 1e-6),
            "the mask uses the finer equipment grid");
        BuildPlan hat = Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_HAT, true);
        check(com.example.ai.AiController.gearProblem(hat, com.example.content.GearKind.HAT) == null,
            "hat example fits: " + com.example.ai.AiController.gearProblem(hat, com.example.content.GearKind.HAT));
        // Big only when asked: the balloon passes with ai.oversize(), fails without it, and nothing passes 5 blocks.
        BuildPlan balloon = Demo.plan(world, origin, player, Demo.BALLOON, true);
        check(balloon.oversize, "oversize recorded");
        check(com.example.ai.AiController.gearProblem(balloon, com.example.content.GearKind.BACK) == null,
            "a big balloon fits when asked: " + com.example.ai.AiController.gearProblem(balloon, com.example.content.GearKind.BACK));
        BuildPlan small = Demo.plan(world, origin, player, Demo.BALLOON.replace("ai.oversize()\n", ""), true);
        check(com.example.ai.AiController.gearProblem(small, com.example.content.GearKind.BACK) != null, "not big unless asked");
        BuildPlan huge = Demo.plan(world, origin, player, "ai.oversize()\nai.addEllipsoid('red_concrete', 0, 4, 0, 3.5, 3, 3)\n", true);
        check(com.example.ai.AiController.gearProblem(huge, com.example.content.GearKind.BACK) != null, "at most 5 blocks");
        BuildPlan wings = Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_WINGS, true);
        check(com.example.ai.AiController.gearProblem(wings, com.example.content.GearKind.BACK) == null,
            "wings example fits: " + com.example.ai.AiController.gearProblem(wings, com.example.content.GearKind.BACK));
        check(com.example.ai.AiController.gearProblem(wings, com.example.content.GearKind.HELMET) != null, "wings are too wide for a head");
        BuildRecord helm = BuildRecord.create("helm", "TEST", "script", helmet);
        helm.kind = "gear";
        helm.gear = "helmet";
        net.minecraft.item.ItemStack helmStack = com.example.ai.build.GearItems.stackFor(helm);
        var equippable = helmStack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        check(equippable != null && equippable.slot() == net.minecraft.entity.EquipmentSlot.HEAD, "a helmet goes on the head");
        check(helmStack.isIn(net.minecraft.registry.tag.ItemTags.HEAD_ARMOR), "counts as head armour (enchantments)");
        check(helmStack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS) != null
            && !helmStack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty(), "it gives armour");

        // Effects and movement: recorded, carried by the item, and dropped when the order did not ask.
        BuildPlan fx = Demo.plan(world, origin, player, """
            ai.addCapsule('oak_planks', 0, -0.1, 0, 0, 1.4, 0, 0.04)
            ai.mirrorOn()
            ai.groupStart('gem')
            ai.addSphere('amethyst_block', 0.12, 1.45, 0, 0.06)
            ai.groupEnd()
            ai.mirrorOff()
            ai.animate('gem', 'spin', 0.12, 1.45, 0, 'y', 0.5, 0)
            ai.glow('gem')
            ai.hitEffect('flame', '#ff6600', 6)
            ai.idleEffect('dust', '#33ccff', 2)
            ai.aura('#ffdd66', 0.6)
            ai.hitEffect('end_rod', '#ffffff', 4, 'converge', 2)
            """);
        check(fx.aura != null && fx.aura.color == 0xffdd66, "aura recorded");
        check(fx.moreHits.size() == 1 && "converge".equals(fx.moreHits.get(0).pattern), "a second hit effect plays together");
        check(fx.parts.stream().anyMatch(q -> q.group > 0), "group parts are marked");
        check(fx.anims.size() == 4, "spin + glow for the gem and its mirrored twin, got " + fx.anims.size());
        check(fx.hit != null && "flame".equals(fx.hit.particle) && fx.idle != null && fx.idle.color == 0x33ccff, "effects recorded");
        BuildRecord fxRecord = BuildRecord.create("staff", "TEST", "script", fx);
        fxRecord.kind = "gear";
        fxRecord.gear = "staff";
        net.minecraft.item.ItemStack fxStack = com.example.ai.build.GearItems.stackFor(fxRecord);
        check(com.example.ai.build.GearEffects.read(fxStack, com.example.ai.build.GearEffects.HIT) != null, "hit effect on the item");
        check(com.example.ai.build.GearItems.modelOf(fxStack).anims.size() == 4, "movements ride in the model");
        check(com.example.ai.build.GearItems.modelOf(fxStack).aura != null, "the aura rides in the model");
        check(com.example.ai.build.GearEffects.read(fxStack, com.example.ai.build.GearEffects.HIT + 2) != null, "second hit effect on the item");
        com.example.ai.AiController.applyEffectRequest(fx, com.example.content.GearKind.STAFF, "", "");
        check(fx.hit == null && fx.moreHits.isEmpty() && fx.idle == null && fx.anims.isEmpty() && fx.aura == null,
            "empty effect fields = no effects at all");
        try {
            Demo.plan(world, origin, player, "ai.addSphere('stone', 0, 0.5, 0, 0.2)\nai.hitEffect('nuke', '#ffffff', 3)");
            throw new GameTestException("an unknown particle should be refused");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("unknown particle"), expected.getMessage());
        }

        // Too long for the hand: refused with a reason the AI can fix.
        BuildPlan pole = Demo.plan(world, origin, player, "ai.addCapsule('oak_planks', 0, 0, 0, 0, 4, 0, 0.05)");
        check(com.example.ai.AiController.gearProblem(pole) != null, "a 4-block pole is too long");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void magicCircleAppearsAndCleansUp(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
        ServerPlayerEntity player = opPlayer(context, origin, 0f);
        var target = context.spawnEntity(EntityType.ARMOR_STAND, new BlockPos(2, 1, 5));
        BuildPlan.Fx fx = new BuildPlan.Fx("enchant", 0x9933ff, 4);
        fx.pattern = "magic_circle";
        fx.size = 1.5;
        check(com.example.ai.build.HitEffects.start(world, player, target, fx, com.example.ai.build.GearEffects.particle(fx)),
            "the effect starts");
        check(!com.example.ai.build.HitEffects.start(world, player, target, fx, com.example.ai.build.GearEffects.particle(fx)),
            "one shaped effect per attacker at a time");
        Box area = new Box(target.getBlockPos()).expand(4);
        context.waitAndRun(6, () -> {
            int pieces = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains("aib_fx")).size();
            check(pieces == 25, "20 ring pieces and 5 star lines, got " + pieces);
            context.waitAndRun(40, () -> {
                int left = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains("aib_fx")).size();
                check(left == 0, "the circle is removed when the effect ends, " + left + " left");
                context.complete();
            });
        });
    }

    /** A sword's attack effect plays when it is swung (in front of the player), even at nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void swingingASwordPlaysItsAttackEffect(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(4, 1, 1));
        ServerPlayerEntity player = opPlayer(context, origin, 0f); // facing south (+Z)
        BuildPlan plan = Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_SWORD
            + "\nai.hitEffect('enchant', '#9933ff', 4, 'magic_circle', 1.5)\n", true);
        plan.motion = "thrust";
        BuildRecord record = BuildRecord.create("swing test", "TEST", "script", plan);
        record.kind = "gear";
        record.gear = "sword";
        net.minecraft.item.ItemStack sword = com.example.ai.build.GearItems.stackFor(record);
        check("thrust".equals(com.example.content.GearMotions.of(sword)), "the sword keeps its attack motion");
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, sword);
        com.example.ai.build.GearEffects.onSwing(player);
        com.example.ai.build.GearEffects.onSwing(player); // too soon: ignored
        Box ahead = new Box(origin.add(0, 0, 2)).expand(3);
        context.waitAndRun(6, () -> {
            int pieces = world.getEntitiesByType(EntityType.BLOCK_DISPLAY, ahead, e -> e.getCommandTags().contains("aib_fx")).size();
            check(pieces == 25, "a magic circle in front of the player, got " + pieces + " pieces");
            context.complete();
        });
    }

    /** An AI bow shoots arrows that carry the bow (for their shape) and land like normal arrows. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void bowShootsItsOwnArrows(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
        ServerPlayerEntity player = opPlayer(context, origin, 0f);
        BuildPlan plan = Demo.plan(world, origin, player, Demo.BOW, true);
        check(com.example.ai.AiController.gearProblem(plan, com.example.content.GearKind.BOW) == null,
            "the bow fits: " + com.example.ai.AiController.gearProblem(plan, com.example.content.GearKind.BOW));
        check(plan.trail != null && plan.hit != null, "trail and landing effect recorded");
        check(plan.parts.stream().anyMatch(p -> p.group == BuildPlan.ARROW_GROUP), "arrow pieces recorded");
        com.example.ai.AiController.applyEffectRequest(plan, com.example.content.GearKind.BOW, "불꽃 화살", "");
        check(plan.trail != null, "the arrow effect field keeps the trail");
        BuildRecord record = BuildRecord.create("bow test", "TEST", "script", plan);
        record.kind = "gear";
        record.gear = "bow";
        net.minecraft.item.ItemStack bow = com.example.ai.build.GearItems.stackFor(record);
        check(bow.getItem() instanceof net.minecraft.item.BowItem, "it is a bow");
        check(bow.isIn(net.minecraft.registry.tag.ItemTags.BOW_ENCHANTABLE), "takes bow enchantments");
        var arrow = ((com.example.content.GearBowItem) bow.getItem()).createArrowEntity(world, player, bow,
            new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW), false);
        check(arrow instanceof com.example.content.AibArrowEntity, "plain arrows become AI arrows");
        com.example.content.AibArrowEntity aib = (com.example.content.AibArrowEntity) arrow;
        check(com.example.ai.build.GearItems.modelOf(aib.bow()) != null, "the arrow carries the bow's model");
        var tipped = ((com.example.content.GearBowItem) bow.getItem()).createArrowEntity(world, player, bow,
            new net.minecraft.item.ItemStack(net.minecraft.item.Items.SPECTRAL_ARROW), false);
        check(!(tipped instanceof com.example.content.AibArrowEntity), "other arrows stay vanilla");
        aib.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY() + 2, origin.getZ() + 0.5, 0, 0);
        aib.setVelocity(0, -1, 0.3);
        world.spawnEntity(aib);
        context.waitAndRun(40, () -> {
            check(aib.hasLanded(), "the arrow landed (and played its landing effect)");
            context.complete();
        });
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

    /**
     * Two operators build at the same time with slow placement. Each queued command must still run
     * as its own player at its own spot, and undo must stop only the undoing player's build.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void twoPlayersBuildAtOnceWithoutMixingUp(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos a = context.getAbsolutePos(new BlockPos(2, 1, 7));
        BlockPos b = context.getAbsolutePos(new BlockPos(7, 1, 7));
        ServerPlayerEntity alice = opPlayer(context, a, 180f);
        ServerPlayerEntity bob = opPlayer(context, b, 180f);
        check(!alice.getUuid().equals(bob.getUuid()), "two different players");
        BuildPlan planA = Demo.plan(world, a, alice, SMALL);
        BuildPlan planB = Demo.plan(world, b, bob, SMALL);
        BuildPlan planC = Demo.plan(world, a, alice, SMALL);

        com.example.config.AiBuilderConfig.get().ticksPerBlock = 2;
        // Alice starts, then Bob starts before Alice's commands have run (this used to make
        // Alice's remaining commands run as Bob).
        UndoService.capture(alice, planA, a, 0, "aib_twoA", "A");
        com.example.ai.api.MinecraftAI.init(world, a, alice.getYaw(), alice.getPitch(), alice);
        com.example.ai.api.MinecraftAI.executePlan(planA, a, 0, "aib_twoA", "A");
        UndoService.capture(bob, planB, b, 0, "aib_twoB", "B");
        com.example.ai.api.MinecraftAI.init(world, b, bob.getYaw(), bob.getPitch(), bob);
        com.example.ai.api.MinecraftAI.executePlan(planB, b, 0, "aib_twoB", "B");
        check(world.getBlockState(a.add(0, 0, -4)).isAir() && world.getBlockState(b.add(0, 0, -4)).isAir(),
            "slow placement has not run yet");
        // Alice undoes right away: her build stops, Bob's keeps going.
        UndoService.undo(alice);
        check(com.example.ai.build.BuildTaskQueue.pendingCount() > 0, "Bob's build is still queued");

        context.runAtTick(40, () -> {
            check(world.getBlockState(b.add(0, 0, -4)).isOf(Blocks.STONE_BRICKS), "Bob's box was built at Bob's spot");
            check(world.getBlockState(b.add(1, 2, -3)).isOf(Blocks.OAK_STAIRS), "Bob's stairs too");
            check(world.getBlockState(a.add(0, 0, -4)).isAir(), "Alice's cancelled build did not appear");
            // Now both build at once again; both finish, each at their own place.
            UndoService.capture(alice, planC, a, 0, "aib_twoC", "C");
            com.example.ai.api.MinecraftAI.init(world, a, alice.getYaw(), alice.getPitch(), alice);
            com.example.ai.api.MinecraftAI.executePlan(planC, a, 0, "aib_twoC", "C");
            com.example.ai.api.MinecraftAI.init(world, b, bob.getYaw(), bob.getPitch(), bob);
        });
        context.runAtTick(80, () -> {
            check(world.getBlockState(a.add(0, 0, -4)).isOf(Blocks.STONE_BRICKS), "Alice's second build finished");
            check(world.getBlockState(a.add(1, 2, -3)).isOf(Blocks.OAK_STAIRS), "with its stairs");
            com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyOperatorsCanUseTheWorkshop(TestContext context) {
        ServerPlayerEntity guest = context.createMockCreativeServerPlayerInWorld();
        check(!com.example.network.AiNetworking.allowed(guest), "a non-operator is turned away");
        ServerPlayerEntity op = opPlayer(context, context.getAbsolutePos(new BlockPos(1, 1, 1)), 0f);
        check(com.example.network.AiNetworking.allowed(op), "an operator may use it");
        var root = context.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("aibuild");
        check(root != null, "/aibuild exists");
        check(!root.canUse(guest.getCommandSource()), "/aibuild is hidden from non-operators");
        check(root.canUse(op.getCommandSource()), "/aibuild works for operators");
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

        // The mock player is always in creative: the item is kept (survival takes one, like blocks).
        BlockPos clicked = origin.add(0, -1, 3);
        int before = item.getCount();
        check(com.example.ai.build.ObjectItems.use(player, item, clicked), "placed from the item");
        check(item.getCount() == before, "creative keeps the item");
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

            com.example.ai.build.ObjectItems.breakObject(player, boxes.get(0).getId(), true); // as in survival
            context.waitAndRun(2, () -> {
                int left = world.getEntitiesByClass(net.minecraft.entity.Entity.class, area,
                    e -> e.getCommandTags().contains("aib")).size();
                check(left == 0, "mining removed every part and hitbox, " + left + " left");
                var drops = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, area, e -> true);
                check(drops.size() == 1 && record.id.equals(com.example.ai.build.ObjectItems.idOf(drops.get(0).getStack())),
                    "mining drops the object item, got " + drops.size());
                check(placedFrom.getCount() == before, "count unchanged");
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
