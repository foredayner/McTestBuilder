package com.example.ai.build;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shaped hit effects of AI weapons, played over a few ticks by the server so everyone sees them:
 * a shockwave ring, a cone of flame, a slash arc, a spiral, a pillar, a magic circle (also drawn
 * with glowing display pieces so its lines are solid) or a lightning bolt.
 */
public final class HitEffects {

    /** Tag on the magic circle's display pieces, so leftovers (after a crash) are removed on load. */
    public static final String TAG = "aib_fx";
    private static final int MAX_ACTIVE = 32;
    /** While true, our own new pieces are being added (not leftovers to remove). */
    private static boolean spawning;
    private static final List<Effect> ACTIVE = new ArrayList<>();
    /** One effect per attacker at a time: hits during an effect only get the plain burst. */
    private static final Map<UUID, Effect> BY_ATTACKER = new HashMap<>();

    private HitEffects() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!spawning && entity.getCommandTags().contains(TAG)) {
                boolean live = false;
                for (Effect e : ACTIVE) {
                    live |= e.pieces.contains(entity.getUuid());
                }
                if (!live) {
                    entity.discard();
                }
            }
        });
    }

    private static final class Effect {
        final ServerWorld world;
        final UUID attacker;
        final ParticleEffect particle;
        final BuildPlan.Fx fx;
        final Vec3d center;
        final Vec3d from;
        final Vec3d dir;
        final int duration;
        final List<UUID> pieces = new ArrayList<>();
        final double[] bolt;
        int age;

        Effect(ServerWorld world, UUID attacker, ParticleEffect particle, BuildPlan.Fx fx, Vec3d center, Vec3d from, Vec3d dir,
               int duration) {
            this.world = world;
            this.attacker = attacker;
            this.particle = particle;
            this.fx = fx;
            this.center = center;
            this.from = from;
            this.dir = dir;
            this.duration = duration;
            this.bolt = new double[18];
            for (int i = 0; i < bolt.length; i++) {
                bolt[i] = (world.random.nextDouble() - 0.5) * 0.9;
            }
        }
    }

    /** True while this attacker's last shaped effect is still playing. */
    public static boolean busy(LivingEntity attacker) {
        return busy(attacker.getUuid());
    }

    public static boolean busy(UUID who) {
        for (Effect e : ACTIVE) {
            if (e.attacker.equals(who) && e.age < e.duration) {
                return true;
            }
        }
        return false;
    }

    public static boolean start(ServerWorld world, LivingEntity attacker, LivingEntity target, BuildPlan.Fx fx, ParticleEffect particle) {
        return start(world, attacker, target, fx, particle, false);
    }

    /** together: part of a set started by the same hit (does not wait for the attacker's last effect). */
    public static boolean start(ServerWorld world, LivingEntity attacker, LivingEntity target, BuildPlan.Fx fx, ParticleEffect particle,
                                boolean together) {
        Vec3d look = attacker.getRotationVec(1.0F);
        Vec3d from = attacker.getEyePos().add(0, -0.4, 0).add(look.multiply(0.6));
        return startAt(world, attacker.getUuid(), target.getPos(), from, look, fx, particle, together);
    }

    /**
     * Play a shaped effect centred on {@code center} (feet level of where it lands); {@code from}
     * is where a cone or slash starts and {@code look} the direction it goes.
     */
    public static boolean startAt(ServerWorld world, UUID who, Vec3d center, Vec3d from, Vec3d look, BuildPlan.Fx fx,
                                  ParticleEffect particle, boolean together) {
        if (!together && busy(who)) {
            return false;
        }
        if (ACTIVE.size() >= MAX_ACTIVE) {
            return false;
        }
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize();
        int duration = switch (fx.pattern) {
            case "shockwave" -> 12;
            case "cone" -> 8;
            case "slash" -> 3;
            case "spiral" -> 16;
            case "pillar" -> 12;
            case "magic_circle" -> 36;
            case "lightning" -> 4;
            case "expand" -> 10;
            case "converge" -> 14;
            default -> 1;
        };
        Effect e = new Effect(world, who, particle, fx, center, from, flat, duration);
        if ("magic_circle".equals(fx.pattern)) {
            spawnCircle(e);
        }
        ACTIVE.add(e);
        BY_ATTACKER.put(who, e);
        return true;
    }

    private static void tick() {
        for (Iterator<Effect> it = ACTIVE.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            try {
                play(e);
            } catch (RuntimeException ignored) {
                e.age = e.duration;
            }
            e.age++;
            if (e.age >= e.duration) {
                for (UUID id : e.pieces) {
                    Entity piece = e.world.getEntity(id);
                    if (piece != null) {
                        piece.discard();
                    }
                }
                it.remove();
                BY_ATTACKER.remove(e.attacker, e);
            }
        }
    }

    private static void dot(Effect e, double x, double y, double z) {
        e.world.spawnParticles(e.particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
    }

    private static void play(Effect e) {
        double size = e.fx.size;
        double t = e.age / (double) Math.max(1, e.duration - 1);
        Vec3d c = e.center;
        switch (e.fx.pattern) {
            case "shockwave" -> {
                double r = 0.3 + size * t;
                int n = Math.min(64, Math.max(12, (int) (r * 14)));
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    dot(e, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r);
                }
            }
            case "cone" -> {
                // A stream of particles flying forward and spreading: a burst of flame from the swing.
                int n = Math.min(24, 6 + e.fx.count * 2);
                for (int i = 0; i < n; i++) {
                    double spread = 0.45;
                    double vx = e.dir.x + (e.world.random.nextDouble() - 0.5) * spread;
                    double vy = (e.world.random.nextDouble() - 0.3) * spread * 0.6;
                    double vz = e.dir.z + (e.world.random.nextDouble() - 0.5) * spread;
                    e.world.spawnParticles(e.particle, e.from.x, e.from.y, e.from.z, 0, vx, vy, vz, 0.15 + 0.12 * size);
                }
            }
            case "slash" -> {
                Vec3d side = new Vec3d(-e.dir.z, 0, e.dir.x);
                Vec3d mid = e.from.add(e.dir.multiply(0.4 + size * 0.5));
                int n = Math.min(48, (int) (20 * size));
                for (int i = 0; i <= n; i++) {
                    double a = Math.toRadians(-70 + 140.0 * i / n);
                    double tilt = 0.35 * Math.sin(a) + (e.age - 1) * 0.15;
                    Vec3d p = mid.add(side.multiply(Math.sin(a) * size)).add(e.dir.multiply((Math.cos(a) - 1) * size * 0.5));
                    dot(e, p.x, p.y + tilt, p.z);
                }
            }
            case "spiral" -> {
                double h = size * 1.6;
                for (int strand = 0; strand < 2; strand++) {
                    for (int k = 0; k < 3; k++) {
                        double s = (e.age * 3 + k) / (double) (e.duration * 3);
                        double a = s * 4 * Math.PI + strand * Math.PI;
                        double r = 0.35 + 0.25 * size * (1 - s);
                        dot(e, c.x + Math.cos(a) * r, c.y + s * h, c.z + Math.sin(a) * r);
                    }
                }
            }
            case "pillar" -> {
                double h = size * 2.2;
                int n = Math.min(30, 8 + e.fx.count * 2);
                for (int i = 0; i < n; i++) {
                    double a = e.world.random.nextDouble() * 2 * Math.PI;
                    double r = e.world.random.nextDouble() * 0.25 * size;
                    dot(e, c.x + Math.cos(a) * r, c.y + e.world.random.nextDouble() * h, c.z + Math.sin(a) * r);
                }
            }
            case "magic_circle" -> {
                if (e.age % 2 == 0) {
                    double turn = e.age * 0.06;
                    ring(e, c, size, 0);
                    ring(e, c, size * 0.62, turn);
                    star(e, c, size * 0.62, turn);
                }
                if (e.age % 3 == 0) {
                    turnCircle(e);
                }
            }
            case "expand", "converge" -> {
                // A shell of particles around the hit point (chest height) growing out or shrinking in.
                double grow = "expand".equals(e.fx.pattern) ? t : 1 - t;
                double r = 0.15 + size * grow;
                Vec3d mid = c.add(0, 1.0, 0);
                int n = Math.min(40, 14 + e.fx.count * 3);
                for (int i = 0; i < n; i++) {
                    // Random points spread evenly over the sphere.
                    double z = e.world.random.nextDouble() * 2 - 1;
                    double a = e.world.random.nextDouble() * 2 * Math.PI;
                    double ring = Math.sqrt(1 - z * z);
                    dot(e, mid.x + Math.cos(a) * ring * r, mid.y + z * r, mid.z + Math.sin(a) * ring * r);
                }
            }
            case "lightning" -> {
                if (e.age % 2 == 0) {
                    Vec3d top = c.add(0, 3 + size * 2, 0);
                    int segs = e.bolt.length / 2;
                    Vec3d prev = top;
                    for (int i = 1; i <= segs; i++) {
                        double f = i / (double) segs;
                        Vec3d next = top.lerp(c, f).add(i == segs ? 0 : e.bolt[2 * i - 2], 0, i == segs ? 0 : e.bolt[2 * i - 1]);
                        for (int k = 0; k < 6; k++) {
                            Vec3d p = prev.lerp(next, k / 6.0);
                            dot(e, p.x, p.y, p.z);
                        }
                        prev = next;
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void ring(Effect e, Vec3d c, double r, double offset) {
        int n = Math.min(60, Math.max(16, (int) (r * 18)));
        for (int i = 0; i < n; i++) {
            double a = offset + 2 * Math.PI * i / n;
            dot(e, c.x + Math.cos(a) * r, c.y + 0.08, c.z + Math.sin(a) * r);
        }
    }

    private static void star(Effect e, Vec3d c, double r, double offset) {
        // A five-pointed star (pentagram) inside the inner ring.
        for (int i = 0; i < 5; i++) {
            double a1 = offset + 2 * Math.PI * i / 5, a2 = offset + 2 * Math.PI * ((i + 2) % 5) / 5;
            for (int k = 0; k <= 8; k++) {
                double f = k / 8.0;
                double x = Math.cos(a1) * r * (1 - f) + Math.cos(a2) * r * f;
                double z = Math.sin(a1) * r * (1 - f) + Math.sin(a2) * r * f;
                dot(e, c.x + x, c.y + 0.08, c.z + z);
            }
        }
    }

    // ---------------------------------------------------------------- magic circle pieces

    private static final int RING_PIECES = 20;

    /** Glowing flat pieces on the ground: an outer ring and a star, which then turn slowly. */
    private static void spawnCircle(Effect e) {
        String block = concreteFor(e.fx.color);
        spawning = true;
        try {
            for (int i = 0; i < RING_PIECES + 5; i++) {
                String snbt = String.format(Locale.ROOT,
                    "{block_state:{Name:\"%s\"},brightness:{sky:15,block:15},transformation:%s,Tags:[\"%s\"]}",
                    block, transform(e, i, 0, 0.5f), TAG);
                var piece = new net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity(
                    net.minecraft.entity.EntityType.BLOCK_DISPLAY, e.world);
                piece.readNbt(net.minecraft.nbt.StringNbtReader.parse(snbt));
                piece.setPosition(e.center.x, e.center.y + 0.03, e.center.z);
                if (e.world.spawnEntity(piece)) {
                    e.pieces.add(piece.getUuid());
                }
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            // A bad piece description: the particles still draw the circle.
        } finally {
            spawning = false;
        }
    }

    private static void turnCircle(Effect e) {
        double t = e.age / (double) e.duration;
        // Grow in quickly, turn, shrink away at the end.
        float grow = (float) Math.min(1, Math.min(0.5 + t * 8, (1 - t) * 4));
        for (int i = 0; i < e.pieces.size(); i++) {
            Entity piece = e.world.getEntity(e.pieces.get(i));
            if (piece == null) {
                continue;
            }
            String snbt = "{start_interpolation:0,interpolation_duration:3,transformation:"
                + transform(e, i, e.age * 0.05 + 0.2, Math.max(0.01f, grow)) + "}";
            try {
                // Same as /data merge entity: write, merge, read back (keeps position and tags).
                net.minecraft.nbt.NbtCompound data = piece.writeNbt(new net.minecraft.nbt.NbtCompound());
                data.copyFrom(net.minecraft.nbt.StringNbtReader.parse(snbt));
                piece.readNbt(data);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
                // keep the piece as it is
            }
        }
    }

    /** Transformation of piece i (ring segment or star line) turned by {@code turn} radians, scaled by {@code grow}. */
    private static String transform(Effect e, int i, double turn, float grow) {
        double r = e.fx.size;
        double len;
        double cx;
        double cz;
        double angle;
        if (i < RING_PIECES) {
            double a = 2 * Math.PI * (i + 0.5) / RING_PIECES + turn;
            len = 2 * r * Math.sin(Math.PI / RING_PIECES) + 0.04;
            cx = Math.cos(a) * r;
            cz = Math.sin(a) * r;
            angle = a + Math.PI / 2;
        } else {
            int k = i - RING_PIECES;
            double ri = r * 0.62;
            double a1 = 2 * Math.PI * k / 5 - turn * 1.5, a2 = 2 * Math.PI * ((k + 2) % 5) / 5 - turn * 1.5;
            double x1 = Math.cos(a1) * ri, z1 = Math.sin(a1) * ri, x2 = Math.cos(a2) * ri, z2 = Math.sin(a2) * ri;
            len = Math.hypot(x2 - x1, z2 - z1);
            cx = (x1 + x2) / 2;
            cz = (z1 + z2) / 2;
            angle = Math.atan2(z2 - z1, x2 - x1);
        }
        cx *= grow;
        cz *= grow;
        float sx = (float) (len * grow), sy = 0.03f, sz = 0.07f;
        // Rotate around Y so the piece's length (X) follows the line, then centre it on (cx, cz).
        float half = (float) (-angle / 2);
        float qy = MathHelper.sin(half), qw = MathHelper.cos(half);
        // Box corner offset from its centre, turned: centre = translation + R * (size / 2).
        double hx = sx / 2, hz = sz / 2;
        double rx = Math.cos(-angle) * hx + Math.sin(-angle) * hz;
        double rz = -Math.sin(-angle) * hx + Math.cos(-angle) * hz;
        return String.format(Locale.ROOT,
            "{left_rotation:[0f,%.5ff,0f,%.5ff],right_rotation:[0f,0f,0f,1f],translation:[%.4ff,0f,%.4ff],scale:[%.4ff,%.4ff,%.4ff]}",
            qy, qw, cx - rx, cz - rz, sx, sy, sz);
    }

    /** The concrete colour closest to an RGB colour (glowing pieces of the magic circle). */
    static String concreteFor(int rgb) {
        String[] names = { "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black" };
        int[] colors = { 0xCFD5D6, 0xE06101, 0xA9309F, 0x2489C7, 0xF1AF15, 0x5EA918, 0xD5658F, 0x373A3E, 0x7D7D73, 0x157788,
            0x64209C, 0x2D2F8F, 0x603C20, 0x495B24, 0x8E2121, 0x080A0F };
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            long dr = ((rgb >> 16) & 255) - ((colors[i] >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((colors[i] >> 8) & 255);
            long db = (rgb & 255) - (colors[i] & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return "minecraft:" + names[best] + "_concrete";
    }
}
