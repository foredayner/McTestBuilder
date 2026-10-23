package com.example.ai.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildPlanTest {

    @Test
    void rotatesClockwiseSeenFromAbove() {
        assertArrayEquals(new int[] { 1, 0 }, BuildPlan.rotate(0, -1, 1), "north -> east");
        assertArrayEquals(new int[] { 0, 1 }, BuildPlan.rotate(1, 0, 1), "east -> south");
        assertArrayEquals(new int[] { 3, -2 }, BuildPlan.rotate(3, -2, 4), "four turns = none");
        assertArrayEquals(BuildPlan.rotate(5, 7, 3), BuildPlan.rotate(5, 7, -1));
    }

    @Test
    void facingsAndTurns() {
        assertEquals("south", BuildPlan.normalizeFacing("SOUTH"));
        assertEquals("west", BuildPlan.normalizeFacing("West (-X)"));
        assertEquals("north", BuildPlan.normalizeFacing(null));
        assertEquals(1, BuildPlan.turnsBetween("north", "east"));
        assertEquals(3, BuildPlan.turnsBetween("east", "north"));
        assertEquals(2, BuildPlan.turnsBetween("south", "north"));
    }

    @Test
    void boundsIgnoreAirAndIncludeParts() {
        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("fill", -10, -10, -10, 10, 10, 10, "minecraft:air", "replace"));
        plan.ops.add(new BuildPlan.Op("fill", 0, 0, 0, 2, 3, 4, "minecraft:stone", "outline"));
        assertArrayEquals(new int[] { 0, 0, 0, 2, 3, 4 }, plan.bounds());
        plan.parts.add(new BuildPlan.Part("minecraft:red_concrete", 5.5, 0.5, 0.5, 1, 1, 1, 0, 0, 0));
        assertEquals(6, plan.bounds()[3], "a part at x=5.5 widens the bounds");
        assertTrue(plan.isObject());
        assertFalse(plan.isEmpty());
    }

    @Test
    void problemsCatchBigFillsFarBlocksAndBadParts() {
        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("fill", 0, 0, 0, 40, 40, 40, "minecraft:stone", "replace"));
        plan.ops.add(new BuildPlan.Op("set", 300, 0, 0, 300, 0, 0, "minecraft:stone", "replace"));
        assertEquals(2, plan.problems().size(), plan.problems().toString());

        BuildPlan object = new BuildPlan();
        object.parts.add(new BuildPlan.Part("minecraft:stone", 0, 0, 0, 0, 1, 1, 0, 0, 0));
        assertTrue(object.problems().get(0).contains("size"));
        BuildPlan many = new BuildPlan();
        for (int i = 0; i <= BuildPlan.MAX_PARTS; i++) {
            many.parts.add(new BuildPlan.Part("minecraft:stone", 0, 0, 0, 0.1, 0.1, 0.1, 0, 0, 0));
        }
        assertTrue(many.problems().get(0).contains("parts"));
    }

    @Test
    void recordSurvivesGzipAndOldFilesGetDefaults() {
        BuildPlan plan = new BuildPlan();
        plan.facing = "east";
        plan.parts.add(new BuildPlan.Part("minecraft:gold_block", 1.25, 0.5, -2, 0.5, 1, 0.5, 45, 0, 0));
        BuildRecord record = BuildRecord.create("작은 금 트로피", "OLLAMA", "qwen2.5-coder:7b", plan);
        BuildRecord back = BuildRecord.fromGzip(record.toGzip());
        assertEquals("작은 금 트로피", back.prompt);
        assertEquals("object", back.kind);
        assertEquals(45, back.plan.parts.get(0).yaw);
        assertEquals("east", back.plan.facing);

        BuildRecord old = BuildRecord.fromJson("{\"id\":\"1\",\"prompt\":\"house\",\"plan\":{\"ops\":[],\"facing\":\"north\"}}");
        assertNotNull(old.plan.parts);
        assertEquals("build", old.kind);
        assertThrows(IllegalArgumentException.class, () -> BuildRecord.fromGzip(new byte[] { 1, 2, 3 }));
    }
}
