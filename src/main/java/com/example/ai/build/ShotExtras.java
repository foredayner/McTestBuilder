package com.example.ai.build;

import com.example.content.AibArrowEntity;
import com.example.content.GearBowItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Extra projectiles of a shot (Spark.Shot extras): from a bow's arrow, a beam, or a melee
 * weapon's swing. They are real projectiles that hurt half as much as the main shot (a bow's
 * extras: half an arrow; a melee weapon's: half its damage), cost no arrows and cannot be picked
 * up. The ways they go: spread, ring, spiral, trail, arc, homing, rain (from the sky onto where
 * the shot is aimed) and split (bursting out where the main shot lands).
 */
public final class ShotExtras {

    /** A melee weapon's extras fly this fast (blocks per tick). */
    static final double SWING_SPEED = 1.6;

    private ShotExtras() {
    }

    /** What the shooter aims at (up to 64 blocks): the point, and the entity if there is one. */
    private static Object[] aim(ServerWorld world, LivingEntity shooter) {
        HitResult hit = GearBowItem.beamTarget(world, shooter);
        return new Object[] { hit.getPos(), hit instanceof EntityHitResult e ? e.getEntity() : null };
    }

    /** A bow just made its main arrow: the extras go a moment later, when the arrow has its speed. */
    public static void afterShot(ServerWorld world, LivingEntity shooter, AibArrowEntity main) {
        Spark.Shot shot = main.shot();
        if (shot == null || !shot.hasExtras() || main.isExtra()) {
            return;
        }
        Object[] aim = aim(world, shooter);
        EffectDamage.later(0, () -> {
            if (main.getVelocity().lengthSquared() > 1e-6) {
                // Each extra hurts half as much as this arrow.
                launch(world, shooter, main.bow(), main.getPos(), main.getVelocity(), shot, (Vec3d) aim[0], (Entity) aim[1],
                    main.variant(), main.getVelocity().length() * 2.0 * Spark.MAX_DAMAGE, null, 0);
            }
        });
    }

    /** A beam shot: the extras fly from the bow toward where the beam stopped. */
    public static void afterBeam(ServerWorld world, LivingEntity shooter, ItemStack bow, Vec3d muzzle, Vec3d look, float pull,
                                 Spark.Shot shot, HitResult hit) {
        if (shot == null || !shot.hasExtras()) {
            return;
        }
        // Slower than the beam, so they are seen; each hurts half as much as the beam (an arrow with the same draw).
        launch(world, shooter, bow, muzzle, look.normalize().multiply(1.5), shot, hit.getPos(),
            hit instanceof EntityHitResult e ? e.getEntity() : null, 0, pull * 3.0 * 2.0 * Spark.MAX_DAMAGE, null, 0);
    }

    /** A melee weapon was swung: its extras leave when the swing is done, and pass the one it hit. */
    public static void fromSwing(ServerWorld world, LivingEntity wielder, ItemStack weapon, Spark.Shot shot, Entity hit, int lead) {
        Object[] aim = aim(world, wielder);
        Vec3d look = wielder.getRotationVec(1.0F);
        Vec3d from = wielder.getEyePos().add(0, -0.2, 0).add(look.multiply(0.6));
        double damage = EffectDamage.weaponDamage(wielder) * Spark.MAX_DAMAGE;
        launch(world, wielder, weapon, from, look.multiply(SWING_SPEED), shot, (Vec3d) aim[0], (Entity) aim[1], 0, damage, hit, lead);
    }

    /** Where the main shot landed: the extras that burst out from there. */
    public static void split(ServerWorld world, AibArrowEntity main, Vec3d at) {
        Spark.Shot shot = main.shot();
        if (shot == null || !shot.hasExtras() || !(main.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        for (Spark.Extra e : shot.extras) {
            if (!"split".equals(e.path)) {
                continue;
            }
            EffectDamage.later(e.delay, () -> {
                double spread = Math.toRadians(e.angleOr(50));
                for (int i = 0; i < e.count; i++) {
                    double around = 2 * Math.PI * i / e.count;
                    Vec3d dir = new Vec3d(Math.cos(around) * Math.sin(spread), Math.cos(spread), Math.sin(around) * Math.sin(spread));
                    spawn(world, owner, main.bow(), at, dir.multiply(0.9), plain("split", e), variantOf(main.variant(), e),
                        GearEffects.ARROW_DAMAGE * Spark.MAX_DAMAGE, null);
                }
            });
        }
    }

    private static AibArrowEntity.Motion plain(String path, Spark.Extra e) {
        AibArrowEntity.Motion m = new AibArrowEntity.Motion();
        m.path = path;
        m.size = e.size;
        return m;
    }

    private static int variantOf(int main, Spark.Extra e) {
        return e.shape > 0 ? e.shape - 1 : main;
    }

    static void launch(ServerWorld world, LivingEntity shooter, ItemStack weapon, Vec3d from, Vec3d velocity, Spark.Shot shot, Vec3d target,
                       Entity targetEntity, int mainVariant, double damage, Entity skip, int lead) {
        for (Spark.Extra e : shot.extras) {
            if ("split".equals(e.path)) {
                continue; // where it lands
            }
            if ("trail".equals(e.path)) {
                // One after another along the same line.
                for (int i = 0; i < e.count; i++) {
                    EffectDamage.later(lead + e.delay + (i + 1) * 3, () ->
                        spawn(world, shooter, weapon, from, velocity, plain("trail", e), variantOf(mainVariant, e), damage, skip));
                }
                continue;
            }
            if ("rain".equals(e.path)) {
                for (int i = 0; i < e.count; i++) {
                    double r = e.radiusOr(2.5);
                    double a = world.random.nextDouble() * Math.PI * 2, d = Math.sqrt(world.random.nextDouble()) * r;
                    Vec3d at = target.add(Math.cos(a) * d, 14, Math.sin(a) * d);
                    EffectDamage.later(lead + e.delay + i * 2, () ->
                        spawn(world, shooter, weapon, at, new Vec3d(0, -1.4, 0), plain("rain", e), variantOf(mainVariant, e), damage, skip));
                }
                continue;
            }
            EffectDamage.later(lead + e.delay, () -> {
                for (int i = 0; i < e.count; i++) {
                    Vec3d[] go = start(e, i, from, velocity);
                    AibArrowEntity.Motion m = plain(e.path, e);
                    if ("spiral".equals(e.path)) {
                        m.bx = velocity.x;
                        m.by = velocity.y;
                        m.bz = velocity.z;
                        m.gravity = shot.gravity;
                        m.radius = e.radiusOr(0.5);
                        m.rate = e.turnsOr(2) * 2 * Math.PI / 20;
                        m.phase = 2 * Math.PI * i / e.count;
                    } else if ("arc".equals(e.path) || "homing".equals(e.path)) {
                        m.tx = target.x;
                        m.ty = target.y;
                        m.tz = target.z;
                        m.target = "homing".equals(e.path) && targetEntity != null ? targetEntity.getId() : -1;
                    }
                    spawn(world, shooter, weapon, go[0], go[1], m, variantOf(mainVariant, e), damage, skip);
                }
            });
        }
    }

    /** Where extra number i of a group starts and how it moves at first: {position, velocity}. */
    static Vec3d[] start(Spark.Extra e, int i, Vec3d from, Vec3d velocity) {
        Vec3d d = velocity.normalize();
        double speed = velocity.length();
        Vec3d u = d.crossProduct(new Vec3d(0, 1, 0));
        u = u.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : u.normalize();
        Vec3d v = d.crossProduct(u).normalize();
        int n = e.count;
        return switch (e.path) {
            case "spread" -> {
                // A fan side to side: -angle .. +angle (one alone goes to one side).
                double a = Math.toRadians(e.angleOr(15)) * (n == 1 ? 1 : -1 + 2.0 * i / (n - 1));
                yield new Vec3d[] { from, turn(d, v, a).multiply(speed) };
            }
            case "ring", "arc", "homing" -> {
                // Out from the aim: a ring evenly all round; arcs and homing ones over the top and to the sides
                // (never down into the ground), wider, and curving back in.
                double out = Math.toRadians(e.angleOr("ring".equals(e.path) ? 10 : "arc".equals(e.path) ? 40 : 25));
                double around = "ring".equals(e.path) ? 2 * Math.PI * i / n : -Math.PI / 2 + Math.PI * (i + 0.5) / n;
                Vec3d axis = u.multiply(Math.cos(around)).add(v.multiply(Math.sin(around)));
                yield new Vec3d[] { from, turn(d, axis, out).multiply(speed) };
            }
            case "spiral" -> {
                double phase = 2 * Math.PI * i / n;
                Vec3d off = u.multiply(Math.cos(phase)).add(v.multiply(Math.sin(phase))).multiply(e.radiusOr(0.5));
                yield new Vec3d[] { from.add(off), velocity };
            }
            default -> new Vec3d[] { from, velocity };
        };
    }

    /** Turn a direction round an axis (perpendicular to it) by an angle. */
    static Vec3d turn(Vec3d d, Vec3d axis, double angle) {
        Vec3d k = axis.normalize();
        return d.multiply(Math.cos(angle)).add(k.crossProduct(d).multiply(Math.sin(angle)))
            .add(k.multiply(k.dotProduct(d) * (1 - Math.cos(angle))));
    }

    /** damage: how much it hurts when it hits (an arrow's damage is its speed times its damage setting). */
    private static void spawn(ServerWorld world, LivingEntity shooter, ItemStack weapon, Vec3d at, Vec3d velocity, AibArrowEntity.Motion m,
                              int variant, double damage, Entity skip) {
        if (shooter.isRemoved() || shooter.getWorld() != world) {
            return;
        }
        AibArrowEntity arrow = new AibArrowEntity(world, shooter, new ItemStack(Items.ARROW), weapon);
        arrow.makeExtra(m, skip, variant);
        arrow.setPosition(at);
        arrow.setVelocity(velocity);
        arrow.setDamage(damage / Math.max(0.1, velocity.length()));
        world.spawnEntity(arrow);
    }
}
