package com.example.mixin.client;

import com.example.client.GearModelRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game skips drawing an entity whose outline is off screen. Big AI equipment (a balloon
 * floating above the head, a giant hammer) reaches far past that outline, so it would vanish
 * while still in view: the outline used for that check is grown by the equipment's reach.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void aib$growForBigEquipment(Entity entity, CallbackInfoReturnable<Box> cir) {
        if (entity instanceof LivingEntity living) {
            float reach = GearModelRenderer.reachOf(living);
            if (reach > 1.0F) {
                cir.setReturnValue(cir.getReturnValue().expand(reach));
            }
        }
    }
}
