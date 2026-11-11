package com.example.api;

import com.example.ai.Boot;
import com.example.ai.build.BuildPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrimToSiteTest {

    @BeforeAll
    static void boot() {
        Boot.minecraft();
    }

    @Test
    void crownsPastTheEdgeAreCutAndTheSiteCheckPasses() {
        AiBuildApi.Site site = new AiBuildApi.Site(42, 42, 22);
        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("set", 5, 1, -5, 5, 1, -5, "minecraft:oak_log", null));
        plan.ops.add(new BuildPlan.Op("fill", -6, 10, -8, 3, 14, 2, "minecraft:oak_leaves", "replace"));
        plan.ops.add(new BuildPlan.Op("set", 50, 3, -3, 50, 3, -3, "minecraft:stone", null));
        assertNotNull(AiBuildApi.siteProblemForTests(plan, site));
        assertEquals(2, AiBuildApi.trimForTests(plan, site));
        assertNull(AiBuildApi.siteProblemForTests(plan, site));
        assertEquals(2, plan.ops.size()); // the stone far outside is gone
        BuildPlan.Op leaves = plan.ops.get(1);
        assertEquals(-1, leaves.x1);
        assertEquals(0, leaves.z2);
    }

    @Test
    void aHollowBoxIsCutWithoutANewWall() {
        AiBuildApi.Site site = new AiBuildApi.Site(20, 20, 10);
        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("fill", 10, 0, -10, 30, 6, -4, "minecraft:stone", "hollow"));
        AiBuildApi.trimForTests(plan, site);
        assertNull(AiBuildApi.siteProblemForTests(plan, site));
        for (BuildPlan.Op op : plan.ops) {
            boolean wallAtCut = op.x1 == 20 && op.x2 == 20 && !op.isAir();
            assertFalse(wallAtCut, "no wall where the box was cut");
        }
    }
}
