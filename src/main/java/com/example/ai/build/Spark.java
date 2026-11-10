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
    public static final List<String> PLANES = List.of("front", "vertical", "flat", "side");
    /** Where an attack or landing effect starts. */
    public static final List<String> STARTS = List.of("weapon", "target", "ground", "above", "self");
    /** Where an effect while held or worn sits. */
    public static final List<String> ANCHORS = List.of("item", "head", "body", "back", "feet", "around");
    public static final int MAX_DOTS = 96;
    /** Layers of an attack effect, and effects while held or worn. */
    public static final int MAX_ATTACK = 5;
    public static final int MAX_IDLE = 3;
    /**
     * What the effect is drawn as: dots (the shape's dots), or one smooth piece: swipe (the glowing
     * band a blade leaves), shockwave (a ring spreading over the ground), pillar (a column of light
     * going up), beam (a ray going ahead), disc (a magic circle), ribbon (a band left behind along
     * the path of what it follows: arrows, orbiting lights), bolt (a jagged, branching lightning
     * bolt from the sky down to where it starts).
     */
    public static final List<String> FORMS = List.of("dots", "swipe", "shockwave", "pillar", "beam", "disc", "ribbon", "bolt");
    /** What each dot looks like (cells of textures/misc/looks.png). */
    public static final List<String> LOOKS = List.of("dot", "glow", "star", "streak", "shard", "rune", "ember", "ring");
    /** Vanilla particles an effect may use instead of its own dots ("dust" takes the effect's colours). */
    public static final List<String> PARTICLES = List.of("flame", "soul_fire_flame", "small_flame", "end_rod", "cherry_leaves",
        "snowflake", "electric_spark", "glow", "enchant", "smoke", "large_smoke", "cloud", "bubble", "heart", "note", "crit",
        "enchanted_hit", "totem_of_undying", "witch", "portal", "sculk_soul", "wax_on", "scrape", "lava", "firework", "soul",
        "ash", "white_ash", "spore_blossom_air", "poof", "dust");

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
    /** What it is drawn as (see {@link #FORMS}). */
    public String form = "dots";
    /** Swipe: how far round the blade's arc goes, in degrees. */
    public double arc = 160;
    /** Swipe, shockwave, pillar, beam, ribbon: how thick, as a part of the size (0.05-1). */
    public double width = 0.4;
    /** How each dot looks (see {@link #LOOKS}). */
    public String look = "dot";
    /** A vanilla particle drawn instead of the dots ("" = our own dots; see {@link #PARTICLES}). */
    public String particle = "";
    /** Colours over its life, 2-4 stops (overrides color and color2 when set). */
    public int[] colors;
    /** 0-1: how much the dots differ from each other in size, life and fall (0 = all the same). */
    public double vary = 0;
    /** Blocks per second squared the dots fall (negative: they float up, like smoke). */
    public double gravity = 0;
    /** 0-1: air resistance; moving dots slow down and come to rest. */
    public double drag = 0;
    /** 0-1: dots drift and swirl like smoke or embers in the air. */
    public double turbulence = 0;
    /** A vanilla sound played when the effect starts (e.g. 'entity.blaze.shoot'), or "". */
    public String sound = "";
    public double volume = 1;
    public double pitch = 1;
    /** Ticks after the others it starts (layers: a flash, then a ring, then sparks). */
    public int delay = 0;
    /** Set by the server, not the AI: extra ticks before it starts (weapons wait for the swing to finish). */
    public int lead = 0;
    /** 0-1: shakes the view of whoever is right there when it starts (big hits; players can turn it off). */
    public double shake = 0;
    /** Also a real (harmless) vanilla lightning strike where it starts: no fire, no damage. */
    public boolean lightning;
    /**
     * 0-0.5: what it touches is hurt by this part of the weapon's damage (a shockwave, a beam, a
     * falling bolt...). Never the one hit directly, and at most half the weapon's damage per swing.
     */
    public double damage = 0;
    /** The most a swing's (or a landing's) effects may hurt one target, as part of the weapon's damage. */
    public static final double MAX_DAMAGE = 0.5;
    /** Set by the server, not the AI: a beam this long (blocks) instead of one sized by size (a bow's beam shot). */
    public double reach = 0;
    /** Set by the server, not the AI: how visible a bow's beam is (0 = not at all, 1 = fully). */
    public double opacity = 1;

    /** All effects of one piece of equipment. */
    public static final class Effects {
        /** Weapons: when swung (bows: where the arrow lands). At most 5 layers, each may start later (delay). */
        public List<Spark> attack = new ArrayList<>();
        /** While held or worn. At most 3. */
        public List<Spark> idle = new ArrayList<>();
        /** Bows: behind the arrow in flight. At most 1. */
        public List<Spark> trail = new ArrayList<>();
        /** Bows: what the shot is (an arrow-like thing that flies, or a beam). Null = a normal arrow. */
        public Shot shot;
        /**
         * How projectile shapes are turned: 0 = as made before (the shape's x became up and down in
         * flight), 2 = its x stays side to side and its z up (flat shapes fly flat). New designs are 2.
         */
        public int axes;
        /** Weapons: the damage each hit does floats up over the one hit (like MapleStory). */
        public boolean damageNumbers;
        /** Weapons: every hit rolls 0-9 and does that many ninths of its damage (0 = a miss). */
        public boolean randomDamage;

        public boolean isEmpty() {
            return attack.isEmpty() && idle.isEmpty() && trail.isEmpty() && shot == null && !damageNumbers && !randomDamage;
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
                set.attack = clean(set.attack, MAX_ATTACK);
                set.idle = clean(set.idle, MAX_IDLE);
                set.trail = clean(set.trail, 1);
                set.shot = set.shot == null ? null : set.shot.clamped();
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
        look = look != null && LOOKS.contains(look) ? look : "dot";
        form = form != null && FORMS.contains(form) ? form : "dots";
        arc = clamp(arc, 30, 330);
        width = clamp(width, 0.05, 1);
        particle = particle != null && PARTICLES.contains(particle) ? particle : "";
        if (colors != null) {
            int n = Math.min(4, colors.length);
            int[] c = new int[n];
            for (int i = 0; i < n; i++) {
                c[i] = colors[i] & 0xFFFFFF;
            }
            colors = n >= 2 ? c : null;
            if (colors != null) {
                color = colors[0];
            }
        }
        vary = clamp(vary, 0, 1);
        gravity = clamp(gravity, -3, 8);
        drag = clamp(drag, 0, 1);
        turbulence = clamp(turbulence, 0, 1);
        sound = sound != null && sound.matches("[a-z0-9_.]{1,80}") ? sound : "";
        volume = clamp(volume, 0, 2);
        pitch = clamp(pitch, 0.5, 2);
        reach = clamp(reach, 0, 80);
        damage = clamp(damage, 0, MAX_DAMAGE);
        opacity = clamp(opacity, 0, 1);
        delay = (int) clamp(delay, 0, 40);
        lead = (int) clamp(lead, 0, 30);
        shake = clamp(shake, 0, 1);
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
                case "look" -> s.look = word(v, LOOKS, "look");
                case "form" -> s.form = word(v, FORMS, "form");
                case "arc" -> s.arc = num(v, key);
                case "width", "thickness" -> s.width = num(v, key);
                case "particle" -> {
                    String p = String.valueOf(v).trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
                    s.particle = word(p, PARTICLES, "particle");
                }
                case "colors", "colours" -> {
                    if (!(v instanceof List<?> list) || list.size() < 2 || list.size() > 4) {
                        throw new IllegalArgumentException("colors is a list of 2-4 colours like ['#ffffff', '#ffcc33', '#ff3300']");
                    }
                    s.colors = new int[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        s.colors[i] = color(list.get(i));
                    }
                }
                case "vary" -> s.vary = num(v, key);
                case "gravity" -> s.gravity = num(v, key);
                case "drag" -> s.drag = num(v, key);
                case "turbulence", "swirl" -> s.turbulence = num(v, key);
                case "sound" -> {
                    String snd = String.valueOf(v).trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
                    if (!snd.matches("[a-z0-9_.]{1,80}")) {
                        throw new IllegalArgumentException("sound is a vanilla sound id like 'entity.blaze.shoot', got '" + v + "'");
                    }
                    s.sound = snd;
                }
                case "volume" -> s.volume = num(v, key);
                case "delay", "wait" -> s.delay = (int) Math.round(num(v, key));
                case "shake" -> s.shake = num(v, key);
                case "lightning", "realLightning", "real_lightning" -> s.lightning = bool(v);
                case "damage", "hurt" -> s.damage = num(v, key);
                case "pitch" -> s.pitch = num(v, key);
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
                    + "colors, size, count, dot, look, form, arc, width, particle, vary, gravity, drag, turbulence, plane, start, anchor, moves, speed, "
                    + "ticks, every, fill, lines, points, sound, volume, pitch, delay, shake, lightning, damage");
            }
        }
        if ("points".equals(s.shape) && (s.points == null || s.points.isEmpty())) {
            throw new IllegalArgumentException("shape 'points' needs points: [[u, v], ...]");
        }
        return s.clamped();
    }

    private static boolean bool(Object v) {
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v).trim());
    }

    /**
     * What a bow shoots. kind 'arrow': a thing that flies (the arrow shape made with the bow, or a
     * normal arrow): it may point along its flight, spin or keep still; stick where it hits or vanish
     * with a small burst; fly straight without gravity; be drawn bigger (size, blocks) with a hitbox
     * of about half that. kind 'beam': no arrow flies; a ray of light reaches at once up to 64 blocks
     * and stops at the first thing it meets (it never goes through), hitting like an arrow shot with
     * the same draw.
     */
    public static final class Shot {
        public static final List<String> KINDS = List.of("arrow", "beam");
        /** flight: along its flight; spin: turning flat like a shuriken; tumble: end over end; roll: round its flight; fixed. */
        public static final List<String> FACINGS = List.of("flight", "spin", "tumble", "roll", "fixed");
        /** Shapes drawn by the mod (smooth and thin), instead of a modelled one. */
        public static final List<String> MODELS = List.of("crescent", "shuriken", "disc", "ring", "star", "orb", "arrowhead", "feather");
        public static final List<String> HITS = List.of("stick", "vanish");
        public static final List<String> ORDERS = List.of("random", "cycle");
        public static final int MAX_TEXT = 64;
        public static final double RANGE = 64;
        public String kind = "arrow";
        public String facing = "flight";
        public String hit = "stick";
        public boolean gravity = true;
        /** Drawn length of the arrow shape in blocks (0.25-2); 0 = as designed. */
        public double size = 0;
        /** Beam (and the vanish burst): colour, colour it fades to, width (blocks), how long it shows (ticks). */
        public int color = 0xFFFFFF;
        public int color2 = -1;
        public double width = 0.25;
        public int ticks = 8;
        /** Beam: how visible it is, 0 (invisible: only what it hits shows) to 1. */
        public double opacity = 1;
        /** Letters shot instead of an arrow shape: each shot is one character of this (drawn in the game's font). */
        public String text = "";
        /** With several arrow shapes or letters: 'random' or 'cycle' (in turn). */
        public String order = "random";
        /** A shape drawn by the mod (MODELS), or "" for the modelled one. */
        public String model = "";
        /** Degrees the shape is turned round its flight: 0 = lying flat (a sideways wave), 90 = standing up. */
        public double tilt = 0;
        /**
         * How a flat shape faces as it flies: 'horizontal' (가로: it faces the way it goes, spread left and right, so a
         * crescent is a wide arc seen from behind), 'vertical' (세로: the same, spread up and down), or 'flat' / ""
         * (lying flat like a skimming stone; tilt turns it round its flight).
         */
        public String orient = "";
        public static final List<String> ORIENTS = List.of("horizontal", "vertical", "flat");
        /** Built-in shapes: how thick (0.02-0.3 of its size). */
        public double thickness = 0.06;
        /** More projectiles with each shot (or each swing of a melee weapon), each hurting half as much. */
        public List<Extra> extras = new ArrayList<>();

        /** All extra projectiles of one shot, at most MAX_EXTRAS. */
        public int extraCount() {
            int n = 0;
            if (extras != null) {
                for (Extra e : extras) {
                    n += e.count;
                }
            }
            return n;
        }

        public boolean hasExtras() {
            return extras != null && !extras.isEmpty();
        }

        /** The characters of text, one per shot. */
        public int[] letters() {
            return text == null ? new int[0] : text.codePoints().filter(c -> !Character.isWhitespace(c)).toArray();
        }

        public boolean beam() {
            return "beam".equals(kind);
        }

        public boolean vanishes() {
            return "vanish".equals(hit);
        }

        /** The hitbox (blocks): about half the drawn size, never smaller than a normal arrow's. */
        public double hitbox() {
            return size <= 0 ? 0.5 : Math.max(0.5, size * 0.5);
        }

        public Shot clamped() {
            kind = KINDS.contains(kind) ? kind : "arrow";
            facing = FACINGS.contains(facing) ? facing : "flight";
            hit = HITS.contains(hit) ? hit : "stick";
            size = size <= 0 || Double.isNaN(size) ? 0 : clamp(size, 0.25, 2);
            color &= 0xFFFFFF;
            color2 = color2 < 0 ? -1 : color2 & 0xFFFFFF;
            width = clamp(width, 0.05, 1);
            ticks = (int) clamp(ticks, 4, 20);
            opacity = clamp(opacity, 0, 1);
            order = ORDERS.contains(order) ? order : "random";
            model = model != null && MODELS.contains(model) ? model : "";
            tilt = clamp(tilt, -90, 90);
            orient = orient != null && ORIENTS.contains(orient) ? orient : "";
            thickness = clamp(thickness, 0.02, 0.3);
            if (text == null) {
                text = "";
            }
            // Printable characters only, at most MAX_TEXT.
            StringBuilder t = new StringBuilder();
            text.codePoints().filter(c -> !Character.isISOControl(c)).limit(MAX_TEXT).forEach(t::appendCodePoint);
            text = t.toString();
            // At most MAX_EXTRA_GROUPS groups and MAX_EXTRAS projectiles in all.
            List<Extra> kept = new ArrayList<>();
            int left = MAX_EXTRAS;
            if (extras != null) {
                for (Extra e : extras) {
                    if (e == null || kept.size() >= MAX_EXTRA_GROUPS || left <= 0) {
                        continue;
                    }
                    e.clamped();
                    e.count = Math.min(e.count, left);
                    left -= e.count;
                    kept.add(e);
                }
            }
            extras = kept;
            return this;
        }

        /** From the AI script: [kind: 'beam', color: '#ffe060', width: 0.3] or [facing: 'spin', hit: 'vanish', gravity: false, size: 0.5]. */
        public static Shot fromMap(Map<?, ?> map) {
            Shot s = new Shot();
            if (map != null) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = String.valueOf(e.getKey()).trim();
                    Object v = e.getValue();
                    switch (key) {
                        case "kind", "type" -> s.kind = word(v, KINDS, "shot kind");
                        case "facing", "face", "turn" -> s.facing = word(v, FACINGS, "shot facing");
                        case "hit", "onHit", "on_hit" -> s.hit = word(v, HITS, "shot hit");
                        case "gravity" -> s.gravity = bool(v);
                        case "size" -> s.size = num(v, key);
                        case "color", "colour" -> s.color = color(v);
                        case "color2", "colour2" -> s.color2 = color(v);
                        case "width" -> s.width = num(v, key);
                        case "ticks", "time" -> s.ticks = (int) Math.round(num(v, key));
                        case "opacity", "alpha" -> s.opacity = num(v, key);
                        case "text", "letters" -> s.text = String.valueOf(v);
                        case "order" -> s.order = word(v, ORDERS, "shot order");
                        case "model", "shape" -> s.model = word(v, MODELS, "shot model");
                        case "tilt" -> s.tilt = num(v, key);
                        case "orient", "facingAs", "direction" -> s.orient = switch (String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT)) {
                            case "가로", "horizontal", "wide", "across" -> "horizontal";
                            case "세로", "vertical", "upright", "standing" -> "vertical";
                            case "눕힘", "flat", "lying", "skim" -> "flat";
                            default -> throw new IllegalArgumentException("shot orient must be 'horizontal' (가로), 'vertical' (세로) or 'flat', got '" + v + "'");
                        };
                        case "thickness" -> s.thickness = num(v, key);
                        case "extra", "extras" -> {
                            List<?> list = v instanceof List<?> l ? l : List.of(v);
                            if (!list.isEmpty() && !(list.get(0) instanceof Map<?, ?>)) {
                                list = List.of(v); // one group written without the outer list
                            }
                            for (Object o : list) {
                                if (!(o instanceof Map<?, ?> m)) {
                                    throw new IllegalArgumentException("extra is a list of groups like [[count: 2, path: 'spiral']]");
                                }
                                s.extras.add(Extra.fromMap(m));
                            }
                        }
                        default -> throw new IllegalArgumentException("unknown shot setting '" + key
                            + "'; use kind, facing, hit, gravity, size, color, color2, width, ticks, opacity, text, order, extra, model, orient, tilt, thickness");
                    }
                }
            }
            return s.clamped();
        }
    }

    public static final int MAX_EXTRAS = 12;
    public static final int MAX_EXTRA_GROUPS = 4;

    /**
     * A group of extra projectiles: how many and which way they go. spread: a fan; ring: a cone
     * all round; spiral: turning round the main shot as it flies; trail: one after another behind
     * it; arc: curving out and back into where it is aimed; homing: after what it is aimed at;
     * rain: falling from the sky onto where it is aimed; split: bursting out where the main shot
     * lands. Each hurts half as much as the main shot.
     */
    public static final class Extra {
        public static final List<String> PATHS = List.of("spread", "ring", "spiral", "trail", "arc", "homing", "rain", "split");
        public int count = 2;
        public String path = "spread";
        /** spread / ring / arc: degrees out from the aim; split: how wide the burst is. 0 = the path's own default. */
        public double angle = 0;
        /** spiral: how far from the main shot; rain: how wide the area. 0 = default. */
        public double radius = 0;
        /** spiral: turns per second. 0 = default. */
        public double turns = 0;
        /** Ticks after the shot before they go. */
        public int delay = 0;
        /** Which arrow shape (1-8) or 0 = the same as the main shot. */
        public int shape = 0;
        /** Drawn size (0 = as the main shot). */
        public double size = 0;

        public Extra clamped() {
            count = (int) clamp(count, 1, 8);
            path = PATHS.contains(path) ? path : "spread";
            angle = angle <= 0 || Double.isNaN(angle) ? 0 : clamp(angle, 2, 90);
            radius = radius <= 0 || Double.isNaN(radius) ? 0 : clamp(radius, 0.2, 4);
            turns = turns <= 0 || Double.isNaN(turns) ? 0 : clamp(turns, 0.2, 5);
            delay = (int) clamp(delay, 0, 40);
            shape = (int) clamp(shape, 0, 8);
            size = size <= 0 || Double.isNaN(size) ? 0 : clamp(size, 0.25, 2);
            return this;
        }

        public double angleOr(double fallback) {
            return angle > 0 ? angle : fallback;
        }

        public double radiusOr(double fallback) {
            return radius > 0 ? radius : fallback;
        }

        public double turnsOr(double fallback) {
            return turns > 0 ? turns : fallback;
        }

        public static Extra fromMap(Map<?, ?> map) {
            Extra x = new Extra();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey()).trim();
                Object v = e.getValue();
                switch (key) {
                    case "count", "n" -> x.count = (int) Math.round(num(v, key));
                    case "path", "way" -> x.path = word(v, PATHS, "extra path");
                    case "angle" -> x.angle = num(v, key);
                    case "radius" -> x.radius = num(v, key);
                    case "turns", "speed" -> x.turns = num(v, key);
                    case "delay" -> x.delay = (int) Math.round(num(v, key));
                    case "shape" -> x.shape = (int) Math.round(num(v, key));
                    case "size" -> x.size = num(v, key);
                    default -> throw new IllegalArgumentException("unknown extra setting '" + key
                        + "'; use count, path, angle, radius, turns, delay, shape, size");
                }
            }
            return x.clamped();
        }
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
