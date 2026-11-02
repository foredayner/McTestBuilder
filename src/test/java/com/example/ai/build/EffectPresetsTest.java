package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EffectPresetsTest {

    @Test
    void everyPresetBuildsWithinTheLimits() {
        for (var e : EffectPresets.all().entrySet()) {
            List<Spark> layers = EffectPresets.build(e.getKey(), null);
            assertFalse(layers.isEmpty(), e.getKey());
            int max = switch (e.getValue().when()) {
                case "attack" -> Spark.MAX_ATTACK;
                case "idle" -> Spark.MAX_IDLE;
                default -> 1;
            };
            assertTrue(layers.size() <= max, e.getKey() + " has " + layers.size() + " layers");
            assertTrue(List.of("attack", "idle", "trail").contains(e.getValue().when()), e.getKey());
        }
        assertTrue(EffectPresets.all().size() >= 18, "a good choice: " + EffectPresets.all().size());
    }

    @Test
    void presetsAreLayeredAndCanBeChanged() {
        List<Spark> fire = EffectPresets.build("fire_slash", null);
        assertEquals("swipe", fire.get(0).form);
        assertTrue(fire.stream().anyMatch(s -> s.delay > 0), "layers start one after another");
        List<Spark> pink = EffectPresets.build("frost", Map.of("colors", List.of("#ffffff", "#ffb0f0", "#c040c0"), "scale", 2.0,
            "sound", ""));
        assertEquals(0xFFB0F0, pink.get(0).colors[1], "the colours are replaced");
        assertEquals(EffectPresets.build("frost", null).get(0).size * 2, pink.get(0).size, 1e-9, "sizes are scaled");
        assertTrue(pink.stream().allMatch(s -> s.sound.isEmpty()), "'' takes the sound away");
        List<Spark> loud = EffectPresets.build("ground_smash", Map.of("shake", 0.2));
        assertEquals(0.2, loud.get(0).shake, 1e-9);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> EffectPresets.build("nuke", null));
        assertTrue(e.getMessage().contains("fire_slash"), "the error lists the presets");
        e = assertThrows(IllegalArgumentException.class, () -> EffectPresets.build("holy", Map.of("glitter", 1)));
        assertTrue(e.getMessage().contains("unknown preset change"));
        assertTrue(EffectPresets.catalogue().contains("fire_slash (attack)"));
    }

    @Test
    void layersMayWaitAndShake() {
        Spark s = Spark.fromMap(Map.of("delay", 99, "shake", 5));
        assertEquals(40, s.delay);
        assertEquals(1, s.shake, 1e-9);
    }
}
