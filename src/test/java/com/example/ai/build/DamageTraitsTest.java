package com.example.ai.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageTraitsTest {

    @Test
    void theOrderWordsSwitchTheTraitsOnAndLeaveTheRestForTheAi() {
        String line = "불꽃 베기, 데미지 표시, 랜덤 데미지";
        assertTrue(DamageTraits.numbers(line));
        assertTrue(DamageTraits.random(line));
        assertEquals("불꽃 베기", DamageTraits.rest(line));
        assertEquals("", DamageTraits.rest("랜덤 데미지와 데미지 표시"));
        assertTrue(DamageTraits.numbers("damage numbers") && DamageTraits.random("Random damage"));
        assertFalse(DamageTraits.numbers("푸른 번개") || DamageTraits.random("푸른 번개"));
        assertEquals("푸른 번개", DamageTraits.rest("푸른 번개"));
    }

    @Test
    void theRollScalesFromNothingToAll() {
        assertEquals(0f, DamageTraits.scale(0));
        assertEquals(1f, DamageTraits.scale(9));
        assertEquals(4 / 9f, DamageTraits.scale(4), 1e-6);
    }

    @Test
    void theTraitsAreKeptWithTheEffects() {
        Spark.Effects fx = new Spark.Effects();
        assertTrue(fx.isEmpty());
        fx.randomDamage = true;
        assertFalse(fx.isEmpty(), "a weapon with only random damage still keeps its effects");
        Spark.Effects back = Spark.Effects.fromJson(fx.toJson());
        assertTrue(back.randomDamage && !back.damageNumbers);
    }
}
