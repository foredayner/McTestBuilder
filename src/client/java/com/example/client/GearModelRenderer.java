package com.example.client;

import com.example.AiBuildAssistantMod;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.GearItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws an AI equipment item as its 3D model: every part is a small box with its block's
 * texture. The model's grip point (0, 0, 0) sits where a vanilla sword's handle is and the
 * model, which points up (+Y), is tilted like a sword sprite, so the game's hand-held
 * positions (first and third person) work for it. In inventories it is scaled to fit a slot.
 */
public final class GearModelRenderer implements SpecialModelRenderer<GearModelRenderer.Model> {

    public static final Identifier TYPE_ID = Identifier.of(AiBuildAssistantMod.MOD_ID, "gear_model");

    /** A decoded model: parts with their textures, and its outline after the sword tilt. */
    public record Model(List<BuildPlan.Part> parts, List<Sprite> sprites, float[] tilted) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(LoadedEntityModels entityModels) {
            return new GearModelRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec() {
            return CODEC;
        }
    }

    private static final float GRIP_X = 0.2F;
    private static final float GRIP_Y = 0.2F;
    private static final float TILT = (float) Math.toRadians(-45);

    /** Decoded models by item data, so the gzip and JSON are read once per stack, not every frame. */
    private static final Map<Integer, Map.Entry<WeakReference<NbtComponent>, Model>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map.Entry<WeakReference<NbtComponent>, Model>> eldest) {
            return size() > 64;
        }
    };

    @Override
    public Model getData(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        int key = System.identityHashCode(data);
        Map.Entry<WeakReference<NbtComponent>, Model> hit = CACHE.get(key);
        if (hit != null && hit.getKey().get() == data) {
            return hit.getValue();
        }
        BuildPlan plan = GearItems.modelOf(stack);
        Model model = plan == null || plan.parts.isEmpty() ? null : decode(plan);
        CACHE.put(key, Map.entry(new WeakReference<>(data), model == null ? EMPTY : model));
        return model;
    }

    private static final Model EMPTY = new Model(List.of(), List.of(), new float[4]);

    private static Model decode(BuildPlan plan) {
        List<Sprite> sprites = new ArrayList<>();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float c = (float) Math.cos(TILT), s = (float) Math.sin(TILT);
        for (BuildPlan.Part p : plan.parts) {
            sprites.add(spriteOf(p.block));
            double r = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
            for (int i = 0; i < 4; i++) {
                float x = (float) (p.x + ((i & 1) == 0 ? -r : r));
                float y = (float) (p.y + ((i & 2) == 0 ? -r : r));
                float tx = x * c - y * s, ty = x * s + y * c;
                minX = Math.min(minX, tx); maxX = Math.max(maxX, tx);
                minY = Math.min(minY, ty); maxY = Math.max(maxY, ty);
            }
        }
        return new Model(plan.parts, sprites, new float[] { minX, minY, maxX, maxY });
    }

    private static Sprite spriteOf(String block) {
        String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
        Identifier parsed = Identifier.tryParse(id);
        Block b = Registries.BLOCK.get(parsed == null ? Identifier.ofVanilla("stone") : parsed);
        BlockState state = b.getDefaultState();
        return MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModelParticleSprite(state);
    }

    @Override
    public void render(Model model, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay, boolean glint) {
        if (model == null || model.parts().isEmpty()) {
            return;
        }
        matrices.push();
        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.FIXED) {
            // Fit the tilted model into the slot, centred.
            float[] t = model.tilted();
            float size = Math.max(t[2] - t[0], t[3] - t[1]);
            float scale = 0.95F / Math.max(0.2F, size);
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(scale, scale, scale);
            matrices.translate(-(t[0] + t[2]) / 2, -(t[1] + t[3]) / 2, 0);
        } else {
            matrices.translate(GRIP_X, GRIP_Y, 0.5F);
        }
        matrices.multiply(new Quaternionf().rotateZ(TILT));
        VertexConsumer buffer = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        for (int i = 0; i < model.parts().size(); i++) {
            box(matrices, buffer, model.parts().get(i), model.sprites().get(i), light, overlay);
        }
        matrices.pop();
    }

    private static final int[][] FACES = {
        // corner indices (bit 0 = +x, bit 1 = +y, bit 2 = +z) for each face, counter-clockwise from outside, and the normal
        { 4, 5, 7, 6, 0, 0, 1 },   // +z
        { 1, 0, 2, 3, 0, 0, -1 },  // -z
        { 5, 1, 3, 7, 1, 0, 0 },   // +x
        { 0, 4, 6, 2, -1, 0, 0 },  // -x
        { 6, 7, 3, 2, 0, 1, 0 },   // +y
        { 0, 1, 5, 4, 0, -1, 0 },  // -y
    };

    private static void box(MatrixStack matrices, VertexConsumer buffer, BuildPlan.Part p, Sprite sprite, int light, int overlay) {
        matrices.push();
        matrices.translate((float) p.x, (float) p.y, (float) p.z);
        matrices.multiply(new Quaternionf()
            .rotateY((float) Math.toRadians(p.yaw))
            .rotateX((float) Math.toRadians(p.pitch))
            .rotateZ((float) Math.toRadians(p.roll)));
        MatrixStack.Entry e = matrices.peek();
        float hx = (float) p.sx / 2, hy = (float) p.sy / 2, hz = (float) p.sz / 2;
        Vector3f[] corner = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = new Vector3f((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
        }
        for (int[] f : FACES) {
            // Texture: one block's texture per block of size, cut to the face (bigger faces stretch).
            float w, h;
            if (f[6] != 0) { w = (float) p.sx; h = (float) p.sy; }
            else if (f[4] != 0) { w = (float) p.sz; h = (float) p.sy; }
            else { w = (float) p.sx; h = (float) p.sz; }
            float u0 = sprite.getMinU(), v0 = sprite.getMinV();
            float u1 = u0 + (sprite.getMaxU() - u0) * Math.min(1, w);
            float v1 = v0 + (sprite.getMaxV() - v0) * Math.min(1, h);
            float[][] uv = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
            for (int k = 0; k < 4; k++) {
                buffer.vertex(e, corner[f[k]]).color(0xFFFFFFFF).texture(uv[k][0], uv[k][1]).overlay(overlay).light(light)
                    .normal(e, f[4], f[5], f[6]);
            }
        }
        matrices.pop();
    }
}
