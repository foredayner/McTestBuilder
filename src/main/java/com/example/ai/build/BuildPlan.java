package com.example.ai.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a build script placed, recorded instead of executed: every /setblock and /fill, in
 * coordinates relative to the player's feet, plus the direction the player faced.
 *
 * <p>Recording first lets us catch script errors before anything is placed, run the exact same
 * blocks after approval, and keep the structure for the history tab (preview and rebuild).
 */
public final class BuildPlan {

    public static final List<String> FACINGS = List.of("north", "east", "south", "west");

    public static final class Op {
        public String kind;   // "set" or "fill"
        public int x1;
        public int y1;
        public int z1;
        public int x2;
        public int y2;
        public int z2;
        public String block;  // e.g. minecraft:oak_stairs[facing=north]
        public String mode;   // replace, outline, hollow, keep, destroy

        public Op() {
        }

        public Op(String kind, int x1, int y1, int z1, int x2, int y2, int z2, String block, String mode) {
            this.kind = kind;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.block = block;
            this.mode = mode;
        }

        public boolean isAir() {
            return block != null && (block.equals("minecraft:air") || block.startsWith("minecraft:air[")
                || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air"));
        }
    }

    /**
     * A piece of a small object, drawn by a block_display entity: a block scaled to (sx, sy, sz)
     * blocks, centred at (x, y, z) relative to the feet block's corner, turned by yaw/pitch/roll
     * degrees around its centre.
     */
    public static final class Part {
        public String block;
        public double x;
        public double y;
        public double z;
        public double sx;
        public double sy;
        public double sz;
        public double yaw;
        public double pitch;
        public double roll;

        public Part() {
        }

        public Part(String block, double x, double y, double z, double sx, double sy, double sz, double yaw, double pitch, double roll) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }
    }

    public static final int MAX_PARTS = 400;
    public static final double MAX_PART_SIZE = 16;
    public static final double MAX_PART_OFFSET = 64;

    public List<Op> ops = new ArrayList<>();
    public List<Part> parts = new ArrayList<>();
    public String facing = "north";

    public boolean isEmpty() {
        return (ops == null || ops.isEmpty()) && (parts == null || parts.isEmpty());
    }

    public boolean isObject() {
        return parts != null && !parts.isEmpty();
    }

    /** "NORTH", "North (-Z)", null... -> "north" | "east" | "south" | "west". */
    public static String normalizeFacing(String value) {
        if (value == null) {
            return "north";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (String f : FACINGS) {
            if (lower.startsWith(f)) {
                return f;
            }
        }
        return "north";
    }

    /** Quarter turns clockwise (seen from above) to go from one facing to another. */
    public static int turnsBetween(String from, String to) {
        return Math.floorMod(FACINGS.indexOf(normalizeFacing(to)) - FACINGS.indexOf(normalizeFacing(from)), 4);
    }

    /** Rotate (x, z) clockwise around the origin: north (0,-1) -> east (1,0). */
    public static int[] rotate(int x, int z, int turns) {
        int rx = x;
        int rz = z;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int t = rx;
            rx = -rz;
            rz = t;
        }
        return new int[] { rx, rz };
    }

    /** [minX, minY, minZ, maxX, maxY, maxZ] of everything that is not air (all ops if only air). */
    public int[] bounds() {
        int[] b = null;
        for (int pass = 0; pass < 2 && b == null; pass++) {
            for (Op op : ops) {
                if (pass == 0 && op.isAir()) {
                    continue;
                }
                int[] o = {
                    Math.min(op.x1, op.x2), Math.min(op.y1, op.y2), Math.min(op.z1, op.z2),
                    Math.max(op.x1, op.x2), Math.max(op.y1, op.y2), Math.max(op.z1, op.z2)
                };
                if (b == null) {
                    b = o;
                } else {
                    for (int i = 0; i < 3; i++) {
                        b[i] = Math.min(b[i], o[i]);
                        b[i + 3] = Math.max(b[i + 3], o[i + 3]);
                    }
                }
            }
        }
        if (parts != null) {
            for (Part p : parts) {
                double r = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
                int[] o = {
                    (int) Math.floor(p.x - r), (int) Math.floor(p.y - r), (int) Math.floor(p.z - r),
                    (int) Math.ceil(p.x + r) - 1, (int) Math.ceil(p.y + r) - 1, (int) Math.ceil(p.z + r) - 1
                };
                if (b == null) {
                    b = o;
                } else {
                    for (int i = 0; i < 3; i++) {
                        b[i] = Math.min(b[i], o[i]);
                        b[i + 3] = Math.max(b[i + 3], o[i + 3]);
                    }
                }
            }
        }
        return b == null ? new int[6] : b;
    }

    /** Things vanilla commands would reject or that are clearly wrong, for the AI to fix. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        int bigFills = 0;
        int far = 0;
        for (Op op : ops) {
            long volume = (long) (Math.abs(op.x2 - op.x1) + 1) * (Math.abs(op.y2 - op.y1) + 1) * (Math.abs(op.z2 - op.z1) + 1);
            if ("fill".equals(op.kind) && volume > 32768 && bigFills++ < 3) {
                problems.add("a fill of " + volume + " blocks from (" + op.x1 + "," + op.y1 + "," + op.z1 + ") to ("
                    + op.x2 + "," + op.y2 + "," + op.z2 + ") is over Minecraft's 32768 limit; split it into smaller parts.");
            }
            for (int v : new int[] { op.x1, op.y1, op.z1, op.x2, op.y2, op.z2 }) {
                if (Math.abs(v) > 256) {
                    if (far++ == 0) {
                        problems.add("blocks are placed " + Math.abs(v) + " blocks from the player; keep the build within 256.");
                    }
                    break;
                }
            }
        }
        if (parts != null) {
            if (parts.size() > MAX_PARTS) {
                problems.add("the object has " + parts.size() + " parts; use at most " + MAX_PARTS + " (merge small pieces into bigger boxes).");
            }
            int bad = 0;
            for (Part p : parts) {
                if (Math.max(p.sx, Math.max(p.sy, p.sz)) > MAX_PART_SIZE || Math.min(p.sx, Math.min(p.sy, p.sz)) <= 0) {
                    if (bad++ == 0) {
                        problems.add("a part of " + p.block + " has size " + p.sx + " x " + p.sy + " x " + p.sz
                            + "; each size must be above 0 and at most " + (int) MAX_PART_SIZE + ".");
                    }
                }
                if (Math.abs(p.x) > MAX_PART_OFFSET || Math.abs(p.y) > MAX_PART_OFFSET || Math.abs(p.z) > MAX_PART_OFFSET) {
                    if (bad++ == 0) {
                        problems.add("a part is placed too far away (" + p.x + ", " + p.y + ", " + p.z + "); keep objects within "
                            + (int) MAX_PART_OFFSET + " blocks of the player.");
                    }
                }
            }
        }
        return problems;
    }

    public String summary() {
        int[] b = bounds();
        return "Size: " + (b[3] - b[0] + 1) + " x " + (b[4] - b[1] + 1) + " x " + (b[5] - b[2] + 1)
            + " (W x H x L), " + (isObject() ? parts.size() + " part(s)" + (ops.isEmpty() ? "" : ", ") : "")
            + (ops.isEmpty() && isObject() ? "" : ops.size() + " command(s)");
    }
}
