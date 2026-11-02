package com.example.ai.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaintTest {

    @Test
    void coloursAreReadAndStoredInOneForm() {
        assertEquals("#c0392b", Paint.parse("#C0392B"));
        assertEquals("#aabbcc", Paint.parse("#abc"));
        assertEquals("#88ccff/50", Paint.parse("#88ccff/50"));
        assertEquals("#88ccff/50", Paint.parse("#88ccff / 45%"));
        assertEquals("#88ccff/25", Paint.parse("#88ccff/10"));
        assertEquals("#88ccff/75", Paint.parse("#88ccff/80"));
        assertEquals("#88ccff", Paint.parse("#88ccff/95"));
        assertNull(Paint.parse("#88cc"));
        assertNull(Paint.parse("#gg0000"));
        assertNull(Paint.parse("red"));
        assertTrue(Paint.is(" #fff"));
        assertFalse(Paint.is("oak_planks"));
    }

    @Test
    void storedColoursGiveColourOpacityAndItem() {
        assertTrue(Paint.valid("#88ccff/50"));
        assertFalse(Paint.valid("#88ccff/40"));
        assertFalse(Paint.valid("minecraft:stone"));
        assertEquals(0x88ccff, Paint.rgb("#88ccff/50"));
        assertEquals(50, Paint.opacity("#88ccff/50"));
        assertEquals(100, Paint.opacity("#88ccff"));
        assertEquals(0xFF88CCFF, Paint.argb("#88ccff"));
        assertEquals(0x80, Paint.argb("#88ccff/50") >>> 24);
        assertEquals("minecraft-ai-build-assistant:paint_50", Paint.itemId("#88ccff/50"));
        assertEquals("minecraft-ai-build-assistant:paint", Paint.itemId("#88ccff"));
    }
}
