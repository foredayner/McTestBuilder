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

    @Test
    void scalingGrowsFromTheNearBottomEdge() {
        BuildPlan plan = new BuildPlan();
        plan.facing = "north";
        // A 1 x 0.5 x 1 box standing on the ground, 2 blocks north of the player.
        plan.parts.add(new BuildPlan.Part("minecraft:red_concrete", 0.5, 0.25, -2.5, 1, 0.5, 1, 0, 0, 0));
        BuildPlan big = plan.scaled(3);
        BuildPlan.Part p = big.parts.get(0);
        assertEquals(3, p.sx, 1e-9);
        assertEquals(1.5, p.sy, 1e-9);
        double[] b = big.partBounds();
        assertEquals(0, b[1], 1e-6, "still standing on the ground");
        assertEquals(-2, b[5], 1e-6, "near (south) edge stays 2 blocks ahead");
        assertEquals(0.5, (b[0] + b[3]) / 2, 1e-6, "still centred in front of the player");
        assertEquals("north", big.facing);
        assertEquals(8, plan.maxScale(), 1e-9, "capped at MAX_SCALE");
    }

    @Test
    void solidBoxesFollowTheShape() {
        // A chair: four thin legs, a seat and a back.
        java.util.List<double[]> parts = new java.util.ArrayList<>();
        for (double[] leg : new double[][] { { 0, 0 }, { 0.8, 0 }, { 0, 0.8 }, { 0.8, 0.8 } }) {
            parts.add(new double[] { leg[0], 0, leg[1], leg[0] + 0.2, 0.5, leg[1] + 0.2 });
        }
        parts.add(new double[] { 0, 0.5, 0, 1, 0.6, 1 });   // seat
        parts.add(new double[] { 0, 0.6, 0, 1, 1.4, 0.15 }); // back
        java.util.List<double[]> merged = BuildPlan.mergeBoxes(parts, 3);
        assertEquals(3, merged.size());
        double[] all = { 9, 9, 9, -9, -9, -9 };
        for (double[] b : merged) {
            for (int i = 0; i < 3; i++) {
                all[i] = Math.min(all[i], b[i]);
                all[i + 3] = Math.max(all[i + 3], b[i + 3]);
            }
        }
        assertArrayEquals(new double[] { 0, 0, 0, 1, 1.4, 1 }, all, 1e-9, "nothing lost");
        assertEquals(6, BuildPlan.mergeBoxes(parts, 10).size(), "fewer parts than the limit stay as they are");

        // Square pieces: a 3 x 1 table top becomes three 1 x 1 squares in a row.
        java.util.List<double[]> squares = BuildPlan.squares(new double[] { 0, 1, 0, 3, 1.1, 1 });
        assertEquals(3, squares.size());
        assertEquals(0.5, squares.get(0)[0], 1e-9);
        assertEquals(2.5, squares.get(2)[0], 1e-9);
        assertEquals(1, squares.get(1)[3], 1e-9);
        assertEquals(0.1, squares.get(1)[4], 1e-9);
        // A long thin rail is capped at MAX_SQUARES wider pieces.
        assertEquals(BuildPlan.MAX_SQUARES, BuildPlan.squares(new double[] { 0, 0, 0, 10, 1, 0.1 }).size());
    }
}
