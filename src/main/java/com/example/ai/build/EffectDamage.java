package com.example.ai.build;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Effects that hurt (Spark.damage): when a layer starts (after its delay, and the swing), the
 * server looks for what its area (EffectArea) reaches and hurts it by that part of the weapon's
 * damage. Never the wielder, the one hit directly (it has its normal hit), the wielder's pets or
 * armour stands; players only when the server allows fighting them. One swing (or one landing)
 * hurts a target by at most half the weapon's damage, whatever the layers add up to.
 * Also runs small jobs a few ticks later (extra projectiles that go after the shot).
 */
public final class EffectDamage {

    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Object[]> LATER = new ArrayList<>();

    private EffectDamage() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EffectDamage::tick);
    }

    /** Run something after some ticks on the server thread. */
    public static synchronized void later(int ticks, Runnable job) {
        LATER.add(new Object[] { new int[] { Math.max(0, ticks) }, job });
    }

    /** One swing or landing: who hurts, who was hit directly, the weapon's damage and what each target has taken. */
    public static final class Swing {
        final ServerWorld world;
        final LivingEntity attacker;
        final Entity direct;
        final double base;
        final Map<UUID, Double> used = new HashMap<>();

        Swing(ServerWorld world, LivingEntity attacker, Entity direct, double base) {
            this.world = world;
            this.attacker = attacker;
            this.direct = direct;
            this.base = base;
        }
    }

    private static final class Hit {
        final Swing swing;
        final EffectArea area;
        final double part;
        int wait;
        int age;
        final Set<UUID> done = new HashSet<>();

        Hit(Swing swing, EffectArea area, double part, int wait) {
            this.swing = swing;
            this.area = area;
            this.part = part;
            this.wait = wait;
        }
    }

    /** The damage of a melee weapon in this wielder's hand (its attack damage). */
    public static double weaponDamage(LivingEntity wielder) {
        return wielder.getAttributes().hasAttribute(EntityAttributes.ATTACK_DAMAGE) ? wielder.getAttributeValue(EntityAttributes.ATTACK_DAMAGE)
            : 2;
    }

    public static Swing swing(ServerWorld world, LivingEntity attacker, Entity direct, double base) {
        return new Swing(world, attacker, direct, base);
    }

    /** A hurting layer of this swing: starting at origin along dir, after some ticks. */
    public static synchronized void add(Swing swing, Spark s, Vec3d origin, Vec3d dir, Vec3d wielderEyes, double scale, int afterTicks) {
        if (s.damage <= 0 || swing.attacker == null) {
            return;
        }
        EffectArea area = EffectArea.of(s, vec(origin), vec(dir), wielderEyes == null ? null : vec(wielderEyes), scale);
        if (area.kind == EffectArea.Kind.NONE) {
            return;
        }
        HITS.add(new Hit(swing, area, s.damage, afterTicks));
    }

    private static double[] vec(Vec3d v) {
        return new double[] { v.x, v.y, v.z };
    }

    private static synchronized void tick(MinecraftServer server) {
        if (!LATER.isEmpty()) {
            List<Runnable> due = new ArrayList<>();
            LATER.removeIf(job -> {
                int[] left = (int[]) job[0];
                if (left[0]-- <= 0) {
                    due.add((Runnable) job[1]);
                    return true;
                }
                return false;
            });
            for (Runnable r : due) {
                try {
                    r.run();
                } catch (RuntimeException exception) {
                    System.out.println("[AI] A later effect failed: " + exception.getMessage());
                }
            }
        }
        HITS.removeIf(hit -> {
            if (hit.wait > 0) {
                hit.wait--;
                return false;
            }
            if (hit.swing.attacker.isRemoved() || hit.swing.attacker.getWorld() != hit.swing.world) {
                return true;
            }
            reach(hit);
            hit.age++;
            return hit.age >= hit.area.ticks;
        });
    }

    private static void reach(Hit hit) {
        double seconds = hit.age / 20.0;
        double[] b = hit.area.bound(seconds);
        Box box = new Box(b[0] - b[3], b[1] - b[3], b[2] - b[3], b[0] + b[3], b[1] + b[3], b[2] + b[3]);
        Swing swing = hit.swing;
        for (LivingEntity target : swing.world.getEntitiesByClass(LivingEntity.class, box, e -> mayHurt(swing, e))) {
            if (hit.done.contains(target.getUuid())) {
                continue;
            }
            double[] p = { target.getX(), target.getY() + target.getHeight() / 2, target.getZ() };
            if (!hit.area.hits(p, target.getWidth() / 2, target.getHeight() / 2, seconds)) {
                continue;
            }
            hit.done.add(target.getUuid());
            double taken = swing.used.getOrDefault(target.getUuid(), 0.0);
            double part = Math.min(hit.part, Spark.MAX_DAMAGE - taken);
            if (part <= 0.001) {
                continue;
            }
            swing.used.put(target.getUuid(), taken + part);
            target.damage(swing.world, sourceOf(swing), (float) (part * swing.base));
        }
    }

    private static DamageSource sourceOf(Swing swing) {
        return swing.attacker instanceof PlayerEntity player ? swing.world.getDamageSources().playerAttack(player)
            : swing.world.getDamageSources().mobAttack(swing.attacker);
    }

    /** Who an effect may hurt. */
    static boolean mayHurt(Swing swing, LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || e == swing.attacker || e == swing.direct || e instanceof ArmorStandEntity) {
            return false;
        }
        if (e instanceof Tameable pet && pet.getOwner() == swing.attacker) {
            return false;
        }
        if (e instanceof PlayerEntity target) {
            return swing.attacker instanceof PlayerEntity attacker && attacker.shouldDamagePlayer(target);
        }
        return true;
    }

    /** Tests: nothing waiting. */
    public static synchronized void clear() {
        HITS.clear();
        LATER.clear();
    }

    /** Tests: hurting layers still to check. */
    public static synchronized int pending() {
        return HITS.size();
    }
}
