package com.example.ai.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PixelArtTest {

    private static final Map<String, String> P = Map.of("A", "#ff0000", "B", "#0000ff");

    @Test
    void picturesAreCheckedWithReasons() {
        IllegalArgumentException uneven = assertThrows(IllegalArgumentException.class,
            () -> PixelArt.sprite(P, List.of("AA", "A"), 0.0625));
        assertTrue(uneven.getMessage().contains("same length"), uneven.getMessage());
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> PixelArt.sprite(P, List.of("AZ"), 0.0625));
        assertTrue(unknown.getMessage().contains("'Z'"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PixelArt.sprite(Map.of("A", "#ff00"), List.of("A"), 0.0625));
        assertThrows(IllegalArgumentException.class, () -> PixelArt.box(P, 0, 0, 0, 1, 1, 1, Map.of("side", List.of("A"))));
    }

    @Test
    void sameColoursMergeIntoFewPieces() {
        assertEquals(1, PixelArt.sprite(P, List.of("AAA", "AAA", "AAA"), 0.0625).size(), "a full square is one piece");
        assertEquals(0, PixelArt.sprite(P, List.of("...", ". ."), 0.0625).size(), "empty pixels make nothing");
        assertEquals(2, PixelArt.sprite(P, List.of("AB", "AB"), 0.0625).size(), "two columns");
    }

    @Test
    void spriteStandsOnItsBottomCentreFacingTheViewer() {
        List<PixelArt.Piece> pieces = PixelArt.sprite(P, List.of("AB"), 1);
        PixelArt.Piece a = pieces.stream().filter(p -> p.material().equals("#ff0000")).findFirst().orElseThrow();
        PixelArt.Piece b = pieces.stream().filter(p -> p.material().equals("#0000ff")).findFirst().orElseThrow();
        assertEquals(-0.5, a.cx(), 1e-9, "the left column is at -X");
        assertEquals(0.5, b.cx(), 1e-9);
        assertEquals(0.5, a.cy(), 1e-9, "it stands on y = 0");
        assertEquals(1, a.sz(), 1e-9, "one pixel thick");
        List<PixelArt.Piece> tall = PixelArt.sprite(P, List.of("A", "B"), 1);
        assertEquals("#ff0000", tall.stream().max((p, q) -> Double.compare(p.cy(), q.cy())).orElseThrow().material(), "the top row is on top");
    }

    @Test
    void boxFacesAreSeenFromOutside() {
        // A box from (0,0,0) to (2,1,2).
        PixelArt.Piece front = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("front", List.of("A."))));
        assertTrue(front.cx() < 1 && front.cz() < 0, "front: on -Z, left column at -X");
        PixelArt.Piece back = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("back", List.of("A."))));
        assertTrue(back.cx() > 1 && back.cz() > 2, "back: on +Z, left column at +X");
        PixelArt.Piece left = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("left", List.of("A."))));
        assertTrue(left.cx() < 0 && left.cz() > 1, "left: on -X, left column at the back");
        PixelArt.Piece right = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("right", List.of("A."))));
        assertTrue(right.cx() > 2 && right.cz() < 1, "right: on +X, left column at the front");
        PixelArt.Piece top = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("top", List.of("..", "A."))));
        assertTrue(top.cy() > 1 && top.cz() < 1 && top.cx() < 1, "top: the bottom row is the front, left column -X");
        PixelArt.Piece up = first(PixelArt.box(P, 0, 0, 0, 2, 1, 2, Map.of("front", List.of("A", "."))));
        assertTrue(up.cy() > 0.5, "the top row is up");
        assertEquals(PixelArt.FACE_THICKNESS, front.sz(), 1e-12, "faces are thin");
    }

    @Test
    void turnsLikeThePieces() {
        double[] v = PixelArt.turn(new double[] { 0, 0, -1 }, 90, 0, 0);
        assertEquals(-1, v[0], 1e-9, "yaw 90: a picture facing -Z faces -X");
        assertEquals(0, v[2], 1e-9);
        double[] up = PixelArt.turn(new double[] { 0, 1, 0 }, 0, 0, 90);
        assertEquals(-1, up[0], 1e-9, "roll 90 tips +Y over to -X");
    }

    private static PixelArt.Piece first(List<PixelArt.Piece> pieces) {
        assertEquals(1, pieces.size());
        return pieces.get(0);
    }
}
