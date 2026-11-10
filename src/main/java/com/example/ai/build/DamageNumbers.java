package com.example.ai.build;

import com.example.content.AibArrowEntity;
import com.example.network.AiNetworkPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;

/**
 * Server side of the two weapon traits (Spark.Effects.randomDamage / damageNumbers): which weapon
 * a hit came from, the roll, and the number sent to the players who see the one hit.
 */
public final class DamageNumbers {

    private DamageNumbers() {
    }

    /** A hit by a weapon with a trait: its damage after the roll (-1 = not rolled) and whether to show it. */
    public record Hit(float amount, int roll, boolean show) {
    }

    /** The effects of the AI weapon a hit came from: its arrow's bow, or what the attacker swung; null for anything else. */
    public static Spark.Effects weaponOf(DamageSource source) {
        Entity direct = source.getSource();
        if (direct instanceof AibArrowEntity arrow) {
            ItemStack bow = arrow.bow();
            return bow.isEmpty() ? null : GearEffects.of(bow);
        }
        if (source.getAttacker() instanceof LivingEntity attacker && direct == attacker) {
            ItemStack held = attacker.getMainHandStack();
            return held.isEmpty() ? null : GearEffects.of(held);
        }
        return null;
    }

    /** Before the damage lands: null when no trait applies, else the (rolled) damage. */
    public static Hit start(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        Spark.Effects fx = weaponOf(source);
        if (fx == null || !fx.randomDamage && !fx.damageNumbers) {
            return null;
        }
        int roll = fx.randomDamage ? world.random.nextInt(10) : -1;
        float rolled = roll < 0 ? amount : amount * DamageTraits.scale(roll);
        return new Hit(rolled, roll, fx.damageNumbers);
    }

    /** After it landed (or not): what it took goes up over the one hit; a roll of 0 shows as a miss. */
    public static void finish(ServerWorld world, LivingEntity target, DamageSource source, Hit hit, float before, boolean landed) {
        if (!hit.show()) {
            return;
        }
        float taken = Math.max(0, before - (target.getHealth() + target.getAbsorptionAmount()));
        if (!landed && hit.roll() != 0) {
            return; // it did not land (still hurt from the last hit, blocked): nothing to show
        }
        AiNetworkPayloads.DamageNumberS2CPayload payload = new AiNetworkPayloads.DamageNumberS2CPayload(target.getId(),
            hit.roll() == 0 ? 0 : taken, (byte) hit.roll());
        Set<ServerPlayerEntity> to = new HashSet<>(PlayerLookup.tracking(target));
        if (target instanceof ServerPlayerEntity self) {
            to.add(self);
        }
        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            to.add(attacker);
        }
        for (ServerPlayerEntity player : to) {
            if (ServerPlayNetworking.canSend(player, AiNetworkPayloads.DamageNumberS2CPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
