package com.example.gametest;

import com.example.ai.api.MinecraftAI;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.example.ai.build.UndoService;
import com.example.ai.script.ScriptExecutor;
import com.example.ai.script.ScriptLinter;
import com.example.config.AiBuilderConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Runs a build script the way the mod does (review, dry run, place), without any AI call. */
public final class Demo {

    public static final String HOUSE =
        "ai.placeBox(-3, 0, 4, 7, 4, 7, 'stone_bricks')\n"
            + "ai.placeFloor(-3, -1, 4, 7, 7, 'oak_planks')\n"
            + "ai.placeFloor(-3, 4, 4, 7, 7, 'dark_oak_planks')\n"
            + "ai.clearArea(0, 0, 4, 1, 2, 1)\n"
            + "ai.placeBlock(0, 0, 4, 'oak_door[facing=north,half=lower]')\n"
            + "ai.placeBlock(0, 1, 4, 'oak_door[facing=north,half=upper]')\n"
            + "ai.placeBlock(-3, 2, 7, 'glass')\n"
            + "ai.placeBlock(3, 2, 7, 'glass')\n"
            + "ai.placeBlock(-1, 0, 5, 'oak_stairs[facing=south]')\n";

    public static final String APPLE =
        "def cx = 1.5\n"
            + "def cz = 2.5\n"
            + "ai.addSphere('red_concrete', cx, 0.35, cz, 0.3)\n"
            + "ai.addPart('brown_concrete', cx - 0.03, 0.62, cz - 0.03, 0.06, 0.15, 0.06)\n"
            + "ai.addPart('lime_concrete', cx, 0.7, cz - 0.04, 0.16, 0.02, 0.08, 30)\n";

    /** A greatsword taller than the player, with a long two-handed grip. */
    public static final String GREATSWORD =
        "ai.addCapsule('dark_oak_planks', 0, -0.3, 0, 0, 0.35, 0, 0.05)\n"
            + "ai.addSphere('gold_block', 0, -0.33, 0, 0.08)\n"
            + "ai.addRoundBox('gold_block', -0.35, 0.35, -0.07, 0.7, 0.1, 0.14, 0.03)\n"
            + "ai.addRoundBox('iron_block', -0.2, 0.45, -0.04, 0.4, 2.1, 0.08, 0.02)\n"
            + "ai.addRoundBox('light_gray_concrete', -0.04, 0.5, -0.05, 0.08, 1.9, 0.1, 0)\n"
            + "ai.addCone('iron_block', 0, 2.54, 0, 0.2, 0.36)\n";

    /** A 4-block balloon on a string, floating above the head: worn on the back, asked to be big. */
    public static final String BALLOON =
        "ai.oversize()\n"
            + "ai.addRoundBox('brown_terracotta', -0.27, -0.45, -0.15, 0.54, 0.06, 0.3, 0.02)\n"
            + "ai.addTube('white_wool', [[0.1, -0.42, 0.15], [0.25, 0.6, 0.35], [0.2, 1.75, 0.25]], 0.015)\n"
            + "ai.addCone('red_terracotta', 0.2, 1.68, 0.25, 0.1, 0.16)\n"
            + "ai.addEllipsoid('red_concrete', 0.2, 3.8, 0.25, 1.6, 2.0, 1.6)\n"
            + "ai.addEllipsoid('pink_concrete', -0.35, 4.5, -0.95, 0.3, 0.45, 0.25)\n";

    /** A wooden bow with its own red-fletched arrow, a flame trail and a shockwave where arrows land. */
    public static final String BOW =
        "ai.addTube('dark_oak_planks', [[0.3, -0.65, 0], [0.08, -0.35, 0], [0, 0, 0], [0.08, 0.35, 0], [0.3, 0.65, 0]], 0.035)\n"
            + "ai.addCapsule('brown_wool', 0, -0.1, 0, 0, 0.1, 0, 0.05)\n"
            + "ai.addCapsule('white_wool', 0.3, -0.65, 0, 0.3, 0.65, 0, 0.01)\n"
            + "ai.arrowStart()\n"
            + "ai.addCapsule('oak_planks', 0, 0, 0, 0, 0.7, 0, 0.018)\n"
            + "ai.addCone('iron_block', 0, 0.66, 0, 0.045, 0.14)\n"
            + "ai.mirrorOn()\n"
            + "ai.addPart('red_wool', 0.012, 0.02, -0.006, 0.06, 0.16, 0.012)\n"
            + "ai.mirrorOff()\n"
            + "ai.arrowEnd()\n"
            + "ai.hitEffect('flame', '#ff6600', 6, 'shockwave', 1.5)\n"
            + "ai.arrowTrail('flame', '#ff6600', 2)\n";

    private Demo() {
    }

    /** gear: equipment, on the finer equipment grid (as the mod does for 장비 orders). */
    public static BuildPlan plan(ServerWorld world, BlockPos origin, ServerPlayerEntity player, String script, boolean gear) {
        fine = gear;
        try {
            return plan(world, origin, player, script);
        } finally {
            fine = false;
        }
    }

    private static boolean fine = false;

    /** Review + dry run. Throws with the reason if the script would be rejected. */
    public static BuildPlan plan(ServerWorld world, BlockPos origin, ServerPlayerEntity player, String script) {
        List<String> review = ScriptLinter.lint(script);
        if (!review.isEmpty()) {
            throw new IllegalStateException("review: " + review);
        }
        MinecraftAI.init(world, origin, player.getYaw(), player.getPitch(), player);
        BuildPlan plan = new BuildPlan();
        plan.facing = BuildPlan.normalizeFacing(MinecraftAI.getFacing());
        MinecraftAI.startRecording(plan, fine);
        ScriptExecutor.ExecutionResult result;
        try {
            result = ScriptExecutor.executeScript(script);
        } finally {
            MinecraftAI.stopRecording();
        }
        plan.useObjectFrame();
        if (!result.success()) {
            throw new IllegalStateException("dry run: " + result.message());
        }
        if (!plan.problems().isEmpty()) {
            throw new IllegalStateException("plan: " + plan.problems());
        }
        return plan;
    }

    /** Place a plan right away (one command per tick setting off) and remember it for undo. */
    public static BuildRecord place(ServerPlayerEntity player, BlockPos origin, BuildPlan plan, int turns, String prompt) {
        AiBuilderConfig.get().ticksPerBlock = 1;
        BuildRecord record = BuildRecord.create(prompt, "TEST", "script", plan);
        String tag = "aib_" + record.id;
        UndoService.capture(player, plan, origin, turns, tag, prompt);
        MinecraftAI.init(player.getServerWorld(), origin, player.getYaw(), player.getPitch(), player);
        MinecraftAI.executePlan(plan, origin, turns, tag, prompt);
        return record;
    }
}
