package com.example.ai.build;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * One equipment effect made of glowing colour dots: a shape (heart, star, ring, slash arc, custom
 * points...) drawn by every player's game and moved over time (forward, rise, spin, expand,
 * scatter, fade...). The server only says which effect plays where; the dots are drawn by the
 * client, so they are smooth and cost almost no network.
 */
public final class Spark {

    public static final List<String> SHAPES = List.of(
        "burst", "sphere", "circle", "ring", "heart", "star", "crescent", "slash", "cross", "spiral", "bolt",
        "magic_circle", "square", "triangle", "diamond", "line", "points");
    public static final List<String> MOVES = List.of(
        "forward", "back", "rise", "fall", "expand", "shrink", "spin", "orbit", "pulse", "scatter", "gather",
        "wave", "reveal", "fade", "flicker");
    public static final List<String> PLANES = List.of("front", "flat", "side");
    /** Where an attack or landing effect starts. */
    public static final List<String> STARTS = List.of("weapon", "target", "ground", "above", "self");
    /** Where an effect while held or worn sits. */
    public static final List<String> ANCHORS = List.of("item", "head", "body", "back", "feet", "around");
    public static final int MAX_DOTS = 96;

    public String shape = "burst";
    /** Shape "points": custom points in shape units (-1..1), as [u, v] or [u, v, w]. */
    public List<double[]> points;
    /** Join custom points (or a polygon's corners) with lines of dots. */
    public boolean lines = true;
    /** Fill closed shapes (heart, star, circle...) instead of drawing their outline. */
    public boolean fill;
    public int color = 0xFFFFFF;
    /** Colour it turns into over its life (-1 = stays the same). */
    public int color2 = -1;
    /** Size in blocks (radius of the shape). */
    public double size = 0.6;
    /** How many dots. */
    public int count = 32;
    /** Dot size in blocks. */
    public double dot = 0.07;
    public String plane = "front";
    public String start = "weapon";
    public String anchor = "";
    public List<String> moves = new ArrayList<>();
    /** Blocks per second for moves along a direction; turns per second for spin and orbit. */
    public double speed = 1.5;
    /** Life in ticks (20 = 1 second); for a looping effect while held/worn, one cycle. */
    public int ticks = 14;
    /** Held/worn and trails: ticks between new copies (0 = one copy that loops in place). */
    public int every = 0;

    /** All effects of one piece of equipment. */
    public static final class Effects {
        /** Weapons: when swung (bows: where the arrow lands). At most 3, they play together. */
        public List<Spark> attack = new ArrayList<>();
        /** While held or worn. At most 2. */
        public List<Spark> idle = new ArrayList<>();
        /** Bows: behind the arrow in flight. At most 1. */
        public List<Spark> trail = new ArrayList<>();

        public boolean isEmpty() {
            return attack.isEmpty() && idle.isEmpty() && trail.isEmpty();
        }

        public String toJson() {
            return GSON.toJson(this);
        }

        public static Effects fromJson(String json) {
            try {
                Effects set = GSON.fromJson(json, Effects.class);
                if (set == null) {
                    return new Effects();
                }
                set.attack = clean(set.attack, 3);
                set.idle = clean(set.idle, 2);
                set.trail = clean(set.trail, 1);
                return set;
            } catch (RuntimeException exception) {
                return new Effects();
            }
        }

        private static List<Spark> clean(List<Spark> list, int max) {
            List<Spark> out = new ArrayList<>();
            if (list != null) {
                for (Spark s : list) {
                    if (s != null && out.size() < max) {
                        out.add(s.clamped());
                    }
                }
            }
            return out;
        }
    }

    private static final Gson GSON = new Gson();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Spark fromJson(String json) {
        try {
            Spark s = GSON.fromJson(json, Spark.class);
            return s == null ? null : s.clamped();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static List<Spark> listFromJson(String json) {
        try {
            List<Spark> list = GSON.fromJson(json, new TypeToken<List<Spark>>() { }.getType());
            List<Spark> out = new ArrayList<>();
            if (list != null) {
                for (Spark s : list) {
                    if (s != null) {
                        out.add(s.clamped());
                    }
                }
            }
            return out;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** Keep every value in a safe range (also for data that came over the network). */
    public Spark clamped() {
        shape = SHAPES.contains(shape) ? shape : "burst";
        plane = PLANES.contains(plane) ? plane : "front";
        start = STARTS.contains(start) ? start : "weapon";
        anchor = anchor == null || !ANCHORS.contains(anchor) ? "" : anchor;
        List<String> m = new ArrayList<>();
        if (moves != null) {
            for (String move : moves) {
                if (MOVES.contains(move) && !m.contains(move) && m.size() < 6) {
                    m.add(move);
                }
            }
        }
        moves = m;
        size = clamp(size, 0.1, 4);
        count = (int) clamp(count, 4, MAX_DOTS);
        dot = clamp(dot, 0.02, 0.3);
        speed = clamp(speed, 0, 12);
        ticks = (int) clamp(ticks, 2, 100);
        every = (int) clamp(every, 0, 40);
        color &= 0xFFFFFF;
        color2 = color2 < 0 ? -1 : color2 & 0xFFFFFF;
        if (points != null) {
            List<double[]> p = new ArrayList<>();
            for (double[] q : points) {
                if (q != null && q.length >= 2 && p.size() < 64) {
                    p.add(new double[] { clamp(q[0], -2, 2), clamp(q[1], -2, 2), q.length > 2 ? clamp(q[2], -2, 2) : 0 });
                }
            }
            points = p;
        }
        return this;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /**
     * Build an effect from what the AI script wrote, e.g. [shape: 'heart', color: '#ff66cc', size: 0.5,
     * moves: ['forward', 'fade'], speed: 6]. Unknown keys or values are reported so the AI can fix them.
     */
    public static Spark fromMap(Map<?, ?> map) {
        Spark s = new Spark();
        if (map == null) {
            return s;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).trim();
            Object v = e.getValue();
            switch (key) {
                case "shape" -> s.shape = word(v, SHAPES, "shape");
                case "color", "colour" -> s.color = color(v);
                case "color2", "colour2", "toColor" -> s.color2 = color(v);
                case "size" -> s.size = num(v, key);
                case "count", "dots" -> s.count = (int) Math.round(num(v, key));
                case "dot", "dotSize" -> s.dot = num(v, key);
                case "plane" -> s.plane = word(v, PLANES, "plane");
                case "start" -> s.start = word(v, STARTS, "start");
                case "anchor", "at" -> s.anchor = word(v, ANCHORS, "anchor");
                case "speed" -> s.speed = num(v, key);
                case "ticks", "time" -> s.ticks = (int) Math.round(num(v, key));
                case "every" -> s.every = (int) Math.round(num(v, key));
                case "fill" -> s.fill = Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
                case "lines" -> s.lines = Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
                case "move", "moves" -> {
                    s.moves = new ArrayList<>();
                    if (v instanceof List<?> list) {
                        for (Object o : list) {
                            s.moves.add(word(o, MOVES, "move"));
                        }
                    } else {
                        for (String part : String.valueOf(v).split("[,\\s]+")) {
                            if (!part.isBlank()) {
                                s.moves.add(word(part, MOVES, "move"));
                            }
                        }
                    }
                }
                case "points" -> {
                    if (!(v instanceof List<?> list)) {
                        throw new IllegalArgumentException("points must be a list like [[0, 1], [0.5, 0], [0, -1]]");
                    }
                    s.points = new ArrayList<>();
                    for (Object o : list) {
                        if (!(o instanceof List<?> xy) || xy.size() < 2) {
                            throw new IllegalArgumentException("each point is [u, v] or [u, v, w], got " + o);
                        }
                        s.points.add(new double[] { num(xy.get(0), "points"), num(xy.get(1), "points"),
                            xy.size() > 2 ? num(xy.get(2), "points") : 0 });
                    }
                    s.shape = "points";
                }
                default -> throw new IllegalArgumentException("unknown effect setting '" + key + "'; use shape, color, color2, "
                    + "size, count, dot, plane, start, anchor, moves, speed, ticks, every, fill, lines, points");
            }
        }
        if ("points".equals(s.shape) && (s.points == null || s.points.isEmpty())) {
            throw new IllegalArgumentException("shape 'points' needs points: [[u, v], ...]");
        }
        return s.clamped();
    }

    private static String word(Object v, List<String> allowed, String what) {
        String w = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(w)) {
            throw new IllegalArgumentException("unknown " + what + " '" + v + "'; use one of " + allowed);
        }
        return w;
    }

    private static double num(Object v, String what) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(what + " must be a number, got '" + v + "'");
        }
    }

    private static int color(Object v) {
        String c = String.valueOf(v).trim().replace("#", "");
        try {
            return Integer.parseInt(c, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("colour must look like '#ff66cc', got '" + v + "'");
        }
    }

    // ------------------------------------------------------------------ shape points

    /**
     * The dots of the shape in shape units: u (right), v (up), w (toward the viewer / out of the
     * plane), mostly within -1..1. Random parts use a fixed seed so every player sees the same.
     */
    public List<double[]> dots() {
        Random random = new Random(31L * shape.hashCode() + count);
        List<double[]> out = new ArrayList<>();
        int n = count;
        switch (shape) {
            case "burst" -> {
                for (int i = 0; i < n; i++) {
                    double[] d = randomInBall(random);
                    out.add(d);
                }
            }
            case "sphere" -> {
                double golden = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < n; i++) {
                    double y = 1 - 2 * (i + 0.5) / n;
                    double r = Math.sqrt(1 - y * y);
                    out.add(new double[] { Math.cos(golden * i) * r, y, Math.sin(golden * i) * r });
                }
            }
            case "circle", "ring" -> {
                if (fill && "circle".equals(shape)) {
                    for (int i = 0; i < n; i++) {
                        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble());
                        out.add(new double[] { Math.cos(a) * r, Math.sin(a) * r, 0 });
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n;
                        out.add(new double[] { Math.cos(a), Math.sin(a), 0 });
                    }
                }
            }
            case "heart" -> {
                if (fill) {
                    while (out.size() < n) {
                        double x = random.nextDouble() * 2.4 - 1.2, y = random.nextDouble() * 2.4 - 1.2;
                        double a = x * x + y * y - 1;
                        if (a * a * a - x * x * y * y * y <= 0) {
                            out.add(new double[] { x * 0.85, y * 0.85 + 0.05, 0 });
                        }
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        double t = Math.PI * 2 * i / n;
                        double x = 16 * Math.pow(Math.sin(t), 3);
                        double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
                        out.add(new double[] { x / 17.0, (y + 2.5) / 17.0, 0 });
                    }
                }
            }
            case "star" -> polygon(out, star(5, 0.42), n, random);
            case "triangle" -> polygon(out, regular(3, Math.PI / 2), n, random);
            case "square" -> polygon(out, regular(4, Math.PI / 4), n, random);
            case "diamond" -> polygon(out, regular(4, Math.PI / 2), n, random);
            case "cross" -> {
                List<double[]> bars = new ArrayList<>();
                bars.add(new double[] { -1, 0, 0 });
                bars.add(new double[] { 1, 0, 0 });
                path(out, bars, n / 2, false);
                List<double[]> up = new ArrayList<>();
                up.add(new double[] { 0, -1, 0 });
                up.add(new double[] { 0, 1, 0 });
                path(out, up, n - n / 2, false);
            }
            case "crescent", "slash" -> {
                // An arc that is thick in the middle and thin at the tips (slash: wide and flat).
                boolean slash = "slash".equals(shape);
                double span = slash ? Math.toRadians(150) : Math.toRadians(250);
                double thick = slash ? 0.25 : 0.45;
                for (int i = 0; i < n; i++) {
                    double s = (i + 0.5) / n;
                    double a = Math.PI / 2 - span / 2 + span * s;
                    double width = thick * Math.sin(Math.PI * s);
                    double r = 1 - width * random.nextDouble();
                    out.add(new double[] { Math.cos(a) * r * (slash ? 1 : 1), Math.sin(a) * r * (slash ? 0.55 : 1), 0 });
                }
            }
            case "spiral" -> {
                for (int i = 0; i < n; i++) {
                    double s = (double) i / n;
                    double a = s * Math.PI * 6;
                    out.add(new double[] { Math.cos(a) * s, Math.sin(a) * s, 0 });
                }
            }
            case "bolt" -> {
                List<double[]> zig = new ArrayList<>();
                double[][] corners = { { 0.1, 1 }, { -0.25, 0.3 }, { 0.2, 0.15 }, { -0.15, -0.5 }, { 0.15, -0.35 }, { -0.05, -1 } };
                for (double[] c : corners) {
                    zig.add(new double[] { c[0], c[1], 0 });
                }
                path(out, zig, n, false);
            }
            case "magic_circle" -> {
                int ring = n * 2 / 5, inner = n / 5;
                for (int i = 0; i < ring; i++) {
                    double a = Math.PI * 2 * i / ring;
                    out.add(new double[] { Math.cos(a), Math.sin(a), 0 });
                }
                for (int i = 0; i < inner; i++) {
                    double a = Math.PI * 2 * i / inner;
                    out.add(new double[] { Math.cos(a) * 0.8, Math.sin(a) * 0.8, 0 });
                }
                List<double[]> pentagram = new ArrayList<>();
                for (int k = 0; k <= 5; k++) {
                    double a = Math.PI / 2 + k * 4 * Math.PI / 5;
                    pentagram.add(new double[] { Math.cos(a) * 0.8, Math.sin(a) * 0.8, 0 });
                }
                path(out, pentagram, n - ring - inner, false);
            }
            case "line" -> {
                for (int i = 0; i < n; i++) {
                    out.add(new double[] { -1 + 2.0 * i / Math.max(1, n - 1), 0, 0 });
                }
            }
            case "points" -> {
                List<double[]> p = points == null ? List.of() : points;
                if (lines && p.size() >= 2) {
                    path(out, p, n, false);
                } else {
                    out.addAll(p);
                }
            }
            default -> {
                for (int i = 0; i < n; i++) {
                    out.add(randomInBall(random));
                }
            }
        }
        return out;
    }

    private static double[] randomInBall(Random random) {
        while (true) {
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1, z = random.nextDouble() * 2 - 1;
            if (x * x + y * y + z * z <= 1) {
                return new double[] { x, y, z };
            }
        }
    }

    private static List<double[]> regular(int sides, double start) {
        List<double[]> c = new ArrayList<>();
        for (int i = 0; i < sides; i++) {
            double a = start + Math.PI * 2 * i / sides;
            c.add(new double[] { Math.cos(a), Math.sin(a), 0 });
        }
        return c;
    }

    private static List<double[]> star(int tips, double inner) {
        List<double[]> c = new ArrayList<>();
        for (int i = 0; i < tips * 2; i++) {
            double r = i % 2 == 0 ? 1 : inner;
            double a = Math.PI / 2 + Math.PI * i / tips;
            c.add(new double[] { Math.cos(a) * r, Math.sin(a) * r, 0 });
        }
        return c;
    }

    /** A closed polygon: its outline, or (fill) random points inside it. */
    private void polygon(List<double[]> out, List<double[]> corners, int n, Random random) {
        if (!fill) {
            path(out, corners, n, true);
            return;
        }
        int guard = 0;
        while (out.size() < n && guard++ < n * 50) {
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1;
            if (inside(corners, x, y)) {
                out.add(new double[] { x, y, 0 });
            }
        }
    }

    private static boolean inside(List<double[]> poly, double x, double y) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double[] a = poly.get(i), b = poly.get(j);
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                in = !in;
            }
        }
        return in;
    }

    /** n dots spread evenly along the lines through the corners (closed: back to the first). */
    private static void path(List<double[]> out, List<double[]> corners, int n, boolean closed) {
        int segments = closed ? corners.size() : corners.size() - 1;
        if (segments <= 0 || n <= 0) {
            out.addAll(corners);
            return;
        }
        double total = 0;
        double[] len = new double[segments];
        for (int i = 0; i < segments; i++) {
            double[] a = corners.get(i), b = corners.get((i + 1) % corners.size());
            len[i] = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            total += len[i];
        }
        for (int k = 0; k < n; k++) {
            double d = total * (closed ? k / (double) n : k / (double) Math.max(1, n - 1));
            int i = 0;
            while (i < segments - 1 && d > len[i]) {
                d -= len[i];
                i++;
            }
            double[] a = corners.get(i), b = corners.get((i + 1) % corners.size());
            double f = len[i] <= 0 ? 0 : Math.min(1, d / len[i]);
            out.add(new double[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f });
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
