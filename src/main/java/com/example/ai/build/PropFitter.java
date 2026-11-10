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
        // era may list several periods ("contemporary,contemporary_east"); "" = none belong.
        List<String> eras = era == null ? null : PropLibrary.eraList(era);
        Map<Long, String> blocks = blocks(plan);
        java.util.Set<Long> doors = doorZone(blocks);
        // Every prop's blocks, so a pot can stand on a table prop.
        java.util.Set<Long> under = new java.util.HashSet<>();
        for (BuildPlan.Prop prop : plan.props) {
            PropLibrary.Def def = PropLibrary.get(prop.id);
            if (def != null && !"top".equals(def.mount())) {
                under.addAll(cells(prop, def));
            }
        }
        List<BuildPlan.Prop> kept = new ArrayList<>();
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (BuildPlan.Prop prop : plan.props) {
            PropLibrary.Def def = PropLibrary.get(prop.id);
            String why = def == null ? "unknown" : eras != null && !eras.contains(def.era()) ? "another period"
                : problem(prop, def, blocks, room, taken, doors, under);
            if (why != null) {
                notes.add("[가구] " + (def == null ? prop.id : def.name()) + " 뺐어요 (" + korean(why) + ")");
                continue;
            }
            taken.addAll(cells(prop, def));
            kept.add(prop);
        }
        plan.props = kept;
        if (fill && eras != null && !eras.isEmpty()) {
            if (kept.isEmpty()) {
                int added = furnish(plan, eras, blocks, room, taken);
                if (added > 0) {
                    notes.add("[가구] 빈 실내에 " + added + "개를 놓았어요");
                }
            }
            // Props the period uses in most houses (a folding screen): one along an inside wall when missing.
            for (String e : eras) {
                for (PropLibrary.Def d : PropLibrary.all(e)) {
                    if (!d.often() || plan.props.stream().anyMatch(p -> p.id.equals(d.id()))) {
                        continue;
                    }
                    List<int[]> spots = spots(blocks, room, taken, d.w(), "floor".equals(d.mount()) && "back".equals(d.align()));
                    if (!spots.isEmpty()) {
                        int[] s = spots.get(spots.size() / 2);
                        BuildPlan.Prop p = new BuildPlan.Prop(d.id(), s[0], s[3], s[1], FACINGS[s[2]]);
                        plan.props.add(p);
                        taken.addAll(cells(p, d));
                        notes.add("[가구] " + d.name() + "을(를) 세웠어요");
                    }
                }
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
            case "by a door" -> "문 앞뒤";
            case "in the air" -> "공중에 뜸";
            case "no window" -> "뒤에 창문 없음";
            default -> "모르는 가구";
        };
    }

    /** Why a prop cannot stand where it is, or null. */
    static String problem(BuildPlan.Prop prop, PropLibrary.Def def, Map<Long, String> blocks, int[] room, java.util.Set<Long> taken) {
        return problem(prop, def, blocks, room, taken, java.util.Set.of(), java.util.Set.of());
    }

    static String problem(BuildPlan.Prop prop, PropLibrary.Def def, Map<Long, String> blocks, int[] room, java.util.Set<Long> taken,
                          java.util.Set<Long> doors, java.util.Set<Long> under) {
        List<Long> cells = cells(prop, def);
        for (long c : cells) {
            String b = blocks.get(c);
            if (b != null && !soft(b)) {
                return "in a block";
            }
            if (taken.contains(c)) {
                return "on another";
            }
            if (doors.contains(c)) {
                return "by a door";
            }
        }
        String f = BuildPlan.normalizeFacing(prop.facing);
        int y = (int) Math.floor(prop.y + 1e-6);
        int[] o0 = origin(prop);
        int[] along0 = "north".equals(f) || "south".equals(f) ? new int[] { 1, 0 } : new int[] { 0, 1 };
        String mount = def.mount();
        if ("floor".equals(mount) || "top".equals(mount)) {
            // Something under it: the floor (or, on top, a table of blocks or a table prop).
            int below = "top".equals(mount) ? (int) Math.floor(prop.y - 0.05) : y - 1;
            int held = 0;
            for (int i = 0; i < def.w(); i++) {
                int x = o0[0] + along0[0] * i, z = o0[1] + along0[1] * i;
                long k = key(x, below, z);
                String b = blocks.get(k);
                boolean floorOfRoom = below < 0; // the ground (or a room's floor) under the design
                if (b != null && !b.contains("door") || under.contains(k) || floorOfRoom) {
                    held++;
                }
            }
            if (held * 2 < def.w()) {
                return "in the air";
            }
        }
        if ("window".equals(mount)) {
            // A curtain hangs over glass: most of the blocks behind it are glass or panes.
            int[] back0 = step(opposite(f));
            int glass = 0, all = 0;
            for (int i = 0; i < def.w(); i++) {
                for (int k = 0; k < 2; k++) {
                    String b = blocks.get(key(o0[0] + along0[0] * i + back0[0], y + k, o0[1] + along0[1] * i + back0[1]));
                    all++;
                    if (b != null && (b.contains("glass") || b.contains("pane"))) {
                        glass++;
                    }
                }
            }
            if (glass * 2 < all) {
                return "no window";
            }
        }
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

    static final String[] FACINGS = { "north", "south", "east", "west" };

    /**
     * The period's basics (those of the last listed period that has any: its own over the shared
     * ones) along inside walls of the lowest floor with room, spaced apart.
     */
    static int furnish(BuildPlan plan, List<String> eras, Map<Long, String> blocks, int[] room, java.util.Set<Long> taken) {
        List<PropLibrary.Def> basics = new ArrayList<>();
        for (String e : eras) {
            List<PropLibrary.Def> these = new ArrayList<>();
            for (PropLibrary.Def d : PropLibrary.all(e)) {
                if (d.fill() && d.w() == 1 && "floor".equals(d.mount())) {
                    these.add(d);
                }
            }
            if (!these.isEmpty()) {
                basics = these;
            }
        }
        if (basics.isEmpty()) {
            return 0;
        }
        List<int[]> spots = spots(blocks, room, taken, 1, true);
        // Spread out: take spots at least 3 blocks from those taken.
        List<int[]> chosen = new ArrayList<>();
        for (int[] s : spots) {
            if (chosen.size() >= Math.min(FILL, basics.size())) {
                break;
            }
            boolean far = true;
            for (int[] c : chosen) {
                far &= Math.abs(c[0] - s[0]) + Math.abs(c[1] - s[1]) >= 3;
            }
            if (far) {
                chosen.add(s);
            }
        }
        for (int i = 0; i < chosen.size(); i++) {
            int[] s = chosen.get(i);
            BuildPlan.Prop p = new BuildPlan.Prop(basics.get(i % basics.size()).id(), s[0], s[3], s[1], FACINGS[s[2]]);
            plan.props.add(p);
            taken.addAll(cells(p, basics.get(i % basics.size())));
        }
        return chosen.size();
    }

    /**
     * Places along inside walls on the lowest floor that has at least two: {x, z, facing, y}, w blocks
     * wide (along the wall from x or z), each with air for 2 blocks up, a floor under it, the wall
     * behind (when needed), open floor in front, inside the building, not by a door, not taken.
     */
    static List<int[]> spots(Map<Long, String> blocks, int[] room, java.util.Set<Long> taken, int w, boolean wallBehind) {
        List<int[]> out = new ArrayList<>();
        if (blocks.isEmpty()) {
            return out;
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
        for (int y = Math.max(minY + 1, 0); y <= maxY; y++) {
            out.clear();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int f = 0; f < 4; f++) {
                        if (fits(blocks, room, taken, x, y, z, f, w, wallBehind, minX, maxX, minZ, maxZ)) {
                            out.add(new int[] { x, z, f, y });
                            break;
                        }
                    }
                }
            }
            if (out.size() >= 2) {
                return out;
            }
        }
        out.clear();
        return out;
    }

    private static boolean fits(Map<Long, String> blocks, int[] room, java.util.Set<Long> taken, int x0, int y, int z0, int f, int w,
                                boolean wallBehind, int minX, int maxX, int minZ, int maxZ) {
        String facing = FACINGS[f];
        int[] along = "north".equals(facing) || "south".equals(facing) ? new int[] { 1, 0 } : new int[] { 0, 1 };
        int[] back = step(opposite(facing));
        int[] front = step(facing);
        for (int i = 0; i < w; i++) {
            int x = x0 + along[0] * i, z = z0 + along[1] * i;
            if (blocks.containsKey(key(x, y, z)) || blocks.containsKey(key(x, y + 1, z)) || !solid(blocks, null, x, y - 1, z)
                || taken.contains(key(x, y, z)) || taken.contains(key(x, y + 1, z))) {
                return false;
            }
            if (room == null && !enclosed(blocks, x, y, z, minX, maxX, minZ, maxZ)) {
                return false;
            }
            if (nearDoor(blocks, x, y, z) || doorZone(blocks).contains(key(x, y, z))) {
                return false;
            }
            if (wallBehind && !solid(blocks, room, x + back[0], y, z + back[1])) {
                return false;
            }
            if (blocks.containsKey(key(x + front[0], y, z + front[1])) || blocks.containsKey(key(x + front[0], y + 1, z + front[1]))) {
                return false;
            }
        }
        return true;
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

    /** The 2 blocks in front of and behind every door (3 wide, door high): kept free for walking in. */
    static java.util.Set<Long> doorZone(Map<Long, String> blocks) {
        java.util.Set<Long> zone = new java.util.HashSet<>();
        for (Map.Entry<Long, String> e : blocks.entrySet()) {
            String b = e.getValue();
            if (!b.contains("_door") || b.contains("trapdoor") || b.contains("half=upper")) {
                continue;
            }
            int x = kx(e.getKey()), y = ky(e.getKey()), z = kz(e.getKey());
            if (blocks.containsKey(key(x, y - 1, z)) && blocks.get(key(x, y - 1, z)).contains("_door")) {
                continue; // the upper half (counted from the lower)
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("facing=(north|south|east|west)").matcher(b);
            List<String> ways = m.find() ? List.of(m.group(1)) : List.of("north", "east");
            for (String w : ways) {
                int[] d = step(w);
                int[] side = { d[1], d[0] };
                for (int k = 1; k <= 2; k++) {
                    for (int s = -1; s <= 1; s++) {
                        for (int up = 0; up <= 1; up++) {
                            zone.add(key(x + d[0] * k + side[0] * s, y + up, z + d[1] * k + side[1] * s));
                            zone.add(key(x - d[0] * k + side[0] * s, y + up, z - d[1] * k + side[1] * s));
                        }
                    }
                }
            }
        }
        return zone;
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
