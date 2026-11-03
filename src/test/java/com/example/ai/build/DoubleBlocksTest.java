package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DoubleBlocksTest {

    private static BuildPlan.Op set(int x, int y, int z, String block) {
        return new BuildPlan.Op("set", x, y, z, x, y, z, block, "replace");
    }

    private static BuildPlan.Op at(List<BuildPlan.Op> ops, int x, int y, int z) {
        BuildPlan.Op found = null;
        for (BuildPlan.Op op : ops) {
            if ("set".equals(op.kind) && op.x1 == x && op.y1 == y && op.z1 == z) {
                found = op; // the last one placed there wins
            }
        }
        return found;
    }

    @Test
    void aLowerDoorGetsItsUpperHalfOverAWall() {
        List<BuildPlan.Op> ops = new ArrayList<>();
        ops.add(set(2, 1, -1, "minecraft:spruce_door[facing=south,hinge=left]"));
        ops.add(new BuildPlan.Op("fill", 0, 1, -1, 5, 4, -1, "minecraft:bricks", "replace")); // the wall over it, later
        List<BuildPlan.Op> out = DoubleBlocks.complete(ops);
        assertEquals("minecraft:spruce_door[facing=south,hinge=left,half=lower]", at(out, 2, 1, -1).block);
        assertEquals("minecraft:spruce_door[facing=south,hinge=left,half=upper]", at(out, 2, 2, -1).block);
        assertEquals("fill", out.get(0).kind, "the wall first, the door after it");
    }

    @Test
    void anUpperDoorGetsItsLowerHalf() {
        List<BuildPlan.Op> out = DoubleBlocks.complete(List.of(set(0, 2, 0, "oak_door[half=upper,facing=east]")));
        assertEquals(2, out.size());
        assertTrue(at(out, 0, 1, 0).block.contains("half=lower"));
        assertTrue(at(out, 0, 2, 0).block.contains("half=upper"));
    }

    @Test
    void bothHalvesGiven() {
        List<BuildPlan.Op> out = DoubleBlocks.complete(List.of(set(0, 1, 0, "oak_door[half=lower]"), set(0, 2, 0, "oak_door[half=upper]")));
        assertEquals(2, out.size(), "one pair, not two");
    }

    @Test
    void aBedGetsItsHeadTowardItsFacing() {
        List<BuildPlan.Op> out = DoubleBlocks.complete(List.of(set(3, 1, -4, "minecraft:red_bed[facing=north]")));
        assertEquals("minecraft:red_bed[facing=north,part=foot]", at(out, 3, 1, -4).block);
        assertEquals("minecraft:red_bed[facing=north,part=head]", at(out, 3, 1, -5).block);
        List<BuildPlan.Op> head = DoubleBlocks.complete(List.of(set(3, 1, -4, "white_bed[part=head,facing=east]")));
        assertTrue(at(head, 2, 1, -4).block.contains("part=foot"), "a head alone gets its foot behind it");
    }

    @Test
    void otherBlocksStayAsTheyAre() {
        List<BuildPlan.Op> ops = List.of(set(0, 0, 0, "stone"), set(1, 0, 0, "oak_trapdoor[half=top]"));
        assertSame(ops, DoubleBlocks.complete(ops));
        assertTrue(DoubleBlocks.isDouble("sunflower"));
    }
}
