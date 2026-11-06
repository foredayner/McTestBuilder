package com.example.ai.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NatureHelpersTest {

    private static int[] box(List<Map.Entry<int[], String>> cells, String part) {
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (Map.Entry<int[], String> e : cells) {
            if (!e.getValue().contains(part)) {
                continue;
            }
            int[] p = e.getKey();
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], p[i]);
                b[i + 3] = Math.max(b[i + 3], p[i]);
            }
        }
        return b;
    }

    @Test
    void aBroadTreeHasATrunkBranchesRootsAndAFullCrown() {
        var cells = NatureHelpers.tree(0, 0, 0, 14, "oak", 0);
        int[] logs = box(cells, "_log");
        int[] leaves = box(cells, "leaves");
        assertEquals(0, logs[1], "the trunk starts on the ground");
        assertTrue(leaves[4] >= 12 && leaves[4] <= 18, "the crown reaches about its height: " + leaves[4]);
        assertTrue(leaves[3] - leaves[0] >= 8, "a wide crown: " + (leaves[3] - leaves[0]));
        assertTrue(logs[3] - logs[0] >= 4, "branches and roots reach out: " + (logs[3] - logs[0]));
        assertTrue(cells.stream().allMatch(e -> !e.getValue().contains("leaves") || e.getValue().contains("persistent=true")),
            "leaves never decay");
        assertEquals(cells.size(), NatureHelpers.tree(0, 0, 0, 14, "oak", 0).size(), "the same tree every time");
    }

    @Test
    void conifersDeadTreesAndWillowsHaveTheirShapes() {
        var spruce = NatureHelpers.tree(0, 0, 0, 15, "spruce", 1);
        int[] leaves = box(spruce, "leaves");
        assertTrue(leaves[4] >= 15, "a spruce has leaves up to its tip");
        assertTrue(box(NatureHelpers.tree(0, 0, 0, 9, "dead", 0), "leaves")[0] == Integer.MAX_VALUE, "a dead tree is bare");
        var willow = NatureHelpers.tree(0, 0, 0, 12, "willow", 0);
        var oak = NatureHelpers.tree(0, 0, 0, 12, "oak", 0);
        assertTrue(box(willow, "leaves")[1] < box(oak, "leaves")[1], "a willow's leaves hang lower");
        assertThrows(IllegalArgumentException.class, () -> NatureHelpers.tree(0, 0, 0, 10, "palm", 0));
    }

    @Test
    void rocksBushesAndMoundsStayInTheirBox() {
        var rock = NatureHelpers.rock(2, 0, 3, 6, 4, 5, "mossy");
        int[] b = box(rock, "");
        assertTrue(b[0] >= 2 && b[3] <= 7 && b[1] >= 0 && b[4] <= 3 && b[2] >= 3 && b[5] <= 7, java.util.Arrays.toString(b));
        assertTrue(rock.size() > 20, "a real lump: " + rock.size());
        assertTrue(rock.stream().anyMatch(e -> e.getValue().equals("moss_block")), "a mossy rock has moss");
        assertThrows(IllegalArgumentException.class, () -> NatureHelpers.rock(0, 0, 0, 3, 3, 3, "lava"));
        var bush = NatureHelpers.bush(0, 0, 0, 3, 2, 3, null);
        assertTrue(bush.stream().allMatch(e -> e.getValue().startsWith("oak_leaves[persistent=true]")));
        var mound = NatureHelpers.mound(0, 0, 0, 7, 3, 7, "grass_block");
        assertEquals(2, box(mound, "")[4], "a mound 3 high");
        assertTrue(mound.stream().anyMatch(e -> e.getValue().equals("dirt")), "earth inside");
    }
}
