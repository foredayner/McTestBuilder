package com.example.ai.build;

import com.example.ai.Boot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RebuildServiceTest {

    @BeforeAll
    static void boot() {
        Boot.minecraft();
    }

    private static BuildPlan withOp(String block, String mode) {
        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("fill", 0, 0, 0, 1, 1, 1, block, mode));
        return plan;
    }

    @Test
    void acceptsNormalPlans() {
        assertNull(RebuildService.validate(withOp("minecraft:oak_stairs[facing=north,half=bottom]", "outline")));
    }

    @Test
    void rejectsAnythingThatCouldTurnIntoAnotherCommand() {
        assertNotNull(RebuildService.validate(withOp("minecraft:stone replace\nop @a", "replace")));
        assertNotNull(RebuildService.validate(withOp("minecraft:stone", "replace run say hi")));
        assertNotNull(RebuildService.validate(withOp("stone", "replace")), "ids must be namespaced");
        BuildPlan far = new BuildPlan();
        far.ops.add(new BuildPlan.Op("set", 9999, 0, 0, 9999, 0, 0, "minecraft:stone", "replace"));
        assertNotNull(RebuildService.validate(far));
        assertNotNull(RebuildService.validate(new BuildPlan()), "empty");
        BuildPlan badPart = new BuildPlan();
        badPart.parts.add(new BuildPlan.Part("minecraft:stone", 0, 0, 0, 100, 1, 1, 0, 0, 0));
        assertNotNull(RebuildService.validate(badPart));
    }
}
