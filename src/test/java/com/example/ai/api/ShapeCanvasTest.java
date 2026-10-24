package com.example.ai.api;

import com.example.ai.Boot;
import com.example.ai.build.BuildPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCanvasTest {

    @BeforeAll
    static void boot() {
        Boot.minecraft();
    }

    private static BuildPlan record(Runnable script) {
        BuildPlan plan = new BuildPlan();
        MinecraftAI.startRecording(plan);
        try {
            script.run();
        } finally {
            MinecraftAI.stopRecording();
        }
        return plan;
    }

    @Test
    void coverUsesFewBoxes() {
        List<int[]> cube = new java.util.ArrayList<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) cube.add(new int[] { x, y, z });
        assertEquals(1, ShapeCanvas.cover(cube).size(), "a full cube is one box");
        List<int[]> plus = List.of(new int[] { 1, 0, 0 }, new int[] { 0, 0, 1 }, new int[] { 1, 0, 1 }, new int[] { 2, 0, 1 }, new int[] { 1, 0, 2 });
        assertEquals(2, ShapeCanvas.cover(plus).size(), "a plus sign is two crossing bars");
    }

    @Test
    void equipmentUsesTheFinerGrid() {
        BuildPlan plan = new BuildPlan();
        MinecraftAI.startRecording(plan, true);
        try {
            MinecraftAI.addSphere("white_concrete", 0, 0.4, 0, 0.07);
            MinecraftAI.oversize();
        } finally {
            MinecraftAI.stopRecording();
        }
        assertTrue(plan.oversize);
        // Some box edge lies between two 1/16 lines: the grid is 1/32.
        assertTrue(plan.parts.stream().anyMatch(p -> offSixteenth(p.x - p.sx / 2) || offSixteenth(p.sx)), "1/32 grid");
        for (BuildPlan.Part p : plan.parts) {
            assertEquals(0, (p.sx * 32) % 1, 1e-6);
        }
        // Objects stay on the 1/16 grid.
        BuildPlan object = record(() -> MinecraftAI.addSphere("white_concrete", 0, 0.4, 0, 0.07));
        assertFalse(object.oversize);
        for (BuildPlan.Part p : object.parts) {
            assertEquals(0, (p.sx * 16) % 1, 1e-6);
        }
    }

    private static boolean offSixteenth(double v) {
        return Math.abs(v * 16 - Math.round(v * 16)) > 1e-6;
    }

    @Test
    void sphereIsSmoothAndFitsTheBudget() {
        BuildPlan plan = record(() -> MinecraftAI.addSphere("red_concrete", 0, 0.5, 2, 0.5));
        assertFalse(plan.parts.isEmpty());
        assertTrue(plan.parts.size() <= BuildPlan.MAX_PARTS);
        double[] b = plan.partBounds();
        assertEquals(-0.5, b[0], 0.07);
        assertEquals(0.5, b[3], 0.07);
        assertEquals(0, b[1], 0.07, "stands on the ground");
        for (BuildPlan.Part p : plan.parts) {
            assertEquals("minecraft:red_concrete", p.block);
            assertEquals(0, p.yaw);
            // On the 1/16 grid: sizes are whole pixels.
            assertEquals(0, (p.sx * 16) % 1, 1e-6);
        }
        // A big sphere switches to a coarser grid instead of going over the part limit.
        BuildPlan big = record(() -> MinecraftAI.addSphere("red_concrete", 0, 2, 4, 2));
        assertTrue(big.parts.size() <= BuildPlan.MAX_PARTS - 5, "parts " + big.parts.size());
        assertEquals(4, big.partBounds()[3] - big.partBounds()[0], 0.6);
    }

    @Test
    void thinStemsStayInOnePiece() {
        // A stem thinner than one grid pixel, between cell centres: it must not vanish or break up.
        BuildPlan plan = record(() -> MinecraftAI.addTube("iron_block", List.of(List.of(0, 0, 2), List.of(0.05, 0.5, 2.02), List.of(0, 1, 2)), 0.02));
        double low = plan.parts.stream().mapToDouble(p -> p.y - p.sy / 2).min().orElse(9);
        double high = plan.parts.stream().mapToDouble(p -> p.y + p.sy / 2).max().orElse(-9);
        assertTrue(low < 0.07 && high > 0.93, "from " + low + " to " + high);
        // Every height step of the grid has some of the stem in it (no gaps).
        for (double y = 0.05; y < 0.95; y += 1.0 / 16) {
            final double at = y;
            assertTrue(plan.parts.stream().anyMatch(p -> Math.abs(p.y - at) <= p.sy / 2 + 1e-9), "gap at y " + y);
        }
    }

    @Test
    void laterShapesPaintOverEarlierOnes() {
        BuildPlan plan = record(() -> {
            MinecraftAI.addSphere("white_concrete", 0, 0.5, 2, 0.4);
            MinecraftAI.addSphere("black_concrete", 0, 0.5, 1.65, 0.1); // eye, half outside the head
        });
        assertTrue(plan.parts.stream().anyMatch(p -> p.block.equals("minecraft:black_concrete")));
        // No black and white boxes share space: the canvas keeps one block per cell.
        for (BuildPlan.Part a : plan.parts) {
            for (BuildPlan.Part c : plan.parts) {
                if (a.block.equals(c.block)) {
                    continue;
                }
                double ox = Math.min(a.x + a.sx / 2, c.x + c.sx / 2) - Math.max(a.x - a.sx / 2, c.x - c.sx / 2);
                double oy = Math.min(a.y + a.sy / 2, c.y + c.sy / 2) - Math.max(a.y - a.sy / 2, c.y - c.sy / 2);
                double oz = Math.min(a.z + a.sz / 2, c.z + c.sz / 2) - Math.max(a.z - a.sz / 2, c.z - c.sz / 2);
                assertFalse(ox > 1e-6 && oy > 1e-6 && oz > 1e-6, "different blocks overlap");
            }
        }
    }

    @Test
    void mirrorMakesBothSides() {
        BuildPlan plan = record(() -> {
            MinecraftAI.mirrorOn();
            MinecraftAI.addSphere("black_concrete", 0.2, 1, 2, 0.06);
            MinecraftAI.addPart("oak_stairs[facing=east]", 0.3, 0, 2, 0.2, 0.2, 0.2, 20);
            MinecraftAI.mirrorOff();
            MinecraftAI.addSphere("red_concrete", 0.5, 0.2, 3, 0.1); // after mirrorOff: one side only
        });
        double[] eyes = plan.parts.stream().filter(p -> p.block.equals("minecraft:black_concrete")).mapToDouble(p -> p.x).toArray();
        assertTrue(java.util.Arrays.stream(eyes).anyMatch(x -> x > 0.1) && java.util.Arrays.stream(eyes).anyMatch(x -> x < -0.1), "both eyes");
        BuildPlan.Part right = plan.parts.stream().filter(p -> p.block.contains("facing=east")).findFirst().orElseThrow();
        BuildPlan.Part left = plan.parts.stream().filter(p -> p.block.contains("facing=west")).findFirst().orElseThrow();
        assertEquals(-right.x, left.x, 1e-9);
        assertEquals(-right.yaw, left.yaw, 1e-9);
        assertTrue(plan.parts.stream().filter(p -> p.block.equals("minecraft:red_concrete")).allMatch(p -> p.x > 0), "not mirrored after mirrorOff");
    }

    @Test
    void conesCapsulesTubesAndRoundBoxes() {
        BuildPlan plan = record(() -> {
            MinecraftAI.addCone("orange_concrete", 0, 0, 2, 0.3, 0.6);
            MinecraftAI.addCone("white_wool", 1, 0, 2, 0.3, 0.15, 0.4);
            MinecraftAI.addCapsule("brown_concrete", -1, 0.1, 2, -1, 0.8, 2.3, 0.08, 0.04);
            MinecraftAI.addTube("lime_concrete", List.of(List.of(0, 1, 3), List.of(0.3, 1.4, 3.2), List.of(0.1, 1.8, 3)), 0.05);
            MinecraftAI.addRoundBox("oak_planks", 2, 0, 2, 0.8, 0.1, 0.8, 0.03);
        });
        for (String block : new String[] { "orange_concrete", "white_wool", "brown_concrete", "lime_concrete", "oak_planks" }) {
            assertTrue(plan.parts.stream().anyMatch(p -> p.block.equals("minecraft:" + block)), block);
        }
        double coneTop = plan.parts.stream().filter(p -> p.block.equals("minecraft:orange_concrete"))
            .mapToDouble(p -> p.y + p.sy / 2).max().orElse(0);
        // The very tip of a point is thinner than one grid cell, so it ends a little below 0.6.
        assertTrue(coneTop > 0.45 && coneTop <= 0.6 + 1e-9, "cone top " + coneTop);
        assertTrue(plan.problems().isEmpty(), plan.problems().toString());
    }
}
