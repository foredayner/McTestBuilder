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

    private Demo() {
    }

    /** Review + dry run. Throws with the reason if the script would be rejected. */
    public static BuildPlan plan(ServerWorld world, BlockPos origin, ServerPlayerEntity player, String script) {
        List<String> review = ScriptLinter.lint(script);
        if (!review.isEmpty()) {
            throw new IllegalStateException("review: " + review);
        }
        MinecraftAI.init(world, origin, player.getYaw(), player.getPitch(), player);
        BuildPlan plan = new BuildPlan();
        plan.facing = BuildPlan.normalizeFacing(MinecraftAI.getFacing());
        MinecraftAI.startRecording(plan);
        ScriptExecutor.ExecutionResult result;
        try {
            result = ScriptExecutor.executeScript(script);
        } finally {
            MinecraftAI.stopRecording();
        }
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
