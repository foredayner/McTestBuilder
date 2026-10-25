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
    /** Bows: particles following the arrow in flight. */
    public static final String TRAIL = "aib_fx_trail";
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

    /** Last swing effect per player (server ticks), so a modified client cannot flood effects. */
    private static final java.util.Map<java.util.UUID, Integer> LAST_SWING = new java.util.HashMap<>();

    /**
     * A player swung an equipment weapon (told by their client when the attack key is pressed and
     * the attack is charged): play its attack effect in front of them, on whatever they aim at
     * within reach, or about two blocks ahead.
     */
    public static void onSwing(net.minecraft.server.network.ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof com.example.content.GearItem gear) || gear.kind().worn()
            || gear.kind() == GearKind.BOW || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int now = player.getServer() == null ? player.age : player.getServer().getTicks();
        int interval = Math.max(4, Math.round(player.getAttackCooldownProgressPerTick() * 0.8F));
        Integer last = LAST_SWING.get(player.getUuid());
        if (last != null && now - last >= 0 && now - last < interval) {
            return;
        }
        LAST_SWING.put(player.getUuid(), now);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        double reach = player.getEntityInteractionRange();
        Vec3d center;
        net.minecraft.util.hit.EntityHitResult aimed = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, eye,
            eye.add(look.multiply(reach)), player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0),
            e -> e instanceof LivingEntity && !e.isSpectator() && e.canHit(), reach * reach);
        if (aimed != null) {
            center = aimed.getEntity().getPos();
        } else {
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize();
            center = player.getPos().add(flat.multiply(2.0));
        }
        Vec3d from = eye.add(0, -0.4, 0).add(look.multiply(0.6));
        play(world, player.getUuid(), stack, center, from, look, sizeFor(stack));
    }

    /** Big weapons get bigger effects: about the weapon's length over a normal sword's (1-2.5x). */
    private static double sizeFor(ItemStack stack) {
        BuildPlan model = GearItems.modelOf(stack);
        if (model == null || model.parts.isEmpty()) {
            return 1;
        }
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (BuildPlan.Part p : model.parts) {
            if (p.group != BuildPlan.ARROW_GROUP) {
                low = Math.min(low, p.y - p.sy / 2);
                high = Math.max(high, p.y + p.sy / 2);
            }
        }
        return high > low ? Math.max(1, Math.min(2.5, (high - low) / 1.5)) : 1;
    }

    /** All hit effects of {@code stack}: plain bursts, and shaped ones (one set per wielder at a time). */
    private static void play(ServerWorld world, java.util.UUID who, ItemStack stack, Vec3d center, Vec3d from, Vec3d look) {
        play(world, who, stack, center, from, look, 1);
    }

    private static void play(ServerWorld world, java.util.UUID who, ItemStack stack, Vec3d center, Vec3d from, Vec3d look,
                             double scale) {
        boolean busy = HitEffects.busy(who);
        for (String key : new String[] { HIT, HIT + 2, HIT + 3 }) {
            BuildPlan.Fx fx = read(stack, key);
            ParticleEffect effect = fx == null ? null : particle(fx);
            if (effect == null) {
                continue;
            }
            if (scale > 1) {
                BuildPlan.Fx big = new BuildPlan.Fx(fx.particle, fx.color, (int) Math.min(12, Math.round(fx.count * scale)));
                big.pattern = fx.pattern;
                big.size = Math.min(4, fx.size * scale);
                fx = big;
            }
            if (!"burst".equals(fx.pattern)) {
                if (!busy) {
                    HitEffects.startAt(world, who, center, from, look, fx, effect, true);
                    continue;
                }
                fx = new BuildPlan.Fx(fx.particle, fx.color, fx.count);
            }
            double spread = 0.35 * scale;
            world.spawnParticles(effect, center.x, center.y + 1.0, center.z, fx.count * 3, spread, 0.4 * scale, spread, 0.08);
        }
    }

    /** One tick of a flying arrow's trail. */
    public static void arrowTrail(ServerWorld world, com.example.content.AibArrowEntity arrow, BuildPlan.Fx fx) {
        ParticleEffect effect = particle(fx);
        if (effect != null) {
            world.spawnParticles(effect, arrow.getX(), arrow.getY(), arrow.getZ(), fx.count, 0.04, 0.04, 0.04, 0.0);
        }
    }

    /** An arrow from an AI bow landed at {@code at}: the bow's hit effects play there. */
    public static void arrowImpact(ServerWorld world, com.example.content.AibArrowEntity arrow, Vec3d at) {
        Vec3d dir = arrow.getVelocity().lengthSquared() < 1e-6 ? arrow.getRotationVec(1.0F) : arrow.getVelocity().normalize();
        play(world, arrow.getUuid(), arrow.bow(), at, at.add(0, 0.6, 0).subtract(dir.multiply(1.2)), dir);
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
