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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: an AI sword with an ordered attack motion swings with that motion. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique
    private String aib$motion = "";

    @Shadow
    protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void aib$pickMotion(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, CallbackInfo ci) {
        aib$motion = player.isUsingItem() ? "" : GearMotionClient.motionOf(item);
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
    private void aib$swing(HeldItemRenderer self, MatrixStack matrices, Arm arm, float swingProgress) {
        if (aib$motion.isEmpty()) {
            applySwingOffset(matrices, arm, swingProgress);
            return;
        }
        // The resting pose of the normal swing, then the ordered motion.
        applySwingOffset(matrices, arm, 0);
        GearMotionClient.firstPerson(matrices, arm, aib$motion, GearMotionClient.progress(swingProgress));
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void aib$clearMotion(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 int light, CallbackInfo ci) {
        aib$motion = "";
    }
}
