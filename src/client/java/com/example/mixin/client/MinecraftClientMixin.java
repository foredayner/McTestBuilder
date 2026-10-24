package com.example.mixin.client;

import com.example.client.network.AiClientNetworking;
import com.example.content.GearItem;
import com.example.content.GearKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attack key pressed with an AI weapon (not at a block, charged): tell the server, which plays
 * the weapon's attack effect for everyone, whether or not the swing hits something.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public ClientPlayerEntity player;

    @Shadow
    public HitResult crosshairTarget;

    @Shadow
    protected int attackCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void aib$attackEffect(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity p = this.player;
        if (p == null || p.isSpectator() || this.attackCooldown > 0
            || !(p.getMainHandStack().getItem() instanceof GearItem gear) || gear.kind().worn() || gear.kind() == GearKind.BOW) {
            return;
        }
        if (this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return; // starting to mine
        }
        if (p.getAttackCooldownProgress(0.5F) < 0.9F) {
            return;
        }
        AiClientNetworking.sendAttackSwing();
    }
}
