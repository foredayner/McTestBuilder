package com.example.client;

import com.example.AiBuildAssistantMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * Smooth round pieces (BuildPlan.Part shape): a sphere (ellipsoid), a cylinder or a cone, drawn as
 * a fine mesh instead of many small boxes. In the world each is an item display of the mod's
 * "smooth" item, which this renderer draws; equipment and the 3D pictures draw the same mesh.
 * The shape spans -0.5..0.5 on each axis of its piece (sizes come from the scale).
 */
public final class SmoothShapes implements SpecialModelRenderer<SmoothShapes.Look> {

    public static final Identifier TYPE_ID = Identifier.of(AiBuildAssistantMod.MOD_ID, "smooth_shape");
    private static final Identifier PAINT_SPRITE = Identifier.of(AiBuildAssistantMod.MOD_ID, "block/paint");
    private static final int STACKS = 14;
    private static final int SLICES = 24;

    /** What a smooth display item shows: its shape, taper and colour. */
    public record Look(String shape, float taper, int argb) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(LoadedEntityModels entityModels) {
            return new SmoothShapes();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec() {
            return CODEC;
        }
    }

    @Override
    public Look getData(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = data == null ? new NbtCompound() : data.copyNbt();
        String shape = nbt.getString("aib_shape");
        float taper = nbt.contains("aib_taper") ? nbt.getFloat("aib_taper") : 1;
        DyedColorComponent dye = stack.get(DataComponentTypes.DYED_COLOR);
        int rgb = dye == null ? 0xFFFFFF : dye.rgb();
        return new Look(shape.isEmpty() ? "sphere" : shape, taper, 0xFF000000 | rgb);
    }

    @Override
    public void render(Look look, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay, boolean glint) {
        if (look == null) {
            return;
        }
        matrices.push();
        matrices.translate(0.5F, 0.5F, 0.5F); // item models span 0..1; the shape is centred
        draw(matrices.peek(), vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), look.shape(), look.taper(),
            1, 1, 1, look.argb(), light, overlay);
        matrices.pop();
    }

    static Sprite paintSprite() {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(PAINT_SPRITE);
    }

    /**
     * The shape centred on the current origin, sx x sy x sz big (1 x 1 x 1 when the matrix scales
     * it), in the block-atlas layer (the white paint sprite, tinted).
     */
    static void draw(MatrixStack.Entry e, VertexConsumer b, String shape, float taper, float sx, float sy, float sz, int argb,
                     int light, int overlay) {
        Sprite sprite = paintSprite();
        float u = (sprite.getMinU() + sprite.getMaxU()) / 2, v = (sprite.getMinV() + sprite.getMaxV()) / 2;
        if ("cylinder".equals(shape) || "cone".equals(shape)) {
            frustum(e, b, "cylinder".equals(shape) ? 1 : Math.max(0, Math.min(1, taper)), sx, sy, sz, argb, light, overlay, u, v);
        } else {
            sphere(e, b, sx, sy, sz, argb, light, overlay, u, v);
        }
    }

    private static void sphere(MatrixStack.Entry e, VertexConsumer b, float sx, float sy, float sz, int argb, int light, int overlay,
                               float u, float v) {
        float[][] p = new float[(STACKS + 1) * (SLICES + 1)][];
        float[][] n = new float[p.length][];
        for (int i = 0; i <= STACKS; i++) {
            double t = Math.PI * i / STACKS;
            for (int j = 0; j <= SLICES; j++) {
                double f = 2 * Math.PI * j / SLICES;
                float x = (float) (Math.sin(t) * Math.cos(f)), y = (float) Math.cos(t), z = (float) (Math.sin(t) * Math.sin(f));
                p[i * (SLICES + 1) + j] = new float[] { x * sx / 2, y * sy / 2, z * sz / 2 };
                // The normal of an ellipsoid: the sphere's, divided by the stretch.
                n[i * (SLICES + 1) + j] = normal(x / sx, y / sy, z / sz);
            }
        }
        for (int i = 0; i < STACKS; i++) {
            for (int j = 0; j < SLICES; j++) {
                int a = i * (SLICES + 1) + j, c = a + SLICES + 1;
                quad(e, b, p[a], n[a], p[c], n[c], p[c + 1], n[c + 1], p[a + 1], n[a + 1], argb, light, overlay, u, v);
            }
        }
    }

    /** An upright cut cone: bottom radius 1/2, top radius taper/2 (a cylinder at taper 1), with its ends closed. */
    private static void frustum(MatrixStack.Entry e, VertexConsumer b, float taper, float sx, float sy, float sz, int argb, int light,
                                int overlay, float u, float v) {
        float hy = sy / 2;
        float slope = (1 - taper) / 2 * Math.max(sx, sz) / Math.max(1e-4F, sy);
        for (int j = 0; j < SLICES; j++) {
            double f0 = 2 * Math.PI * j / SLICES, f1 = 2 * Math.PI * (j + 1) / SLICES;
            float c0 = (float) Math.cos(f0), s0 = (float) Math.sin(f0), c1 = (float) Math.cos(f1), s1 = (float) Math.sin(f1);
            float[] b0 = { c0 * sx / 2, -hy, s0 * sz / 2 }, b1 = { c1 * sx / 2, -hy, s1 * sz / 2 };
            float[] t0 = { c0 * sx / 2 * taper, hy, s0 * sz / 2 * taper }, t1 = { c1 * sx / 2 * taper, hy, s1 * sz / 2 * taper };
            float[] n0 = normal(c0 / sx, slope / Math.max(sx, sz), s0 / sz), n1 = normal(c1 / sx, slope / Math.max(sx, sz), s1 / sz);
            quad(e, b, b0, n0, t0, n0, t1, n1, b1, n1, argb, light, overlay, u, v);
            // The ends.
            float[] down = { 0, -1, 0 }, up = { 0, 1, 0 };
            float[] cb = { 0, -hy, 0 }, ct = { 0, hy, 0 };
            quad(e, b, cb, down, b0, down, b1, down, b1, down, argb, light, overlay, u, v);
            if (taper > 0) {
                quad(e, b, ct, up, t1, up, t0, up, t0, up, argb, light, overlay, u, v);
            }
        }
    }

    private static float[] normal(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-6F ? new float[] { 0, 1, 0 } : new float[] { x / l, y / l, z / l };
    }

    /** A quad drawn from both sides (so its winding never hides it). */
    private static void quad(MatrixStack.Entry e, VertexConsumer b, float[] p0, float[] n0, float[] p1, float[] n1, float[] p2, float[] n2,
                             float[] p3, float[] n3, int argb, int light, int overlay, float u, float v) {
        vert(e, b, p0, n0, argb, light, overlay, u, v);
        vert(e, b, p1, n1, argb, light, overlay, u, v);
        vert(e, b, p2, n2, argb, light, overlay, u, v);
        vert(e, b, p3, n3, argb, light, overlay, u, v);
        vert(e, b, p3, n3, argb, light, overlay, u, v);
        vert(e, b, p2, n2, argb, light, overlay, u, v);
        vert(e, b, p1, n1, argb, light, overlay, u, v);
        vert(e, b, p0, n0, argb, light, overlay, u, v);
    }

    private static void vert(MatrixStack.Entry e, VertexConsumer b, float[] p, float[] n, int argb, int light, int overlay, float u, float v) {
        b.vertex(e, p[0], p[1], p[2]).color(argb).texture(u, v).overlay(overlay).light(light).normal(e, n[0], n[1], n[2]);
    }
}
