package com.example.ai.script;

import com.example.ai.Boot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptLinterTest {

    @BeforeAll
    static void boot() {
        Boot.minecraft();
    }

    private static String one(String code) {
        List<String> problems = ScriptLinter.lint(code);
        assertEquals(1, problems.size(), problems.toString());
        return problems.get(0);
    }

    @Test
    void goodScriptsPass() {
        assertEquals(List.of(), ScriptLinter.lint(
            "def x = ai.getForwardX(3)\n"
                + "for (int i = 0; i < 3; i++) { ai.placeBox(x, i, 0, 3, 1, 3, 'stone_bricks') }\n"
                + "ai.placeWall('oak_planks', 0, 0, 0, 3, 5, 'x')\n"
                + "ai.placeBlock(0, 3, 0, 'minecraft:oak_stairs[facing=north]')\n"
                + "ai.addPart('red_concrete', 0, 0, 0, 0.5, 0.25, 0.5, 45)\n"
                + "ai.addSphere('yellow_concrete', 1, 1, 1, 0.4)\n"
                + "println 'ai.notAFunction(1)'\n"));
    }

    @Test
    void catchesWrongArgumentCountWithLine() {
        String p = one("ai.placeBox(0,0,0,'stone')\n");
        assertTrue(p.startsWith("line 1: placeBox got 4"), p);
        assertTrue(one("\n\nai.placeWall(0,0,0,3,'stone','x')").startsWith("line 3:"));
    }

    @Test
    void catchesUnknownFunctionsAndBlocks() {
        assertTrue(one("ai.placeCube(0,0,0,'stone')").contains("does not exist"));
        assertTrue(one("ai.placeBlock(0,0,0,'oak_plank')").contains("not a block"));
        assertTrue(one("ai.placeBlock(0,0,0,'oak_stairs[facing=up]')").contains("invalid block states"));
    }

    @Test
    void tablesOfNumbersWorkButMethodDefinitionsDoNot() {
        assertEquals(List.of(), ScriptLinter.lint("int[][] rects = [[1, 2], [3, 4]]\n"
            + "for (int k = 0; k < rects.length; k++) { ai.placeBlock(rects[k][0], 0, rects[k][1], 'stone') }"));
        assertEquals(List.of(), ScriptLinter.lint("String[] walls = ['stone', 'bricks']\n"
            + "for (int k = 0; k < walls.length; k++) { ai.placeBlock(k, 0, 0, walls[k]) }"));
        assertTrue(one("def wall(int x) { ai.placeBlock(x, 0, 0, 'stone') }\nwall(1)").startsWith("Does not compile"));
    }

    @Test
    void classicForLoopsCompileButClosuresDoNot() {
        assertEquals(List.of(), ScriptLinter.lint("for (int i = 0; i < 3; i++) { ai.placeBlock(i, 0, 0, 'stone') }"));
        assertEquals(List.of(), ScriptLinter.lint("for (i in 0..2) { ai.placeBlock(i, 0, 0, 'stone') }"));
        assertTrue(one("3.times { ai.placeBlock(it, 0, 0, 'stone') }").startsWith("Does not compile"));
    }

    @Test
    void catchesWallsWithoutDirectionAndFarCoordinates() {
        assertTrue(one("ai.placeWall('stone', 0, 0, 0, 3, 5, 'diagonal')").contains("needs a direction"));
        assertTrue(one("ai.placeBox(0, 0, 900, 3, 3, 3, 'stone')").contains("900"));
    }

    @Test
    void catchesCompileErrorsAndForbiddenCode() {
        assertTrue(one("ai.placeBlock(0,0,0,'stone'").startsWith("Does not compile"));
        assertTrue(one("System.exit(0)").startsWith("Does not compile"));
    }
}
