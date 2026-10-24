package com.example.mixin.client;

import com.example.client.GearMotionClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: an AI sword with an ordered attack motion swings with that motion. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique
    private String aib$motion = "";

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void aib$pickMotion(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, CallbackInfo ci) {
        aib$motion = player.isUsingItem() ? "" : GearMotionClient.motionOf(item);
    }

    /** The normal swing turn (called from the item drawing above): replaced by the ordered motion. */
    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void aib$swing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        if (aib$motion.isEmpty()) {
            return;
        }
        // The resting pose of the normal swing, then the ordered motion.
        int side = arm == Arm.RIGHT ? 1 : -1;
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
        GearMotionClient.firstPerson(matrices, arm, aib$motion, GearMotionClient.progress(swingProgress));
        ci.cancel();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void aib$clearMotion(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 int light, CallbackInfo ci) {
        aib$motion = "";
    }
}
