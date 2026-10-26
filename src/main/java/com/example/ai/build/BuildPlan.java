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
        /** Animation group (index into the plan's groups, 1-based); 0 = does not move. */
        public int group;

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

    /** A looping movement of one group of parts (equipment only), around a pivot point. */
    public static final class Anim {
        public int group;
        /** spin, swing, bob, pulse or glow. */
        public String type;
        public double px;
        public double py;
        public double pz;
        /** x, y or z. */
        public String axis;
        /** spin: turns per second; swing / bob / pulse: cycles per second. */
        public double speed;
        /** swing: degrees; bob: blocks; pulse: fraction of the size. */
        public double amount;

        public Anim() {
        }

        public Anim(int group, String type, double px, double py, double pz, String axis, double speed, double amount) {
            this.group = group;
            this.type = type;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.axis = axis;
            this.speed = speed;
            this.amount = amount;
        }
    }

    /** A glow around equipment (drawn by the mod): colour and strength (0.2-1). */
    public static final class Aura {
        public int color;
        public double strength;

        public Aura() {
        }

        public Aura(int color, double strength) {
            this.color = color;
            this.strength = strength;
        }
    }

    public static final int MAX_PARTS = 400;
    /** Bows: the group of the pieces that make the arrow's shape. */
    public static final int ARROW_GROUP = 999;
    public static final double MAX_PART_SIZE = 16;
    public static final double MAX_PART_OFFSET = 64;

    public List<Op> ops = new ArrayList<>();
    public List<Part> parts = new ArrayList<>();
    public String facing = "north";
    /** Equipment: movements of part groups (only when the order asked for an effect). */
    public List<Anim> anims = new ArrayList<>();
    /** Equipment: colour-dot effects on attack, while held or worn, and behind arrows. */
    public Spark.Effects fx = new Spark.Effects();
    /** Equipment: glow around the model while held or worn (null = none). */
    public Aura aura;
    /** Equipment the order asked to be big (ai.oversize()): it may go past the slot's usual size. */
    public boolean oversize;
    /** Named spots the script marked with ai.mark (e.g. where a clue goes), in the design frame. */
    public List<Mark> marks = new ArrayList<>();

    public static final class Mark {
        public String name;
        public int x;
        public int y;
        public int z;

        public Mark() {
        }

        public Mark(String name, int x, int y, int z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Sword: attack motion chosen in the order ("" = the normal swing). */
    public String motion = "";

    public boolean isEmpty() {
        return (ops == null || ops.isEmpty()) && (parts == null || parts.isEmpty());
    }

    public boolean isObject() {
        return parts != null && !parts.isEmpty();
    }

    /**
     * Objects are designed in one fixed frame (the player looks toward +Z, the object's front
     * faces back at them), whichever way the player really looked; placing turns them.
     */
    public void useObjectFrame() {
        if (isObject() && (ops == null || ops.isEmpty())) {
            facing = "south";
        }
    }

    /** Allowed sizes when placing a saved object bigger or smaller. */
    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 8;

    /**
     * A copy of an object scaled by {@code s}: every part's position and size. It grows from the
     * middle of its bottom edge nearest the player, so it keeps its gap in front of the player
     * instead of swallowing them. Rotations stay, so the front still faces the player.
     */
    public BuildPlan scaled(double s) {
        BuildPlan out = new BuildPlan();
        out.facing = facing;
        out.ops = new ArrayList<>(ops);
        double[] b = partBounds();
        double px = (b[0] + b[3]) / 2;
        double pz = (b[2] + b[5]) / 2;
        double py = b[1];
        switch (normalizeFacing(facing)) {
            case "south" -> pz = b[2];
            case "east" -> px = b[0];
            case "west" -> px = b[3];
            default -> pz = b[5]; // facing north: the near side is the south edge
        }
        for (Part p : parts) {
            out.parts.add(new Part(p.block,
                px + (p.x - px) * s, py + (p.y - py) * s, pz + (p.z - pz) * s,
                p.sx * s, p.sy * s, p.sz * s, p.yaw, p.pitch, p.roll));
            out.parts.get(out.parts.size() - 1).group = p.group;
        }
        return out;
    }

    /** Exact box around the turned parts: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public double[] partBounds() {
        double[] b = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (Part p : parts) {
            org.joml.Quaternionf q = new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(p.yaw))
                .rotateX((float) Math.toRadians(p.pitch))
                .rotateZ((float) Math.toRadians(p.roll));
            org.joml.Matrix3f m = new org.joml.Matrix3f().rotation(q);
            double hx = p.sx / 2, hy = p.sy / 2, hz = p.sz / 2;
            double ex = Math.abs(m.m00()) * hx + Math.abs(m.m10()) * hy + Math.abs(m.m20()) * hz;
            double ey = Math.abs(m.m01()) * hx + Math.abs(m.m11()) * hy + Math.abs(m.m21()) * hz;
            double ez = Math.abs(m.m02()) * hx + Math.abs(m.m12()) * hy + Math.abs(m.m22()) * hz;
            b[0] = Math.min(b[0], p.x - ex);
            b[1] = Math.min(b[1], p.y - ey);
            b[2] = Math.min(b[2], p.z - ez);
            b[3] = Math.max(b[3], p.x + ex);
            b[4] = Math.max(b[4], p.y + ey);
            b[5] = Math.max(b[5], p.z + ez);
        }
        return parts.isEmpty() ? new double[6] : b;
    }

    /** A copy of an object moved by (dx, dy, dz). */
    public BuildPlan translated(double dx, double dy, double dz) {
        BuildPlan out = new BuildPlan();
        out.facing = facing;
        out.ops = new ArrayList<>(ops);
        for (Part p : parts) {
            out.parts.add(new Part(p.block, p.x + dx, p.y + dy, p.z + dz, p.sx, p.sy, p.sz, p.yaw, p.pitch, p.roll));
            out.parts.get(out.parts.size() - 1).group = p.group;
        }
        return out;
    }

    /** rotate() for decimals: (x, z) turned clockwise seen from above. */
    public static double[] rotate(double x, double z, int turns) {
        double rx = x;
        double rz = z;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            double t = rx;
            rx = -rz;
            rz = t;
        }
        return new double[] { rx, rz };
    }

    private static double volume(double[] b) {
        return Math.max(0.05, b[3] - b[0]) * Math.max(0.05, b[4] - b[1]) * Math.max(0.05, b[5] - b[2]);
    }

    private static double[] union(double[] a, double[] b) {
        return new double[] {
            Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[3], b[3]), Math.max(a[4], b[4]), Math.max(a[5], b[5])
        };
    }

    /**
     * Merge boxes {minX, minY, minZ, maxX, maxY, maxZ} until at most {@code max} are left,
     * always joining the pair that adds the least empty space, so the result keeps the shape.
     */
    public static List<double[]> mergeBoxes(List<double[]> input, int max) {
        List<double[]> boxes = new ArrayList<>();
        for (double[] b : input) {
            boxes.add(b.clone());
        }
        while (boxes.size() > Math.max(1, max)) {
            int bi = 0;
            int bj = 1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < boxes.size(); i++) {
                double vi = volume(boxes.get(i));
                for (int j = i + 1; j < boxes.size(); j++) {
                    double cost = volume(union(boxes.get(i), boxes.get(j))) - vi - volume(boxes.get(j));
                    if (cost < best) {
                        best = cost;
                        bi = i;
                        bj = j;
                    }
                }
            }
            double[] merged = union(boxes.get(bi), boxes.get(bj));
            boxes.remove(bj);
            boxes.set(bi, merged);
        }
        return boxes;
    }

    /** Most square pieces one box is cut into. */
    public static final int MAX_SQUARES = 6;

    /**
     * Cut a box into square-footprint pieces along its long side (a hitbox entity is square):
     * each piece is {centreX, bottomY, centreZ, width, height}.
     */
    public static List<double[]> squares(double[] b) {
        double dx = Math.max(0.1, b[3] - b[0]);
        double dz = Math.max(0.1, b[5] - b[2]);
        double h = Math.max(0.05, b[4] - b[1]);
        double cx = (b[0] + b[3]) / 2;
        double cz = (b[2] + b[5]) / 2;
        double shortSide = Math.min(dx, dz);
        double longSide = Math.max(dx, dz);
        int n = Math.max(1, Math.min(MAX_SQUARES, (int) Math.ceil(longSide / shortSide - 1e-6)));
        double step = longSide / n;
        double width = Math.max(shortSide, step);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double along = -longSide / 2 + (i + 0.5) * step;
            out.add(dx >= dz
                ? new double[] { cx + along, b[1], cz, width, h }
                : new double[] { cx, b[1], cz + along, width, h });
        }
        return out;
    }

    /** Largest scale this object can take before a part grows past the size limit. */
    public double maxScale() {
        double biggest = 0;
        for (Part p : parts) {
            biggest = Math.max(biggest, Math.max(p.sx, Math.max(p.sy, p.sz)));
        }
        return biggest <= 0 ? MAX_SCALE : Math.min(MAX_SCALE, MAX_PART_SIZE / biggest);
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
