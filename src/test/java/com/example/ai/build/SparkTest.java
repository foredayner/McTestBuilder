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
    void richEffectsKeepTheirLookColoursPhysicsAndSound() {
        Spark s = Spark.fromMap(Map.of("shape", "slash", "look", "streak", "colors", List.of("#ffffff", "#ffc040", "#ff3000"),
            "vary", 0.6, "gravity", 5, "drag", 0.3, "turbulence", 0.2, "sound", "minecraft:entity.blaze.shoot", "volume", 0.6));
        assertEquals("streak", s.look);
        assertEquals(3, s.colors.length);
        assertEquals(0xFFFFFF, s.color, "the first stop is the colour");
        assertEquals(5, s.gravity, 1e-9);
        assertEquals("entity.blaze.shoot", s.sound);
        Spark p = Spark.fromMap(Map.of("particle", "minecraft:cherry_leaves"));
        assertEquals("cherry_leaves", p.particle);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("look", "laser")));
        assertTrue(e.getMessage().contains("unknown look"));
        e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("particle", "explosion_emitter")));
        assertTrue(e.getMessage().contains("unknown particle"));
        e = assertThrows(IllegalArgumentException.class, () -> Spark.fromMap(Map.of("colors", List.of("#ffffff"))));
        assertTrue(e.getMessage().contains("2-4"));
        // From the network: out of range values are made safe.
        Spark bad = Spark.Effects.fromJson("{\"idle\":[{\"look\":\"x\",\"particle\":\"tnt\",\"vary\":9,\"gravity\":99,"
            + "\"sound\":\"../../x\",\"colors\":[1,2,3,4,5,6]}]}").idle.get(0);
        assertEquals("dot", bad.look);
        assertEquals("", bad.particle);
        assertEquals(1, bad.vary, 1e-9);
        assertEquals(8, bad.gravity, 1e-9);
        assertEquals("", bad.sound);
        assertEquals(4, bad.colors.length);
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
