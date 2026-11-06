package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ready-made furniture that buildings place with ai.prop: things that blocks cannot show well
 * (a sofa, a TV, a curtain...). Each prop is an object script in the mod (aib_props/ERA/ID.groovy,
 * front toward -Z, back at z = 0, standing on y = 0), listed in aib_props/props.json with how it is
 * mounted and how many blocks it takes. Scripts are run once and kept.
 */
public final class PropLibrary {

    /**
     * How a prop is listed: mount floor | top (on a table) | wall | ceiling | window (over a window, from its
     * lowest pane up); align back (against the wall behind) | center; pass: players walk through it (a curtain).
     */
    public record Def(String id, String name, String era, String mount, String align, int w, int d, int light, String about, boolean pass,
                      boolean fill, boolean often) {
    }

    /** "a,b" → [a, b] ("" → none). */
    public static List<String> eraList(String eras) {
        List<String> out = new ArrayList<>();
        for (String e : eras.split(",")) {
            if (!e.isBlank()) {
                out.add(e.trim());
            }
        }
        return out;
    }

    /** The periods props are made for, and how the AI is told about each. */
    public static final java.util.Map<String, String> ERAS = new java.util.LinkedHashMap<>();

    static {
        ERAS.put("medieval", "the European Middle Ages (castles, taverns, villages)");
        ERAS.put("1920s", "the 1920s-1930s in the West (Call of Cthulhu's classic era)");
        ERAS.put("joseon", "Korea before 1945: Joseon and the colonial years (hanok, floor sitting)");
        ERAS.put("postwar", "Korea 1950s-1980s (floor sitting, coal briquettes, legged TVs)");
        ERAS.put("contemporary", "today (the 1990s on), anywhere");
        ERAS.put("contemporary_west", "today in the West (chairs, shoes indoors)");
        ERAS.put("contemporary_east", "today in Korea (floor sitting, shoes off at the door)");
    }

    /** The props of one period. */
    public static List<Def> all(String era) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs().values()) {
            if (d.era().equals(era)) {
                out.add(d);
            }
        }
        return out;
    }

    /** For a request: one line per prop of these periods ("a,b"; "" when they have none). */
    public static String catalog(String era) {
        StringBuilder sb = new StringBuilder();
        for (String e : eraList(era)) {
            for (Def d : all(e)) {
                line(sb, d);
            }
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, Def d) {
        sb.append("  ").append(d.id()).append(" (").append(d.w()).append(d.w() == 1 ? " block" : " blocks").append(" wide, ")
            .append(d.mount()).append("): ").append(d.about()).append(d.often() ? " (USE IN MOST ROOMS)" : "").append('\n');
    }

    /** A prop ready to place in the building's frame, which and its name, its light {x, y, z, level} or null, whether it is walked through. */
    public record Placed(BuildPlan plan, String id, String name, int[] light, boolean pass) {
    }

    private static final String ROOT = "/aib_props/";
    private static volatile Map<String, Def> defs;
    private static final Map<String, BuildPlan> PLANS = new ConcurrentHashMap<>();

    private PropLibrary() {
    }

    private static Map<String, Def> defs() {
        Map<String, Def> d = defs;
        if (d == null) {
            Map<String, Def> out = new LinkedHashMap<>();
            try (InputStream in = PropLibrary.class.getResourceAsStream(ROOT + "props.json")) {
                if (in != null) {
                    List<Def> list = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<List<Def>>() {
                    }.getType());
                    for (Def def : list) {
                        out.put(def.id(), def);
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("[ERROR] Prop list could not be read: " + e.getMessage());
            }
            defs = d = Collections.unmodifiableMap(out);
        }
        return d;
    }

    public static List<Def> all() {
        return new ArrayList<>(defs().values());
    }

    public static Def get(String id) {
        return id == null ? null : defs().get(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static String ids() {
        return String.join(", ", defs().keySet());
    }

    /** The prop's script. */
    public static String script(Def def) {
        try (InputStream in = PropLibrary.class.getResourceAsStream(ROOT + def.era() + "/" + def.id() + ".groovy")) {
            if (in == null) {
                throw new IllegalStateException("no script for prop " + def.id());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The prop's pieces as designed (front toward -Z, back at z = 0). */
    public static BuildPlan plan(Def def) {
        // Not computeIfAbsent: the script waits for MinecraftAI.SCRIPT_LOCK, which must not be awaited inside the map's lock.
        BuildPlan plan = PLANS.get(def.id());
        if (plan == null) {
            plan = MinecraftAI.recordDetached(script(def));
            BuildPlan before = PLANS.putIfAbsent(def.id(), plan);
            if (before != null) {
                plan = before;
            }
        }
        return plan;
    }

    /** For the building prompt: every prop, grouped by period. */
    public static String catalog() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> era : ERAS.entrySet()) {
            List<Def> list = all(era.getKey());
            if (list.isEmpty()) {
                continue;
            }
            sb.append("  -- ").append(era.getKey()).append(": ").append(era.getValue()).append('\n');
            for (Def d : list) {
                line(sb, d);
            }
        }
        return sb.toString();
    }

    /**
     * Where a prop goes in the building's frame. It takes its width in blocks from (x, z) toward +X
     * (facing north or south) or +Z (east or west), centred on them; its back lies against the far
     * side of its block (align back) or it stands in the middle of the block (center). Floor and top
     * props stand on y, wall props keep their height above the floor y, window props start at the window's
     * lowest pane y, ceiling props hang from the top of block y.
     */
    public static Placed place(BuildPlan.Prop prop) {
        Def def = get(prop.id);
        if (def == null) {
            return null;
        }
        String f = BuildPlan.normalizeFacing(prop.facing);
        int turns = BuildPlan.turnsBetween("north", f);
        BuildPlan turned = MinecraftAI.turned(plan(def), turns);
        double[] b = turned.partBounds();
        boolean alongX = "north".equals(f) || "south".equals(f);
        boolean centre = "center".equals(def.align());
        double dx, dz;
        if (alongX) {
            dx = prop.x + def.w() / 2.0 - (b[0] + b[3]) / 2;
            if (centre) {
                dz = prop.z + 0.5 - (b[2] + b[5]) / 2;
            } else {
                dz = "north".equals(f) ? prop.z + 1 - b[5] : prop.z - b[2];
            }
        } else {
            dz = prop.z + def.w() / 2.0 - (b[2] + b[5]) / 2;
            if (centre) {
                dx = prop.x + 0.5 - (b[0] + b[3]) / 2;
            } else {
                dx = "west".equals(f) ? prop.x + 1 - b[3] : prop.x - b[0];
            }
        }
        double dy = switch (def.mount()) {
            case "wall", "window" -> prop.y;
            case "ceiling" -> prop.y + 1 - b[4];
            default -> prop.y - b[1];
        };
        int[] light = null;
        if (def.light() > 0) {
            int ly = "ceiling".equals(def.mount()) ? (int) Math.floor(prop.y) : (int) Math.floor(prop.y) + 1;
            light = new int[] { (int) Math.floor(prop.x), ly, (int) Math.floor(prop.z), Math.min(15, def.light()) };
        }
        return new Placed(turned.translated(dx, dy, dz), def.id(), def.name(), light, def.pass());
    }
}
