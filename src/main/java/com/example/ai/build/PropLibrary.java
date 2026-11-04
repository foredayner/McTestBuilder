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

    /** How a prop is listed: mount floor | top (on a table) | wall | ceiling; align back (against the wall behind) | center. */
    public record Def(String id, String name, String era, String mount, String align, int w, int d, int light, String about) {
    }

    /** A prop ready to place in the building's frame, its name, and its light {x, y, z, level} or null. */
    public record Placed(BuildPlan plan, String name, int[] light) {
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
        return PLANS.computeIfAbsent(def.id(), k -> MinecraftAI.recordDetached(script(def)));
    }

    /** For the building prompt: one line per prop. */
    public static String catalog() {
        StringBuilder sb = new StringBuilder();
        for (Def d : defs().values()) {
            sb.append("  ").append(d.id()).append(" (").append(d.w()).append(d.w() == 1 ? " block" : " blocks").append(" wide, ")
                .append(d.mount()).append("): ").append(d.about()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Where a prop goes in the building's frame. It takes its width in blocks from (x, z) toward +X
     * (facing north or south) or +Z (east or west), centred on them; its back lies against the far
     * side of its block (align back) or it stands in the middle of the block (center). Floor and top
     * props stand on y, wall props keep their height above the floor y, ceiling props hang from the top of block y.
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
            case "wall" -> prop.y;
            case "ceiling" -> prop.y + 1 - b[4];
            default -> prop.y - b[1];
        };
        int[] light = null;
        if (def.light() > 0) {
            int ly = "ceiling".equals(def.mount()) ? (int) Math.floor(prop.y) : (int) Math.floor(prop.y) + 1;
            light = new int[] { (int) Math.floor(prop.x), ly, (int) Math.floor(prop.z), Math.min(15, def.light()) };
        }
        return new Placed(turned.translated(dx, dy, dz), def.name(), light);
    }
}
