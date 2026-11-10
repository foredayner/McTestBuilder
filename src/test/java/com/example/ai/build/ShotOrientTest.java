package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShotOrientTest {

    @Test
    void waveFacingIsReadInWordsKoreanToo() {
        assertEquals("horizontal", Spark.Shot.fromMap(Map.of("model", "crescent", "orient", "horizontal")).orient);
        assertEquals("vertical", Spark.Shot.fromMap(Map.of("model", "crescent", "orient", "세로")).orient);
        assertEquals("flat", Spark.Shot.fromMap(Map.of("orient", "flat")).orient);
        assertEquals("", Spark.Shot.fromMap(Map.of("model", "crescent")).orient, "old designs keep lying flat");
        assertThrows(IllegalArgumentException.class, () -> Spark.Shot.fromMap(Map.of("orient", "sideways")));
        assertTrue(Spark.PLANES.contains("vertical"), "effects can sweep up and down");
    }
}
