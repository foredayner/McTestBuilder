package com.example.ai.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Blocks that are two blocks: doors and tall plants (a lower and an upper half) and beds (a foot
 * and a head). The AI often places only one half, or a wall over the upper half of a door, and the
 * game then drops or cuts the other half. Before a plan is placed, every such block becomes its
 * whole pair, placed after everything else (lower before upper, foot before head) so nothing covers
 * it. Pure, so it can be tested without a world.
 */
public final class DoubleBlocks {

    private static final Pattern TALL_PLANTS = Pattern.compile(
        "minecraft:(sunflower|lilac|rose_bush|peony|tall_grass|large_fern|tall_seagrass|pitcher_plant)");

    private DoubleBlocks() {
    }

    static String id(String block) {
        String b = block == null ? "" : block.trim();
        String id = b.contains("[") ? b.substring(0, b.indexOf('[')) : b;
        return id.contains(":") ? id : "minecraft:" + id;
    }

    static Map<String, String> props(String block) {
        Map<String, String> out = new LinkedHashMap<>();
        int open = block.indexOf('[');
        int close = block.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return out;
        }
        for (String kv : block.substring(open + 1, close).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                out.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return out;
    }

    static String state(String id, Map<String, String> props) {
        if (props.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (var e : props.entrySet()) {
            sb.append(first ? "" : ",").append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    static boolean isDoor(String id) {
        return id.endsWith("_door") && !id.endsWith("trapdoor");
    }

    static boolean isBed(String id) {
        return id.endsWith("_bed");
    }

    static boolean isTall(String id) {
        return TALL_PLANTS.matcher(id).matches();
    }

    /** Whether this block is one half of a pair. */
    public static boolean isDouble(String block) {
        String id = id(block);
        return isDoor(id) || isBed(id) || isTall(id);
    }

    /** {dx, dz} of a horizontal facing (north = -z). */
    static int[] step(String facing) {
        return switch (facing == null ? "" : facing) {
            case "east" -> new int[] { 1, 0 };
            case "south" -> new int[] { 0, 1 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, -1 };
        };
    }

    /**
     * The plan's ops with every door, bed and tall plant made whole and moved to the end. Pairs
     * named twice (the AI placed both halves) are placed once.
     */
    public static List<BuildPlan.Op> complete(List<BuildPlan.Op> ops) {
        List<BuildPlan.Op> rest = new ArrayList<>();
        Map<String, BuildPlan.Op[]> pairs = new LinkedHashMap<>();
        for (BuildPlan.Op op : ops) {
            if (!"set".equals(op.kind) || op.block == null || !isDouble(op.block)) {
                rest.add(op);
                continue;
            }
            String id = id(op.block);
            Map<String, String> p = props(op.block);
            BuildPlan.Op first;
            BuildPlan.Op second;
            if (isBed(id)) {
                String facing = p.getOrDefault("facing", "north");
                boolean head = "head".equals(p.get("part"));
                int[] s = step(facing);
                int fx = head ? op.x1 - s[0] : op.x1, fz = head ? op.z1 - s[1] : op.z1;
                Map<String, String> foot = new LinkedHashMap<>(p);
                foot.put("facing", facing);
                foot.put("part", "foot");
                Map<String, String> top = new LinkedHashMap<>(foot);
                top.put("part", "head");
                first = new BuildPlan.Op("set", fx, op.y1, fz, fx, op.y1, fz, state(id, foot), "replace");
                second = new BuildPlan.Op("set", fx + s[0], op.y1, fz + s[1], fx + s[0], op.y1, fz + s[1], state(id, top), "replace");
            } else {
                boolean upper = "upper".equals(p.get("half"));
                int y = upper ? op.y1 - 1 : op.y1;
                Map<String, String> lower = new LinkedHashMap<>(p);
                if (isDoor(id)) {
                    lower.putIfAbsent("facing", "north");
                }
                lower.put("half", "lower");
                Map<String, String> up = new LinkedHashMap<>(lower);
                up.put("half", "upper");
                if (isDoor(id)) {
                    up.remove("powered");
                }
                first = new BuildPlan.Op("set", op.x1, y, op.z1, op.x1, y, op.z1, state(id, lower), "replace");
                second = new BuildPlan.Op("set", op.x1, y + 1, op.z1, op.x1, y + 1, op.z1, state(id, up), "replace");
            }
            // One pair per place: the last one written wins (as the AI meant).
            pairs.put(first.x1 + "," + first.y1 + "," + first.z1, new BuildPlan.Op[] { first, second });
        }
        if (pairs.isEmpty()) {
            return ops;
        }
        // Something placed later on a pair's cells (a wall over a door's top) gives way to the pair.
        List<BuildPlan.Op> out = new ArrayList<>(rest);
        for (BuildPlan.Op[] pair : pairs.values()) {
            out.add(pair[0]);
            out.add(pair[1]);
        }
        return out;
    }
}
