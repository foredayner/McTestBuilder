package com.example.client;

import com.example.content.AibArrowEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.render.entity.state.ProjectileEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Arrows from AI bows: drawn with the arrow shape designed with the bow (pointing where it
 * flies), or as a normal arrow when the bow has none.
 */
public class AibArrowRenderer extends ProjectileEntityRenderer<AibArrowEntity, AibArrowRenderer.State> {

    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");

    public static class State extends ProjectileEntityRenderState {
        GearModelRenderer.Model arrow;
    }

    public AibArrowRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    protected Identifier getTexture(State state) {
        return TEXTURE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(AibArrowEntity entity, State state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        GearModelRenderer.Model bow = GearModelRenderer.modelFor(entity.bow());
        state.arrow = bow == null ? null : bow.arrow();
    }

    @Override
    public void render(State state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (state.arrow == null || state.arrow.parts().isEmpty()) {
            super.render(state, matrices, vertexConsumers, light);
            return;
        }
        matrices.push();
        // Same turn as a vanilla arrow (which lies along +X, pointing where it flies); the AI's arrow points up (+Y).
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F));
        matrices.translate(0, -state.arrow.reach() * 0.6F, 0);
        GearModelRenderer.draw(matrices, vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), state.arrow, light,
            OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
