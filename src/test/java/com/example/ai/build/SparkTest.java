package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SparkTest {

    @Test
    void settingsFromTheScript() {
        Spark s = Spark.fromMap(Map.of("shape", "heart", "fill", true, "color", "#ff66cc", "size", 0.5, "count", 60,
            "moves", List.of("forward", "fade"), "speed", 7));
        assertEquals("heart", s.shape);
        assertTrue(s.fill);
        assertEquals(0xff66cc, s.color);
        assertEquals(List.of("forward", "fade"), s.moves);
        assertEquals(60, s.dots().size());
        for (double[] d : s.dots()) {
            assertTrue(Math.abs(d[0]) <= 1.3 && Math.abs(d[1]) <= 1.3, "inside the shape box");
        }
    }

    @Test
    void customPointsAndLimits() {
        Spark s = Spark.fromMap(Map.of("points", List.of(List.of(0, 1), List.of(1, 0), List.of(0, -1)), "count", 500, "size", 99));
        assertEquals("points", s.shape);
        assertEquals(Spark.MAX_DOTS, s.count, "dots are capped");
        assertEquals(4, s.size, 1e-9, "size is capped");
        assertEquals(Spark.MAX_DOTS, s.dots().size(), "points are joined by lines of dots");
    }

    @Test
    void mistakesAreExplained() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("shape", "nuke")));
        assertTrue(e.getMessage().contains("unknown shape"));
        e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("colour", "red")));
        assertTrue(e.getMessage().contains("#ff66cc"));
        e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("glitter", 1)));
        assertTrue(e.getMessage().contains("unknown effect setting"));
    }

    @Test
    void jsonRoundTripIsClamped() {
        Spark.Effects fx = new Spark.Effects();
        fx.attack.add(Spark.fromMap(Map.of("shape", "star", "moves", List.of("spin"))));
        Spark.Effects back = Spark.Effects.fromJson(fx.toJson());
        assertEquals("star", back.attack.get(0).shape);
        // Bad data from the network is made safe, not trusted.
        Spark bad = Spark.Effects.fromJson("{\"attack\":[{\"shape\":\"x\",\"count\":100000,\"moves\":[\"teleport\"]}]}").attack.get(0);
        assertEquals("burst", bad.shape);
        assertEquals(Spark.MAX_DOTS, bad.count);
        assertTrue(bad.moves.isEmpty());
    }
}
