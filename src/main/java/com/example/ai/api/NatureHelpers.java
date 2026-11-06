package com.example.ai.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Outdoor pieces that come out poorly block by block (pure: they only list the blocks): a tree
 * with a real trunk, branches and a full crown, a lumpy boulder of mixed stone, a bush, and a mound
 * of earth. Shapes are irregular but the same every time for the same arguments.
 */
public final class NatureHelpers {

    private NatureHelpers() {
    }

    /** The woods a tree can be of, and the shape each grows in. */
    public static final List<String> TREES = List.of("oak", "birch", "spruce", "pine", "dark_oak", "jungle", "acacia", "cherry",
        "mangrove", "willow", "dead");

    /** A hash in 0..99 for a block and a salt. */
    static int noise(int x, int y, int z, int salt) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return Math.floorMod(h, 100);
    }

    private static String wood(String kind) {
        return switch (kind) {
            case "pine" -> "spruce";
            case "willow" -> "oak";
            case "dead" -> "spruce";
            default -> kind;
        };
    }

    private static String leaves(String kind) {
        return switch (kind) {
            case "pine" -> "spruce_leaves";
            case "willow" -> "oak_leaves";
            case "cherry" -> "cherry_leaves";
            case "mangrove" -> "mangrove_leaves";
            default -> kind + "_leaves";
        } + "[persistent=true]";
    }

    private static String log(String kind, String axis) {
        return wood(kind) + "_log[axis=" + axis + "]";
    }

    /**
     * A tree standing on (x, y, z) (y = the first block above the ground) about {@code height}
     * high, its trunk {@code trunk} blocks thick (1-3; 0 = by its height). Broad trees get a trunk
     * that splits into 3-5 branches with a crown of overlapping leaf blobs (and roots when thick);
     * spruce and pine a straight trunk in rings of leaves; willow hanging leaves; dead bare branches.
     */
    public static List<Map.Entry<int[], String>> tree(int x, int y, int z, int height, String kindIn, int trunkIn) {
        String kind = kindIn == null ? "oak" : kindIn.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_log", "")
            .replace("_tree", "");
        if (!TREES.contains(kind)) {
            throw new IllegalArgumentException("tree: kind must be one of " + TREES + " (not '" + kindIn + "')");
        }
        int h = Math.max(4, Math.min(30, height));
        int t = trunkIn > 0 ? Math.min(3, trunkIn) : h >= 16 ? 3 : h >= 10 ? 2 : 1;
        Cells out = new Cells();
        boolean conifer = kind.equals("spruce") || kind.equals("pine");
        int top = conifer ? h - 1 : Math.max(3, (int) Math.round(h * 0.55));
        // The trunk (t x t, centred on x, z for odd t; toward +x/+z for even).
        int o = -(t - 1) / 2;
        for (int dy = 0; dy < top; dy++) {
            for (int a = 0; a < t; a++) {
                for (int b = 0; b < t; b++) {
                    // a thick trunk narrows: its corners stop part way up
                    boolean corner = t == 3 && (a != 1 && b != 1);
                    if (corner && dy > top / 3 + noise(x + a, dy, z + b, 1) % 3) {
                        continue;
                    }
                    out.log(x + o + a, y + dy, z + o + b, log(kind, "y"));
                }
            }
        }
        if (t >= 2) {
            // Roots: short runs of logs out from the foot.
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] d : dirs) {
                int len = 1 + noise(x + d[0], y, z + d[1], 2) % (t + 1);
                int sx = d[0] > 0 ? x + o + t - 1 : d[0] < 0 ? x + o : x + o + noise(x, y, z, 3 + d[1]) % t;
                int sz = d[1] > 0 ? z + o + t - 1 : d[1] < 0 ? z + o : z + o + noise(x, y, z, 5 + d[0]) % t;
                for (int i = 1; i <= len; i++) {
                    out.log(sx + d[0] * i, y, sz + d[1] * i, log(kind, d[0] != 0 ? "x" : "z"));
                }
            }
        }
        if (conifer) {
            // Rings of leaves, wide at the bottom, narrow at the top, with gaps between layers.
            int start = Math.max(2, h / 4);
            for (int dy = start; dy <= h; dy++) {
                double f = 1.0 - (dy - start) / (double) Math.max(1, h - start);
                double r = 0.6 + f * (h / 4.0 + t * 0.5);
                if ((dy - start) % 3 == 2 && dy < h - 2) {
                    r *= 0.6;
                }
                disc(out, x, y + dy, z, r, leaves(kind), 7);
            }
            out.leaf(x, y + h, z, leaves(kind));
            out.leaf(x, y + h + 1, z, leaves(kind));
            return out.list();
        }
        // Branches: 3-5 from the top of the trunk, out and up, each ending in a blob of leaves.
        int n = 3 + noise(x, y, z, 7) % 3;
        double spread = h * 0.35 + t;
        List<int[]> ends = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double ang = (i * 2 * Math.PI / n) + noise(x, y + i, z, 8) / 100.0;
            double len = spread * (0.6 + noise(x, y, z + i, 9) / 250.0);
            int bx = x + (int) Math.round(Math.cos(ang) * len);
            int bz = z + (int) Math.round(Math.sin(ang) * len);
            int by = y + top + (int) Math.round((h - top) * (0.35 + noise(x + i, y, z, 10) / 300.0));
            int sy = y + top - 1 - noise(x, y, z, 11 + i) % 2;
            line(out, x, sy, z, bx, by, bz, kind);
            ends.add(new int[] { bx, by, bz });
        }
        line(out, x, y + top - 1, z, x, y + h - 2, z, kind); // the leader up the middle
        ends.add(new int[] { x, y + h - 2, z });
        if (kind.equals("dead")) {
            // Bare: short twigs off each branch end.
            for (int[] e : ends) {
                out.log(e[0], e[1] + 1, e[2], log(kind, "y"));
                out.log(e[0] + (noise(e[0], e[1], e[2], 12) < 50 ? 1 : -1), e[1] + 1, e[2], log(kind, "x"));
            }
            return out.list();
        }
        double blob = Math.max(2.0, h * 0.18 + t * 0.6);
        for (int[] e : ends) {
            double rx = blob * (0.85 + noise(e[0], e[1], e[2], 13) / 300.0);
            double ry = blob * 0.65;
            ellipsoid(out, e[0], e[1] + 1, e[2], rx, ry, rx, leaves(kind), 11);
        }
        if (kind.equals("willow")) {
            // Leaves hanging in strands from the crown's underside.
            int r = (int) Math.ceil(spread + blob);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (noise(x + dx, y, z + dz, 14) >= 18) {
                        continue;
                    }
                    int low = out.lowestLeaf(x + dx, z + dz);
                    if (low == Integer.MAX_VALUE) {
                        continue;
                    }
                    int len = 2 + noise(x + dx, y, z + dz, 15) % Math.max(2, h / 3);
                    for (int i = 1; i <= len && low - i > y + 1; i++) {
                        out.leaf(x + dx, low - i, z + dz, leaves(kind));
                    }
                }
            }
        }
        return out.list();
    }

    /** A rough boulder of mixed stone filling about w x h x l from (x, y, z) up; {@code kind}: stone, mossy, granite, sandstone, dark. */
    public static List<Map.Entry<int[], String>> rock(int x, int y, int z, int w, int h, int l, String kindIn) {
        String kind = kindIn == null ? "stone" : kindIn.trim().toLowerCase(Locale.ROOT);
        String[] mix = switch (kind) {
            case "mossy" -> new String[] { "mossy_cobblestone", "mossy_cobblestone", "stone", "andesite", "moss_block", "cobblestone" };
            case "granite" -> new String[] { "granite", "granite", "stone", "andesite", "diorite", "cobblestone" };
            case "sandstone" -> new String[] { "sandstone", "sandstone", "smooth_sandstone", "red_sandstone", "cut_sandstone", "stone" };
            case "dark" -> new String[] { "deepslate", "cobbled_deepslate", "tuff", "basalt", "blackstone", "deepslate" };
            case "stone" -> new String[] { "stone", "stone", "andesite", "cobblestone", "tuff", "mossy_cobblestone" };
            default -> throw new IllegalArgumentException("rock: kind must be stone, mossy, granite, sandstone or dark (not '" + kindIn + "')");
        };
        Cells out = new Cells();
        double rx = Math.max(1, w) / 2.0, ry = Math.max(1, h), rz = Math.max(1, l) / 2.0;
        double cx = x + rx - 0.5, cz = z + rz - 0.5;
        for (int dx = 0; dx < Math.max(1, w); dx++) {
            for (int dz = 0; dz < Math.max(1, l); dz++) {
                for (int dy = 0; dy < Math.max(1, h); dy++) {
                    int bx = x + dx, by = y + dy, bz = z + dz;
                    double ex = (bx - cx) / rx, ey = dy / ry, ez = (bz - cz) / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    double jag = 0.75 + noise(bx, by, bz, 21) / 250.0;
                    if (d > jag) {
                        continue;
                    }
                    String block = mix[noise(bx, by, bz, 22) % mix.length];
                    if (kind.equals("mossy") && dy == h - 1 || kind.equals("mossy") && noise(bx, by + 1, bz, 23) < 10) {
                        block = "moss_block";
                    }
                    out.put(bx, by, bz, block);
                }
            }
        }
        return out.list();
    }

    /** A rounded bush of leaves about w x h x l from (x, y, z) up. */
    public static List<Map.Entry<int[], String>> bush(int x, int y, int z, int w, int h, int l, String leavesIn) {
        String leaves = leavesIn == null || leavesIn.isBlank() ? "oak_leaves" : leavesIn.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (!leaves.endsWith("_leaves") && !leaves.contains("azalea")) {
            leaves = leaves + "_leaves";
        }
        Cells out = new Cells();
        double rx = Math.max(1, w) / 2.0, ry = Math.max(1, h), rz = Math.max(1, l) / 2.0;
        ellipsoidBase(out, x + rx - 0.5, y, z + rz - 0.5, rx + 0.3, ry, rz + 0.3, leaves + "[persistent=true]", 31);
        return out.list();
    }

    /** A mound of earth about w x h x l from (x, y, z) up, its top of {@code top} (grass_block, moss_block, podzol...). */
    public static List<Map.Entry<int[], String>> mound(int x, int y, int z, int w, int h, int l, String topIn) {
        String top = topIn == null || topIn.isBlank() ? "grass_block" : topIn.trim();
        Cells out = new Cells();
        double rx = Math.max(1, w) / 2.0, rz = Math.max(1, l) / 2.0;
        double cx = x + rx - 0.5, cz = z + rz - 0.5;
        for (int dx = 0; dx < Math.max(1, w); dx++) {
            for (int dz = 0; dz < Math.max(1, l); dz++) {
                double ex = (x + dx - cx) / (rx + 0.3), ez = (z + dz - cz) / (rz + 0.3);
                double f = 1 - (ex * ex + ez * ez);
                if (f <= 0) {
                    continue;
                }
                int col = (int) Math.round(Math.max(1, h) * Math.sqrt(f));
                for (int dy = 0; dy < col; dy++) {
                    out.put(x + dx, y + dy, z + dz, dy == col - 1 ? top : "dirt");
                }
            }
        }
        return out.list();
    }

    private static void line(Cells out, int x1, int y1, int z1, int x2, int y2, int z2, String kind) {
        int dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        String axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? "y" : Math.abs(dx) >= Math.abs(dz) ? "x" : "z";
        for (int i = 0; i <= steps; i++) {
            int bx = x1 + (steps == 0 ? 0 : (int) Math.round(dx * i / (double) steps));
            int by = y1 + (steps == 0 ? 0 : (int) Math.round(dy * i / (double) steps));
            int bz = z1 + (steps == 0 ? 0 : (int) Math.round(dz * i / (double) steps));
            out.log(bx, by, bz, log(kind, axis));
        }
    }

    private static void disc(Cells out, int x, int y, int z, double r, String block, int salt) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r - 0.3 || d <= r + 0.2 && noise(x + dx, y, z + dz, salt) < 45) {
                    out.leaf(x + dx, y, z + dz, block);
                }
            }
        }
    }

    private static void ellipsoid(Cells out, int x, int y, int z, double rx, double ry, double rz, String block, int salt) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                    if (d <= 0.75 || d <= 1.05 && noise(x + dx, y + dy, z + dz, salt) < 55) {
                        out.leaf(x + dx, y + dy, z + dz, block);
                    }
                }
            }
        }
    }

    /** An ellipsoid's upper half on the ground (a bush). */
    private static void ellipsoidBase(Cells out, double cx, int y, double cz, double rx, double ry, double rz, String block, int salt) {
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx), z0 = (int) Math.floor(cz - rz), z1 = (int) Math.ceil(cz + rz);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int dy = 0; dy < (int) Math.ceil(ry); dy++) {
                    double ex = (bx - cx) / rx, ey = (dy + 0.5) / ry, ez = (bz - cz) / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    if (d <= 0.8 || d <= 1.1 && noise(bx, y + dy, bz, salt) < 50) {
                        out.put(bx, y + dy, bz, block);
                    }
                }
            }
        }
    }

    /** Blocks by position; logs win over leaves. */
    private static final class Cells {
        final Map<Long, Map.Entry<int[], String>> map = new LinkedHashMap<>();

        static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        void put(int x, int y, int z, String block) {
            map.put(key(x, y, z), Map.entry(new int[] { x, y, z }, block));
        }

        void log(int x, int y, int z, String block) {
            put(x, y, z, block);
        }

        void leaf(int x, int y, int z, String block) {
            map.putIfAbsent(key(x, y, z), Map.entry(new int[] { x, y, z }, block));
        }

        int lowestLeaf(int x, int z) {
            int low = Integer.MAX_VALUE;
            for (Map.Entry<int[], String> e : map.values()) {
                int[] p = e.getKey();
                if (p[0] == x && p[2] == z && e.getValue().contains("leaves")) {
                    low = Math.min(low, p[1]);
                }
            }
            return low;
        }

        List<Map.Entry<int[], String>> list() {
            return new ArrayList<>(map.values());
        }
    }
}
