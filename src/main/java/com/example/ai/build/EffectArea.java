package com.example.ai.build;

/**
 * Where a hurting effect layer (Spark.damage) reaches, worked out on the server from the same
 * settings the players' games draw it with: a swipe is a fan in front of the wielder, a
 * shockwave a ring spreading over the ground, a beam a line, a pillar or bolt a column, a disc a
 * flat circle, dots a ball (one flying forward moves). Pure, so it can be tested without a world.
 * Vectors are {x, y, z}.
 */
public final class EffectArea {

    public enum Kind { FAN, RING, LINE, COLUMN, DISC, BALL, NONE }

    public final Kind kind;
    final double[] origin;
    final double[] dir;
    final double[] wielder;
    final double size;
    final double width;
    final double arc;
    final double length;
    final double speed;
    final double drag;
    /** Ticks the area lasts (checked every tick); 1 = once. */
    public final int ticks;

    private EffectArea(Kind kind, double[] origin, double[] dir, double[] wielder, double size, double width, double arc, double length,
                       double speed, double drag, int ticks) {
        this.kind = kind;
        this.origin = origin;
        this.dir = norm(dir);
        this.wielder = wielder == null ? origin : wielder;
        this.size = size;
        this.width = width;
        this.arc = arc;
        this.length = length;
        this.speed = speed;
        this.drag = drag;
        this.ticks = ticks;
    }

    /** The area of an effect starting at origin going along dir, drawn at scale; wielder = the wielder's eyes (swipes). */
    public static EffectArea of(Spark s, double[] origin, double[] dir, double[] wielder, double scale) {
        double size = s.size * scale;
        return switch (s.form) {
            case "swipe" -> new EffectArea(Kind.FAN, origin, dir, wielder, size, s.width, s.arc, Math.max(2.5, size * 1.6), 0, 0, 1);
            case "shockwave" -> new EffectArea(Kind.RING, origin, dir, wielder, size, s.width, 0, 0, 0, 0, Math.max(1, s.ticks));
            case "beam" -> new EffectArea(Kind.LINE, origin, dir, wielder, size, s.width, 0, s.reach > 0 ? s.reach : size * 4, 0, 0, 1);
            case "pillar" -> new EffectArea(Kind.COLUMN, origin, new double[] { 0, 1, 0 }, wielder, size, s.width, 0, size * 4, 0, 0, 1);
            case "bolt" -> new EffectArea(Kind.COLUMN, add(origin, new double[] { 0, -0.5, 0 }), new double[] { 0, 1, 0 }, wielder,
                size, Math.max(1.2, 0.6 + size * 1.6), 0, 3.5, 0, 0, 1);
            case "disc" -> new EffectArea(Kind.DISC, origin, dir, wielder, size, s.width, 0, 0, 0, 0, 1);
            case "ribbon" -> new EffectArea(Kind.NONE, origin, dir, wielder, size, s.width, 0, 0, 0, 0, 0);
            default -> {
                boolean flies = s.moves.contains("forward") || s.moves.contains("back");
                double sp = s.moves.contains("back") && !s.moves.contains("forward") ? -s.speed : s.speed;
                yield new EffectArea(Kind.BALL, origin, dir, wielder, size, s.width, 0, 0, flies ? sp : 0, s.drag,
                    flies ? Math.max(1, s.ticks) : 1);
            }
        };
    }

    /** Where the middle is after some seconds (only a flying ball moves). */
    public double[] center(double seconds) {
        if (speed == 0) {
            return origin;
        }
        double k = drag * 5;
        double d = k < 1e-3 ? seconds * speed : speed * (1 - Math.exp(-k * seconds)) / k;
        return add(origin, mul(dir, d));
    }

    /** A box around everything it may reach at this time: {x, y, z, radius}. */
    public double[] bound(double seconds) {
        double[] c = center(seconds);
        double r = switch (kind) {
            case FAN -> length + 1;
            case RING -> size * 1.2 + 1;
            case LINE -> length / 2 + 1;
            case COLUMN -> Math.max(width, length) + 1;
            case DISC, BALL -> size + 1;
            case NONE -> 0;
        };
        if (kind == Kind.FAN) {
            c = wielder;
        } else if (kind == Kind.LINE || kind == Kind.COLUMN) {
            c = add(origin, mul(dir, length / 2));
        }
        return new double[] { c[0], c[1], c[2], r };
    }

    /**
     * Whether a target (its middle at p, about halfWidth wide and halfHeight tall) is reached at
     * this many seconds after the start.
     */
    public boolean hits(double[] p, double halfWidth, double halfHeight, double seconds) {
        switch (kind) {
            case FAN -> {
                double[] to = sub(p, wielder);
                double dist = Math.sqrt(to[0] * to[0] + to[2] * to[2]);
                if (dist > length + halfWidth || Math.abs(to[1]) > 2 + halfHeight) {
                    return false;
                }
                if (dist < 0.8) {
                    return true;
                }
                double[] flat = norm(new double[] { dir[0], 0, dir[2] });
                double cos = (to[0] * flat[0] + to[2] * flat[2]) / dist;
                return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) <= arc / 2 + 10;
            }
            case RING -> {
                double t = Math.min(1, seconds * 20 / ticks);
                double eased = 1 - Math.pow(1 - t, 3);
                double outer = size * (0.15 + eased);
                double inner = Math.max(0, outer - size * width * (1 - 0.6 * t)) - 0.6;
                double[] to = sub(p, origin);
                double dist = Math.sqrt(to[0] * to[0] + to[2] * to[2]);
                return Math.abs(to[1]) <= 1 + halfHeight && dist <= outer + halfWidth && dist >= inner - halfWidth;
            }
            case LINE, COLUMN -> {
                double radius = kind == Kind.COLUMN && width > 1 ? width : Math.max(0.4, size * width * 0.6);
                return distanceToSegment(p, origin, add(origin, mul(dir, length))) <= radius + Math.max(halfWidth, halfHeight * 0.5);
            }
            case DISC -> {
                double[] to = sub(p, origin);
                return Math.abs(to[1]) <= 1 + halfHeight && Math.sqrt(to[0] * to[0] + to[2] * to[2]) <= size + halfWidth;
            }
            case BALL -> {
                double[] c = center(seconds);
                double[] to = sub(p, c);
                double dy = Math.max(0, Math.abs(to[1]) - halfHeight);
                return Math.sqrt(to[0] * to[0] + dy * dy + to[2] * to[2]) <= size + halfWidth;
            }
            default -> {
                return false;
            }
        }
    }

    static double distanceToSegment(double[] p, double[] a, double[] b) {
        double[] ab = sub(b, a);
        double len2 = dot(ab, ab);
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, dot(sub(p, a), ab) / len2));
        double[] q = add(a, mul(ab, t));
        double[] d = sub(p, q);
        return Math.sqrt(dot(d, d));
    }

    static double[] add(double[] a, double[] b) {
        return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    static double[] mul(double[] a, double k) {
        return new double[] { a[0] * k, a[1] * k, a[2] * k };
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-9 ? new double[] { 0, 0, 1 } : mul(a, 1 / l);
    }
}
