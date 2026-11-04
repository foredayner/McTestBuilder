package com.example.ai.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Building parts that are easy to get wrong block by block (pure: they only list the blocks):
 * a gable or hip roof of stairs with an overhang, a door, a window with a sill and shutters, a wall
 * of mixed blocks. Coordinates are the script's (relative to the build origin); "facing" is the
 * way the outside of a door or window looks.
 */
public final class BuildingHelpers {

    /** One block to set. */
    public record Cell(int x, int y, int z, String block) {
    }

    private BuildingHelpers() {
    }

    /** "spruce", "spruce_planks", "stone_bricks", "deepslate_tiles" -> the name its stairs and slabs start with. */
    public static String base(String material) {
        String m = material == null ? "oak" : material.trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("minecraft:")) {
            m = m.substring(10);
        }
        if (m.endsWith("_planks")) {
            return m.substring(0, m.length() - 7);
        }
        if (m.endsWith("_stairs")) {
            return m.substring(0, m.length() - 7);
        }
        if (m.endsWith("_slab")) {
            return m.substring(0, m.length() - 5);
        }
        if (m.equals("bricks")) {
            return "brick";
        }
        if (m.endsWith("bricks") || m.endsWith("tiles")) {
            return m.substring(0, m.length() - 1);
        }
        return m;
    }

    public static String stairs(String material) {
        return base(material) + "_stairs";
    }

    public static String slab(String material) {
        return base(material) + "_slab";
    }

    private static String stair(String material, String facing) {
        return stairs(material) + "[facing=" + facing + ",half=bottom]";
    }

    /**
     * A gable roof over walls from (x, z) to (x + w - 1, z + l - 1) whose tops are at y - 1: stairs
     * climbing from both sides to a ridge along {@code axis} ("x" or "z"), one block of overhang all
     * round, the triangles at the two gable ends filled with {@code gable} (null = none).
     */
    public static List<Cell> roofGable(int x, int y, int z, int w, int l, String material, String axis, String gable) {
        List<Cell> out = new ArrayList<>();
        boolean alongX = !"z".equalsIgnoreCase(axis == null ? "" : axis.trim());
        int a0 = (alongX ? z : x) - 1, a1 = (alongX ? z + l : x + w);   // across the ridge, with the overhang
        int b0 = (alongX ? x : z) - 1, b1 = (alongX ? x + w : z + l);   // along the ridge, with the overhang
        int wallA0 = alongX ? z : x, wallA1 = alongX ? z + l - 1 : x + w - 1;
        int[] ends = alongX ? new int[] { x, x + w - 1 } : new int[] { z, z + l - 1 };
        String low = alongX ? "south" : "east";   // stairs on the low-coordinate side climb toward +
        String high = alongX ? "north" : "west";
        for (int k = 0; a0 + k <= a1 - k; k++) {
            int lo = a0 + k, hi = a1 - k;
            for (int b = b0; b <= b1; b++) {
                if (lo == hi) {
                    out.add(cell(alongX, b, y + k, lo, slab(material) + "[type=bottom]"));
                } else {
                    out.add(cell(alongX, b, y + k, lo, stair(material, low)));
                    out.add(cell(alongX, b, y + k, hi, stair(material, high)));
                }
            }
            if (gable != null && !gable.isBlank()) {
                for (int end : ends) {
                    for (int a = Math.max(lo + 1, wallA0); a <= Math.min(hi - 1, wallA1); a++) {
                        out.add(cell(alongX, end, y + k, a, gable));
                    }
                }
            }
        }
        return out;
    }

    /** (along, y, across) to (x, y, z) for a ridge along x or z. */
    private static Cell cell(boolean alongX, int along, int y, int across, String block) {
        return alongX ? new Cell(along, y, across, block) : new Cell(across, y, along, block);
    }

    /** A hip roof: stairs climbing from all four sides (one block of overhang) to a ridge of slabs. */
    public static List<Cell> roofHip(int x, int y, int z, int w, int l, String material) {
        List<Cell> out = new ArrayList<>();
        int x0 = x - 1, x1 = x + w, z0 = z - 1, z1 = z + l;
        for (int k = 0; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
            int ax = x0 + k, bx = x1 - k, az = z0 + k, bz = z1 - k;
            if (ax == bx || az == bz) {
                for (int i = ax; i <= bx; i++) {
                    for (int j = az; j <= bz; j++) {
                        out.add(new Cell(i, y + k, j, slab(material) + "[type=bottom]"));
                    }
                }
                break;
            }
            for (int i = ax; i <= bx; i++) {
                out.add(new Cell(i, y + k, az, stair(material, "south")));
                out.add(new Cell(i, y + k, bz, stair(material, "north")));
            }
            for (int j = az + 1; j <= bz - 1; j++) {
                out.add(new Cell(ax, y + k, j, stair(material, "east")));
                out.add(new Cell(bx, y + k, j, stair(material, "west")));
            }
        }
        return out;
    }

    static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    static String facing(String facing) {
        String f = facing == null ? "" : facing.trim().toLowerCase(Locale.ROOT);
        if (!List.of("north", "south", "east", "west").contains(f)) {
            throw new IllegalArgumentException("facing must be north, south, east or west (the way the outside looks), not \"" + facing + "\"");
        }
        return f;
    }

    private static int[] out(String facing) {
        return switch (facing) {
            case "north" -> new int[] { 0, -1 };
            case "south" -> new int[] { 0, 1 };
            case "east" -> new int[] { 1, 0 };
            default -> new int[] { -1, 0 };
        };
    }

    /** A door in the wall block (x, y, z), its outside looking {@code facing}; the code completes the upper half. */
    public static List<Cell> door(int x, int y, int z, String facing, String material) {
        String f = facing(facing);
        String wood = material == null || material.isBlank() ? "oak" : base(material).replace("_door", "");
        return List.of(new Cell(x, y, z, wood + "_door[facing=" + opposite(f) + ",half=lower,hinge=left,open=false]"));
    }

    /**
     * A window w wide and h high in a wall, its bottom-left (lowest x or z) block at (x, y, z), the
     * outside looking {@code facing}: panes in the wall, a sill under it outside and open shutters
     * beside it outside, of {@code frame} (a wood, e.g. "spruce").
     */
    public static List<Cell> window(int x, int y, int z, int w, int h, String facing, String frame, String glass) {
        String f = facing(facing);
        int[] o = out(f);
        boolean alongX = o[0] == 0;
        String wood = frame == null || frame.isBlank() ? "spruce" : base(frame);
        String pane = glass == null || glass.isBlank() ? "glass_pane" : glass.trim();
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < w; i++) {
            int cx = alongX ? x + i : x, cz = alongX ? z : z + i;
            for (int j = 0; j < h; j++) {
                cells.add(new Cell(cx, y + j, cz, pane));
            }
            cells.add(new Cell(cx + o[0], y - 1, cz + o[1], slab(wood) + "[type=top]"));   // sill
        }
        String shutter = wood + "_trapdoor[facing=" + f + ",half=bottom,open=true]";
        for (int side : new int[] { -1, w }) {
            int cx = alongX ? x + side : x, cz = alongX ? z : z + side;
            for (int j = 0; j < h; j++) {
                cells.add(new Cell(cx + o[0], y + j, cz + o[1], shutter));
            }
        }
        return cells;
    }

    /**
     * A wall like placeWall (h high, l long from (x, y, z) along x or z) of several blocks mixed by
     * weight, e.g. {stone_bricks: 70, cracked_stone_bricks: 20, mossy_stone_bricks: 10}; the same
     * wall always gets the same mix.
     */
    public static List<Cell> wallMix(int x, int y, int z, int h, int l, String direction, Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("wallMix needs blocks with weights, e.g. ['stone_bricks': 70, 'cracked_stone_bricks': 30]");
        }
        String d = direction == null ? "x" : direction.trim().toLowerCase(Locale.ROOT);
        boolean alongX = d.equals("x") || d.equals("east") || d.equals("west");
        int total = 0;
        for (int v : weights.values()) {
            total += Math.max(0, v);
        }
        if (total <= 0) {
            throw new IllegalArgumentException("wallMix: the weights must add up to more than 0");
        }
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < l; i++) {
            for (int j = 0; j < h; j++) {
                int cx = alongX ? x + i : x, cz = alongX ? z : z + i, cy = y + j;
                int hash = (cx * 73856093) ^ (cy * 19349663) ^ (cz * 83492791);
                int pick = Math.floorMod(hash * 0x9E3779B1, total);
                String block = null;
                for (Map.Entry<String, Integer> e : weights.entrySet()) {
                    pick -= Math.max(0, e.getValue());
                    if (pick < 0) {
                        block = e.getKey();
                        break;
                    }
                }
                out.add(new Cell(cx, cy, cz, block));
            }
        }
        return out;
    }
}
