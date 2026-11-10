package com.example.mixin;

import com.example.ai.build.DamageNumbers;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hits from AI equipment with random damage or damage numbers: the damage is rolled before it
 * lands, and what it really took (after armour) is sent to the players who see the one hit.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @WrapMethod(method = "damage")
    private boolean aib$rollAndShow(ServerWorld world, DamageSource source, float amount, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        DamageNumbers.Hit hit = DamageNumbers.start(world, self, source, amount);
        if (hit == null) {
            return original.call(world, source, amount);
        }
        float before = self.getHealth() + self.getAbsorptionAmount();
        boolean landed = original.call(world, source, hit.amount());
        DamageNumbers.finish(world, self, source, hit, before, landed);
        return landed;
    }
}
