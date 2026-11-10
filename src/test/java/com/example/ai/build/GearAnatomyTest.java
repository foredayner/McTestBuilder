package com.example.ai.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GearAnatomyTest {

    @Test
    void theRequestPicksWhatItNames() {
        assertTrue(GearAnatomy.forRequest("검은 카타나, 붉은 손잡이").get(0).startsWith("katana"));
        assertTrue(GearAnatomy.forRequest("청룡언월도").get(0).startsWith("guandao"));
        assertTrue(GearAnatomy.forRequest("a frost rapier").get(0).startsWith("rapier"));
        assertFalse(GearAnatomy.forRequest("창을 들고").isEmpty(), "창 with a particle is a spear");
        assertTrue(GearAnatomy.forRequest("창문 모양 방패").isEmpty(), "창문 is not a spear");
        assertTrue(GearAnatomy.forRequest("낫다").isEmpty());
        assertTrue(GearAnatomy.forRequest("").isEmpty());
        assertTrue(GearAnatomy.forRequest("낫, 철퇴, 삼지창, 우산, 깃발").size() <= 4, "at most four guides");
    }
}
