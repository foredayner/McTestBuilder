package com.example.ai.build;

import com.example.content.GearItem;
import com.example.content.GearKind;
import com.example.network.AiNetworkPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colour-dot effects of AI equipment. The effects are stored on the item; the server only tells
 * nearby players "these effects play here now" (on a swing, a mob's hit, an arrow landing), and
 * every player's game draws the dots. Effects while held or worn and arrow trails need no
 * messages at all: each game sees the equipment and draws them itself.
 */
public final class GearEffects {

    /** Item data key: the equipment's effects as JSON (Spark.Effects). */
    public static final String KEY = "aib_fx";

    private GearEffects() {
    }

    static void write(NbtCompound nbt, Spark.Effects fx) {
        if (fx != null && !fx.isEmpty()) {
            nbt.putString(KEY, fx.toJson());
        }
    }

    /** The effects stored on a stack (empty if none). */
    public static Spark.Effects of(ItemStack stack) {
        NbtComponent data = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return new Spark.Effects();
        }
        return Spark.Effects.fromJson(data.copyNbt().getString(KEY));
    }

    /** Last swing effect per player (server ticks), so a modified client cannot flood effects. */
    private static final Map<UUID, Integer> LAST_SWING = new HashMap<>();

    /**
     * A player swung an equipment weapon (told by their client when the attack key is pressed and
     * the attack is charged). Returns how many effects were sent.
     */
    public static int onSwing(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GearItem gear) || gear.kind().worn()
            || gear.kind() == GearKind.BOW || !(player.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        Spark.Effects fx = of(stack);
        if (fx.attack.isEmpty()) {
            return 0;
        }
        int now = player.getServer() == null ? player.age : player.getServer().getTicks();
        int interval = Math.max(4, Math.round(player.getAttackCooldownProgressPerTick() * 0.8F));
        Integer last = LAST_SWING.get(player.getUuid());
        if (last != null && now - last >= 0 && now - last < interval) {
            return 0;
        }
        LAST_SWING.put(player.getUuid(), now);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        double reach = player.getEntityInteractionRange();
        net.minecraft.util.hit.EntityHitResult aimed = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, eye,
            eye.add(look.multiply(reach)), player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0),
            e -> e instanceof LivingEntity && !e.isSpectator() && e.canHit(), reach * reach);
        return play(world, player, fx.attack, aimed == null ? null : aimed.getEntity(), look, sizeFor(stack));
    }

    /** A mob hit {@code target} with an AI weapon (players get theirs when they swing). */
    public static int onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        Spark.Effects fx = of(stack);
        return fx.attack.isEmpty() ? 0 : play(world, attacker, fx.attack, target, attacker.getRotationVec(1.0F), sizeFor(stack));
    }

    /** An arrow from an AI bow landed at {@code at}: the bow's attack effects play there. */
    public static int arrowImpact(ServerWorld world, com.example.content.AibArrowEntity arrow, Vec3d at) {
        Spark.Effects fx = of(arrow.bow());
        if (fx.attack.isEmpty()) {
            return 0;
        }
        Vec3d dir = arrow.getVelocity().lengthSquared() < 1e-6 ? arrow.getRotationVec(1.0F) : arrow.getVelocity().normalize();
        for (Spark s : fx.attack) {
            Vec3d o = switch (s.start) {
                case "above" -> at.add(0, 1.5, 0);
                default -> at;
            };
            send(world, o, dir, s, 1, PlayerLookup.around(world, at, 64));
        }
        return fx.attack.size();
    }

    /** Where each effect starts: at the weapon, on the target, on the ground under it, above it, or on the wielder. */
    private static int play(ServerWorld world, LivingEntity wielder, List<Spark> sparks, Entity target, Vec3d look, double scale) {
        Vec3d eye = wielder.getEyePos();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d targetCenter = target != null ? target.getPos().add(0, target.getHeight() * 0.5, 0) : eye.add(look.multiply(2.5));
        Vec3d targetFeet = target != null ? target.getPos() : wielder.getPos().add(flat.multiply(2.5));
        List<ServerPlayerEntity> viewers = new ArrayList<>(PlayerLookup.tracking(wielder));
        if (wielder instanceof ServerPlayerEntity self && !viewers.contains(self)) {
            viewers.add(self);
        }
        for (Spark s : sparks) {
            Vec3d o = switch (s.start) {
                case "target" -> targetCenter;
                case "ground" -> targetFeet.add(0, 0.05, 0);
                case "above" -> targetCenter.add(0, 1.5, 0);
                case "self" -> wielder.getPos().add(0, wielder.getHeight() * 0.5, 0);
                default -> eye.add(0, -0.35, 0).add(look.multiply(1.2));
            };
            send(world, o, look, s, scale, viewers);
        }
        return sparks.size();
    }

    private static void send(ServerWorld world, Vec3d at, Vec3d dir, Spark spark, double scale, Collection<ServerPlayerEntity> to) {
        AiNetworkPayloads.SparkS2CPayload payload = new AiNetworkPayloads.SparkS2CPayload("[" + spark.toJson() + "]",
            at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, (float) scale);
        for (ServerPlayerEntity p : to) {
            if (p.getServerWorld() == world && ServerPlayNetworking.canSend(p, AiNetworkPayloads.SparkS2CPayload.ID)) {
                ServerPlayNetworking.send(p, payload);
            }
        }
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
}
