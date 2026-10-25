package com.example.mixin.client;

import com.example.client.AibMotionState;
import com.example.client.GearMotionClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: remember the main-hand sword's attack motion on the render state. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL"))
    private void aib$motion(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state instanceof AibMotionState motion) {
            motion.aib$setMotion(entity.preferredHand == Hand.OFF_HAND ? "" : GearMotionClient.motionOf(entity.getMainHandStack()));
            motion.aib$setTwoHanded(!entity.isUsingItem()
                && com.example.client.GearModelRenderer.twoHanded(entity.getMainHandStack()));
        }
    }
}
