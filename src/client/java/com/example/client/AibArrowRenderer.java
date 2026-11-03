package com.example.client;

import com.example.ai.build.Spark;
import com.example.content.AibArrowEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
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
 * Arrows from AI bows: drawn with the arrow shape designed with the bow, or as a normal arrow when
 * the bow has none. The bow's shot settings say how it turns (along its flight, tumbling, or kept
 * upright) and how big it is drawn.
 */
public class AibArrowRenderer extends ProjectileEntityRenderer<AibArrowEntity, AibArrowRenderer.State> {

    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");
    /** Tumbling: degrees per tick (about two turns a second). */
    private static final float SPIN = 36.0F;

    public static class State extends ProjectileEntityRenderState {
        GearModelRenderer.Model arrow;
        String facing = "flight";
        float size;
        float box;
        float spin;
        /** A letter shot (the bow's shot text), or null. */
        String letter;
        int color;
    }

    private final TextRenderer font;

    public AibArrowRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.font = context.getTextRenderer();
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
        state.arrow = bow == null ? null : bow.arrow(entity.variant());
        Spark.Shot shot = entity.shot();
        int[] letters = shot == null ? new int[0] : shot.letters();
        state.letter = letters.length == 0 ? null : new String(Character.toChars(letters[Math.floorMod(entity.variant(), letters.length)]));
        state.color = shot == null ? 0xFFFFFF : shot.color;
        state.facing = shot == null ? "flight" : shot.facing;
        state.size = shot == null ? 0 : (float) shot.size;
        state.box = shot == null || shot.size <= 0 ? 0 : (float) shot.hitbox();
        if (entity.isFlying()) {
            entity.spin = (entity.age + tickDelta) * SPIN; // it stops turning where it sticks
        }
        state.spin = entity.spin;
    }

    @Override
    public void render(State state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (state.letter != null) {
            renderLetter(state, matrices, vertexConsumers);
            return;
        }
        boolean shape = state.arrow != null && !state.arrow.parts().isEmpty();
        float length = shape ? state.arrow.reach() : 0.5F;
        float k = state.size > 0 && length > 0.05F ? state.size / length : 1;
        matrices.push();
        // A bigger shot has a bigger box: draw it round the box's middle.
        matrices.translate(0, state.box / 2, 0);
        if (!shape) {
            matrices.scale(k, k, k);
            super.render(state, matrices, vertexConsumers, light);
            matrices.pop();
            return;
        }
        // Same turn as a vanilla arrow (which lies along +X, pointing where it flies); the AI's arrow points up (+Y).
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw - 90.0F));
        switch (state.facing) {
            case "fixed" -> {
                // Upright as designed, only turned to face the way it goes.
            }
            case "spin" -> {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.spin));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F));
            }
            default -> {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.pitch));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F));
            }
        }
        matrices.scale(k, k, k);
        matrices.translate(0, -length * ("flight".equals(state.facing) ? 0.6F : 0.5F), 0);
        GearModelRenderer.draw(matrices, vertexConsumers, TexturedRenderLayers.getEntityCutout(), state.arrow, light,
            OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    /**
     * A letter in the game's font, a few layers deep so it has some thickness, lit so it reads in
     * the dark. Upright letters ("fixed") turn to face whoever looks; others turn like an arrow.
     */
    private void renderLetter(State state, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        float size = state.size > 0 ? state.size : 0.6F;
        matrices.push();
        matrices.translate(0, Math.max(state.box, 0.5F) / 2, 0);
        switch (state.facing) {
            case "fixed" -> matrices.multiply(this.dispatcher.getRotation());
            case "spin" -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw - 90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.spin));
            }
            default -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw - 90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.pitch));
            }
        }
        float k = size / 8.0F; // the font is about 8 pixels high
        matrices.scale(k, -k, k);
        float x = -this.font.getWidth(state.letter) / 2.0F + 0.5F;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int front = 0xFF000000 | state.color;
        int side = 0xFF000000 | darker(state.color);
        for (int face = 0; face < 2; face++) {
            matrices.push();
            if (face == 1) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
            }
            // Back layers first, darker: the letter's thickness.
            for (int layer = 3; layer >= 0; layer--) {
                matrices.push();
                matrices.translate(0, 0, layer * 0.25F);
                this.font.draw(state.letter, x, -4.0F, layer == 0 ? front : side, false, matrices.peek().getPositionMatrix(),
                    vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
                matrices.pop();
            }
            matrices.pop();
        }
        matrices.pop();
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r * 6 / 10) << 16) | ((g * 6 / 10) << 8) | (b * 6 / 10);
    }
}
