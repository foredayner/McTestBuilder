package com.example.ai.api;

import com.example.ai.Boot;
import com.example.ai.build.BuildPlan;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftAITest {

    @BeforeAll
    static void boot() {
        Boot.minecraft();
    }

    @Test
    void blockStatesTurnWithTheBuild() {
        String east = MinecraftAI.rotateBlockState("minecraft:oak_stairs[facing=north,half=bottom]", 1);
        assertTrue(east.contains("facing=east"), east);
        String log = MinecraftAI.rotateBlockState("minecraft:oak_log[axis=x]", 1);
        assertTrue(log.contains("axis=z"), log);
        assertEquals("minecraft:stone", MinecraftAI.rotateBlockState("minecraft:stone", 1));
        assertEquals("minecraft:oak_stairs[facing=north]", MinecraftAI.rotateBlockState("minecraft:oak_stairs[facing=north]", 0));
        assertEquals("not a block", MinecraftAI.rotateBlockState("not a block", 1), "unknown text passes through");
    }

    @Test
    void blockStateNbt() {
        assertEquals("{Name:\"minecraft:stone\"}", MinecraftAI.blockStateNbt("minecraft:stone"));
        assertEquals("{Name:\"minecraft:oak_stairs\",Properties:{facing:\"east\",half:\"top\"}}",
            MinecraftAI.blockStateNbt("minecraft:oak_stairs[facing=east,half=top]"));
    }

    @Test
    void summonCentresThePartOnTheEntity() {
        BuildPlan.Part part = new BuildPlan.Part("minecraft:red_concrete", 0.5, 0.25, 0.5, 1, 0.5, 1, 0, 0, 0);
        String cmd = MinecraftAI.summonCommand(part, new BlockPos(10, 64, -3), 0, "aib_1");
        // Entity stands 0.75 (half the part's diagonal) below the centre; the model is lifted back.
        assertTrue(cmd.startsWith("summon minecraft:block_display 10.5000 63.5000 -2.5000 "), cmd);
        assertTrue(cmd.contains("translation:[-0.5000f,0.5000f,-0.5000f]"), cmd);
        assertTrue(cmd.contains("width:1.5000f,height:1.5000f"), cmd);
        assertTrue(cmd.contains("scale:[1.0000f,0.5000f,1.0000f]"), cmd);
        assertTrue(cmd.contains("Tags:[\"aib\",\"aib_1\"]"), cmd);
        assertTrue(cmd.contains("left_rotation:[0.000000f,0.000000f,0.000000f,1.000000f]"), cmd);

        // One quarter turn: a part 2 blocks north ends up 2 blocks east.
        BuildPlan.Part north = new BuildPlan.Part("minecraft:stone", 0, 0, -2, 1, 1, 1, 0, 0, 0);
        String turned = MinecraftAI.summonCommand(north, BlockPos.ORIGIN, 1, "t");
        assertTrue(turned.startsWith("summon minecraft:block_display 2.0000 -0.8660 0.0000 "), turned);
    }
}
