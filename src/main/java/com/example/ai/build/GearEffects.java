package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.example.content.GearKind;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Particle effects of AI equipment (no sounds), sent by the server so every player sees them: a burst
 * where a weapon hits, and a light, steady stream while it is held or worn. Only what the
 * order's effect fields asked for is stored on the item.
 */
public final class GearEffects {

    public static final String HIT = "aib_fx_hit";
    public static final String IDLE = "aib_fx_idle";
    /** Ticks between two puffs of the held / worn effect. */
    private static final int IDLE_INTERVAL = 5;

    private GearEffects() {
    }

    static void write(NbtCompound nbt, String key, BuildPlan.Fx fx) {
        if (fx == null || fx.particle == null || !MinecraftAI.PARTICLES.contains(fx.particle)) {
            return;
        }
        NbtCompound c = new NbtCompound();
        c.putString("p", fx.particle);
        c.putInt("c", fx.color & 0xFFFFFF);
        c.putInt("n", Math.max(1, Math.min(12, fx.count)));
        c.putString("t", fx.pattern == null ? "burst" : fx.pattern);
        c.putDouble("s", fx.size);
        nbt.put(key, c);
    }

    public static BuildPlan.Fx read(ItemStack stack, String key) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        NbtCompound nbt = data.copyNbt();
        if (!nbt.contains(key)) {
            return null;
        }
        NbtCompound c = nbt.getCompound(key);
        String p = c.getString("p");
        if (!MinecraftAI.PARTICLES.contains(p)) {
            return null;
        }
        BuildPlan.Fx fx = new BuildPlan.Fx(p, c.getInt("c"), c.getInt("n"));
        String t = c.getString("t");
        fx.pattern = MinecraftAI.PATTERNS.contains(t) ? t : "burst";
        fx.size = c.contains("s") ? Math.max(0.5, Math.min(4, c.getDouble("s"))) : 1;
        return fx;
    }

    public static ParticleEffect particle(BuildPlan.Fx fx) {
        if ("dust".equals(fx.particle)) {
            return new DustParticleEffect(fx.color, 1.2F);
        }
        ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.ofVanilla(fx.particle));
        return type instanceof SimpleParticleType simple ? simple : null;
    }

    /** A weapon hit {@code target}. */
    public static void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return;
        }
        boolean busy = HitEffects.busy(attacker);
        for (String key : new String[] { HIT, HIT + 2, HIT + 3 }) {
            BuildPlan.Fx fx = read(stack, key);
            ParticleEffect effect = fx == null ? null : particle(fx);
            if (effect == null) {
                continue;
            }
            if (!"burst".equals(fx.pattern)) {
                // Shaped effects: one set per attacker at a time (fast hits only get the plain burst).
                if (!busy) {
                    HitEffects.start(world, attacker, target, fx, effect, true);
                    continue;
                }
                fx = new BuildPlan.Fx(fx.particle, fx.color, fx.count);
            }
            burst(world, target, fx, effect);
        }
    }

    private static void burst(ServerWorld world, LivingEntity target, BuildPlan.Fx fx, ParticleEffect effect) {
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        world.spawnParticles(effect, at.x, at.y, at.z, fx.count * 3, target.getWidth() * 0.4, target.getHeight() * 0.3,
            target.getWidth() * 0.4, 0.08);
    }

    /** Every tick for an equipment stack in someone's inventory: puffs while held (or worn). */
    public static void onTick(ItemStack stack, Entity entity, boolean selected, GearKind kind) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof LivingEntity living)
            || entity.age % IDLE_INTERVAL != 0) {
            return;
        }
        boolean active = kind.worn() ? living.getEquippedStack(kind.slot) == stack : (selected || living.getOffHandStack() == stack);
        if (!active) {
            return;
        }
        BuildPlan.Fx fx = read(stack, IDLE);
        ParticleEffect effect = fx == null ? null : particle(fx);
        if (effect == null) {
            return;
        }
        float yaw = living.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        Vec3d base = living.getPos();
        Vec3d at;
        double spread;
        if (kind.slot == null) {
            // Around the held item: in front of the right hand, from the hand up along the item.
            at = base.add(0, living.getHeight() * 0.6, 0).add(right.multiply(0.4)).add(forward.multiply(0.4));
            spread = 0.25;
        } else if (kind.slot == net.minecraft.entity.EquipmentSlot.HEAD) {
            at = base.add(0, living.getStandingEyeHeight() + 0.3, 0);
            spread = 0.3;
        } else if (kind == GearKind.BACK) {
            at = base.add(0, living.getHeight() * 0.65, 0).subtract(forward.multiply(0.5));
            spread = 0.45;
        } else {
            at = base.add(0, living.getHeight() * 0.6, 0);
            spread = 0.35;
        }
        world.spawnParticles(effect, at.x, at.y, at.z, fx.count, spread, spread, spread, 0.01);
    }
}
