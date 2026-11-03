package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EffectAreaTest {

    private static final double[] O = { 0, 0, 0 };
    private static final double[] SOUTH = { 0, 0, 1 };

    private static EffectArea area(Map<String, Object> settings) {
        return EffectArea.of(Spark.fromMap(settings), O, SOUTH, new double[] { 0, 1.6, 0 }, 1);
    }

    @Test
    void aSwipeReachesAFanInFront() {
        EffectArea swipe = area(Map.of("form", "swipe", "size", 1.4, "arc", 160, "damage", 0.3));
        assertTrue(swipe.hits(new double[] { 0, 1, 2 }, 0.3, 0.9, 0), "straight ahead");
        assertTrue(swipe.hits(new double[] { 1.8, 1, 0.5 }, 0.3, 0.9, 0), "to the side, inside the arc");
        assertFalse(swipe.hits(new double[] { 0, 1, -2 }, 0.3, 0.9, 0), "not behind");
        assertFalse(swipe.hits(new double[] { 0, 1, 6 }, 0.3, 0.9, 0), "not far away");
    }

    @Test
    void aShockwaveSpreadsOverTime() {
        EffectArea ring = area(Map.of("form", "shockwave", "size", 3, "width", 0.3, "ticks", 20));
        double[] far = { 3, 0.5, 0 };
        assertFalse(ring.hits(far, 0.3, 0.9, 0), "not yet at the start");
        assertTrue(ring.hits(far, 0.3, 0.9, 0.9), "reached once it has spread");
        assertFalse(ring.hits(new double[] { 3, 5, 0 }, 0.3, 0.9, 0.9), "only over the ground");
        assertEquals(20, ring.ticks, "checked while it spreads");
    }

    @Test
    void aBeamIsALineAndABoltAColumn() {
        EffectArea beam = area(Map.of("form", "beam", "size", 1, "width", 0.3));
        assertTrue(beam.hits(new double[] { 0, 0, 3 }, 0.3, 0.9, 0));
        assertFalse(beam.hits(new double[] { 2, 0, 3 }, 0.3, 0.9, 0), "not beside it");
        assertFalse(beam.hits(new double[] { 0, 0, 6 }, 0.3, 0.9, 0), "not past its end");
        EffectArea bolt = area(Map.of("form", "bolt", "size", 0.8));
        assertTrue(bolt.hits(new double[] { 1.2, 0.9, 0 }, 0.3, 0.9, 0), "near where it strikes");
        assertFalse(bolt.hits(new double[] { 4, 0.9, 0 }, 0.3, 0.9, 0));
    }

    @Test
    void flyingDotsMoveAndStillOnesStay() {
        EffectArea wave = area(Map.of("shape", "crescent", "size", 0.6, "moves", List.of("forward"), "speed", 8, "ticks", 20));
        assertFalse(wave.hits(new double[] { 0, 0, 4 }, 0.3, 0.9, 0), "not there at the start");
        assertTrue(wave.hits(new double[] { 0, 0, 4 }, 0.3, 0.9, 0.5), "there half a second later");
        assertEquals(20, wave.ticks);
        EffectArea burst = area(Map.of("shape", "burst", "size", 1));
        assertTrue(burst.hits(new double[] { 0.8, 0, 0 }, 0.3, 0.9, 0));
        assertEquals(1, burst.ticks, "checked once");
        assertEquals(EffectArea.Kind.NONE, area(Map.of("form", "ribbon")).kind, "a ribbon never hurts");
    }

    @Test
    void damageIsAtMostHalf() {
        assertEquals(0.5, Spark.fromMap(Map.of("damage", 3)).damage, 1e-9);
        assertEquals(0, Spark.fromMap(Map.of("shape", "star")).damage, 1e-9, "effects do not hurt unless asked");
    }

    @Test
    void extrasAreLimited() {
        Spark.Shot s = Spark.Shot.fromMap(Map.of("extra", List.of(
            Map.of("count", 8, "path", "spiral", "radius", 9),
            Map.of("count", 8, "path", "rain"),
            Map.of("count", 3, "path", "split"))));
        assertEquals(Spark.MAX_EXTRAS, s.extraCount(), "at most 12 in all");
        assertEquals(4, s.extras.get(1).count, "the second group gets what is left");
        assertEquals(4, s.extras.get(0).radius, 1e-9, "radius is capped");
        assertEquals(2, s.extras.size(), "nothing is left for the third group");
        Spark.Shot one = Spark.Shot.fromMap(Map.of("extra", Map.of("count", 3, "path", "spread", "angle", 20)));
        assertEquals(1, one.extras.size(), "one group may be written without a list");
        assertEquals(20, one.extras.get(0).angleOr(15), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Spark.Shot.fromMap(Map.of("extra", List.of(Map.of("path", "teleport")))));
        Spark.Shot back = Spark.Effects.fromJson("{\"shot\":{\"extras\":[{\"count\":99,\"path\":\"x\"}]}}").shot;
        assertEquals(8, back.extras.get(0).count);
        assertEquals("spread", back.extras.get(0).path);
    }
}
