package com.example.ai.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the furniture presets a building put in (ai.prop) against its blocks, and furnishes an
 * empty inside: a prop of another period, one standing in a block, or one with no wall behind it
 * (or no ceiling over it) is taken out; when none are left and the period has basics ("fill"),
 * a few are set along inside walls, facing into the room and away from doors. Works on the
 * design (nothing is placed yet).
 */
public final class PropFitter {

    /** How many basics an empty inside gets at most. */
    static final int FILL = 3;

    private PropFitter() {
    }

    /**
     * @param era   the town's period ("1920s", "contemporary"), or null (any period, nothing added)
     * @param fill  furnish an empty inside with the period's basics
     * @param room  a room's inside {minX, minZ, maxX, maxZ}: its walls stand just outside it (not in
     *              the design), or null for a whole building
     * @return what was changed, for the log
     */
    public static List<String> fit(BuildPlan plan, String era, boolean fill, int[] room) {
        List<String> notes = new ArrayList<>();
        if (plan == null) {
            return notes;
        }
        Map<Long, String> blocks = blocks(plan);
        List<BuildPlan.Prop> kept = new ArrayList<>();
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (BuildPlan.Prop prop : plan.props) {
            PropLibrary.Def def = PropLibrary.get(prop.id);
            // era "": the period has no presets, so none belong.
            String why = def == null ? "unknown" : era != null && !def.era().equals(era) ? "another period"
                : problem(prop, def, blocks, room, taken);
            if (why != null) {
                notes.add("[가구] " + (def == null ? prop.id : def.name()) + " 뺐어요 (" + korean(why) + ")");
                continue;
            }
            taken.addAll(cells(prop, def));
            kept.add(prop);
        }
        plan.props = kept;
        if (fill && kept.isEmpty() && era != null && !era.isEmpty()) {
            int added = furnish(plan, era, blocks, room);
            if (added > 0) {
                notes.add("[가구] 빈 실내에 " + added + "개를 놓았어요");
            }
        }
        return notes;
    }

    private static String korean(String why) {
        return switch (why) {
            case "another period" -> "다른 시대";
            case "in a block" -> "블록과 겹침";
            case "no wall" -> "뒤에 벽 없음";
            case "no ceiling" -> "위에 천장 없음";
            case "on another" -> "다른 가구와 겹침";
            default -> "모르는 가구";
        };
    }

    /** Why a prop cannot stand where it is, or null. */
    static String problem(BuildPlan.Prop prop, PropLibrary.Def def, Map<Long, String> blocks, int[] room, java.util.Set<Long> taken) {
        List<Long> cells = cells(prop, def);
        for (long c : cells) {
            String b = blocks.get(c);
            if (b != null && !soft(b)) {
                return "in a block";
            }
            if (taken.contains(c)) {
                return "on another";
            }
        }
        String f = BuildPlan.normalizeFacing(prop.facing);
        int y = (int) Math.floor(prop.y + 1e-6);
        if ("ceiling".equals(def.mount())) {
            int[] o = origin(prop);
            return solid(blocks, room, o[0], y + 1, o[1]) ? null : "no ceiling";
        }
        if (!"back".equals(def.align()) || "top".equals(def.mount())) {
            return null;
        }
        // The block behind each of its blocks (its front looks f, so its back is the other way) holds it up.
        int[] back = step(opposite(f));
        int[] o = origin(prop);
        int[] along = "north".equals(f) || "south".equals(f) ? new int[] { 1, 0 } : new int[] { 0, 1 };
        int h = "window".equals(def.mount()) ? 2 : 1;
        for (int i = 0; i < def.w(); i++) {
            for (int k = 0; k < h; k++) {
                if (!solid(blocks, room, o[0] + along[0] * i + back[0], y + k, o[1] + along[1] * i + back[1])) {
                    return "no wall";
                }
            }
        }
        return null;
    }

    /** The prop's blocks: its width along the wall from (x, z), as tall as it stands (up to 3), one deep. */
    static List<Long> cells(BuildPlan.Prop prop, PropLibrary.Def def) {
        List<Long> out = new ArrayList<>();
        String f = BuildPlan.normalizeFacing(prop.facing);
        int[] o = origin(prop);
        int[] along = "north".equals(f) || "south".equals(f) ? new int[] { 1, 0 } : new int[] { 0, 1 };
        int y = (int) Math.floor(prop.y + 1e-6);
        int tall;
        if ("ceiling".equals(def.mount())) {
            tall = 1;
        } else {
            double[] b = PropLibrary.plan(def).partBounds();
            tall = Math.max(1, Math.min(3, (int) Math.ceil(b[4] - Math.max(0, b[1]) - 0.05 + (prop.y - y))));
        }
        for (int i = 0; i < def.w(); i++) {
            for (int k = 0; k < tall; k++) {
                if ("wall".equals(def.mount()) && k == 0 && tall > 1) {
                    continue; // a wall prop hangs above the floor: the block it is "in" may hold a table
                }
                out.add(key(o[0] + along[0] * i, y + k, o[1] + along[1] * i));
            }
        }
        return out;
    }

    private static int[] origin(BuildPlan.Prop prop) {
        return new int[] { (int) Math.floor(prop.x), (int) Math.floor(prop.z) };
    }

    /** Thin things a prop may stand in or over (a rug under a sofa). */
    private static boolean soft(String block) {
        return block.contains("carpet") || block.contains("pressure_plate") || block.contains("rail") || block.contains("snow[")
            || block.endsWith(":snow") || block.contains("moss_carpet");
    }

    private static boolean solid(Map<Long, String> blocks, int[] room, int x, int y, int z) {
        if (room != null && (x < room[0] || x > room[2] || z < room[1] || z > room[3])) {
            return true; // a room's walls stand just outside it
        }
        String b = blocks.get(key(x, y, z));
        return b != null && !soft(b) && !b.contains("door");
    }

    static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    static int[] step(String f) {
        return switch (f) {
            case "north" -> new int[] { 0, -1 };
            case "south" -> new int[] { 0, 1 };
            case "east" -> new int[] { 1, 0 };
            default -> new int[] { -1, 0 };
        };
    }

    /**
     * The period's basics along inside walls of the lowest floor that has room: air with air above,
     * a floor under it, a wall behind, open floor in front, inside (blocks on all four sides
     * further out), not next to a door; spaced apart.
     */
    static int furnish(BuildPlan plan, String era, Map<Long, String> blocks, int[] room) {
        List<PropLibrary.Def> basics = new ArrayList<>();
        for (PropLibrary.Def d : PropLibrary.all(era)) {
            if (d.fill() && d.w() == 1 && "floor".equals(d.mount())) {
                basics.add(d);
            }
        }
        if (basics.isEmpty() || blocks.isEmpty()) {
            return 0;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : blocks.keySet()) {
            minX = Math.min(minX, kx(k)); maxX = Math.max(maxX, kx(k));
            minY = Math.min(minY, ky(k)); maxY = Math.max(maxY, ky(k));
            minZ = Math.min(minZ, kz(k)); maxZ = Math.max(maxZ, kz(k));
        }
        if (room != null) {
            minX = room[0]; maxX = room[2]; minZ = room[1]; maxZ = room[3];
        }
        String[] facings = { "north", "south", "east", "west" };
        for (int y = Math.max(minY + 1, 0); y <= maxY; y++) {
            List<int[]> spots = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (blocks.containsKey(key(x, y, z)) || blocks.containsKey(key(x, y + 1, z)) || !solid(blocks, null, x, y - 1, z)) {
                        continue;
                    }
                    if (room == null && !enclosed(blocks, x, y, z, minX, maxX, minZ, maxZ)) {
                        continue;
                    }
                    if (nearDoor(blocks, x, y, z)) {
                        continue;
                    }
                    for (int f = 0; f < 4; f++) {
                        int[] back = step(opposite(facings[f]));
                        int[] front = step(facings[f]);
                        if (solid(blocks, room, x + back[0], y, z + back[1]) && !blocks.containsKey(key(x + front[0], y, z + front[1]))
                            && !blocks.containsKey(key(x + front[0], y + 1, z + front[1]))) {
                            spots.add(new int[] { x, z, f });
                            break;
                        }
                    }
                }
            }
            if (spots.size() < 2) {
                continue;
            }
            // Spread out: take spots at least 3 blocks from those taken.
            List<int[]> chosen = new ArrayList<>();
            for (int[] s : spots) {
                boolean far = true;
                for (int[] c : chosen) {
                    far &= Math.abs(c[0] - s[0]) + Math.abs(c[1] - s[1]) >= 3;
                }
                if (far) {
                    chosen.add(s);
                }
                if (chosen.size() >= Math.min(FILL, basics.size())) {
                    break;
                }
            }
            for (int i = 0; i < chosen.size(); i++) {
                int[] s = chosen.get(i);
                plan.props.add(new BuildPlan.Prop(basics.get(i % basics.size()).id(), s[0], y, s[1], facings[s[2]]));
            }
            return chosen.size();
        }
        return 0;
    }

    /** Blocks further out on all four sides, on this floor: inside the building, not in the yard. */
    private static boolean enclosed(Map<Long, String> blocks, int x, int y, int z, int minX, int maxX, int minZ, int maxZ) {
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            boolean hit = false;
            for (int i = 1; i <= Math.max(maxX - minX, maxZ - minZ) + 1 && !hit; i++) {
                int cx = x + d[0] * i, cz = z + d[1] * i;
                if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) {
                    break;
                }
                hit = blocks.containsKey(key(cx, y, cz)) || blocks.containsKey(key(cx, y + 1, cz));
            }
            if (!hit) {
                return false;
            }
        }
        // And a ceiling somewhere above.
        for (int k = 2; k <= 6; k++) {
            if (blocks.containsKey(key(x, y + k, z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearDoor(Map<Long, String> blocks, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String b = blocks.get(key(x + dx, y, z + dz));
                if (b != null && b.contains("door") && !b.contains("trapdoor")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The blocks a design leaves (its commands in order; air takes blocks away), in its own frame. */
    static Map<Long, String> blocks(BuildPlan plan) {
        Map<Long, String> out = new HashMap<>();
        for (BuildPlan.Op op : plan.ops) {
            int x0 = Math.min(op.x1, op.x2), x1 = Math.max(op.x1, op.x2);
            int y0 = Math.min(op.y1, op.y2), y1 = Math.max(op.y1, op.y2);
            int z0 = Math.min(op.z1, op.z2), z1 = Math.max(op.z1, op.z2);
            long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (volume > 400_000) {
                continue; // a huge clearing of the ground: not part of the building's shape
            }
            boolean air = op.isAir();
            String mode = "set".equals(op.kind) ? "replace" : op.mode;
            boolean shellOnly = "outline".equals(mode) || "hollow".equals(mode);
            String block = op.block == null ? "" : op.block;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                        long k = key(x, y, z);
                        if (!shell && shellOnly) {
                            if ("hollow".equals(mode)) {
                                out.remove(k);
                            }
                            continue;
                        }
                        if ("keep".equals(mode) && out.containsKey(k)) {
                            continue;
                        }
                        if (air) {
                            out.remove(k);
                        } else {
                            out.put(k, block);
                        }
                    }
                }
            }
        }
        // Doors and beds are written as one half; the other half counts too.
        for (Map.Entry<Long, String> e : new ArrayList<>(out.entrySet())) {
            String b = e.getValue();
            if (b.contains("_door") && !b.contains("half=upper")) {
                long up = key(kx(e.getKey()), ky(e.getKey()) + 1, kz(e.getKey()));
                out.putIfAbsent(up, b);
            }
        }
        return out;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    static int kx(long k) { return (int) (k << 1 >> 43); }
    static int ky(long k) { return (int) (k << 22 >> 43); }
    static int kz(long k) { return (int) (k << 43 >> 43); }
}
