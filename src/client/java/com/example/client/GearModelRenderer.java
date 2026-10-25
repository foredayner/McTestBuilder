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
    public record Model(List<BuildPlan.Part> parts, List<Sprite> sprites, float[] tilted, boolean worn,
                        Map<Integer, List<BuildPlan.Anim>> anims, BuildPlan.Aura aura, float reach, boolean bow, Model arrow,
                        float length) {
    }

    /** First person, long weapons: extra tilt (degrees) and how far up the shaft it looks held (per block). */
    static final float FIRST_PERSON_LONG_TILT = 30.0F;
    static final float FIRST_PERSON_LONG_SLIDE = 0.5F;

    /** Hand-held models at least this long (blocks) are held with both hands in third person. */
    public static final float TWO_HANDED_LENGTH = 2.0F;

    /**
     * Scale of a hand-held model. Up to vanilla size it is drawn like before (a 1.45 sword about as
     * big as a vanilla one); longer ones grow toward their real size in third person (a 3-block
     * greatsword is 3 blocks), and in first person they are kept short enough to fit the view.
     */
    static float handScale(float length, boolean firstPerson) {
        if (firstPerson) {
            return HAND_SCALE * Math.min(1.0F, 1.8F / Math.max(0.1F, length));
        }
        float t = Math.max(0, Math.min(1, (length - 1.6F) / 1.4F));
        return HAND_SCALE + t * (1.0F / 0.85F - HAND_SCALE);
    }

    /** True when the main-hand stack is a long AI weapon (held with both hands in third person). */
    public static boolean twoHanded(ItemStack stack) {
        if (!(stack.getItem() instanceof com.example.content.GearItem gear) || gear.kind().worn()
            || gear.kind() == com.example.content.GearKind.BOW) {
            return false;
        }
        Model model = modelFor(stack);
        return model != null && model.length() >= TWO_HANDED_LENGTH;
    }

    /**
     * How far (blocks) the biggest AI equipment this entity holds or wears reaches from it, or 0.
     * Used to keep big equipment (a balloon above the head) from vanishing when the wearer's own
     * outline is off screen.
     */
    public static float reachOf(net.minecraft.entity.LivingEntity entity) {
        float reach = 0;
        for (net.minecraft.entity.EquipmentSlot slot : REACH_SLOTS) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.getItem() instanceof com.example.content.GearItem) {
                Model model = modelFor(stack);
                if (model != null) {
                    reach = Math.max(reach, model.reach());
                }
            }
        }
        return reach;
    }

    private static final net.minecraft.entity.EquipmentSlot[] REACH_SLOTS = {
        net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
        net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.entity.EquipmentSlot.OFFHAND };

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
    private static final float HAND_SCALE = 0.8F;
    /** The grip of the vanilla bow sprite (its limbs run along the same diagonal as a sword's blade). */
    private static final float BOW_GRIP_X = 0.31F;
    private static final float BOW_GRIP_Y = 0.69F;

    /** Decoded models by item data, so the gzip and JSON are read once per stack, not every frame. */
    private static final Map<Integer, Map.Entry<WeakReference<NbtComponent>, Model>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map.Entry<WeakReference<NbtComponent>, Model>> eldest) {
            return size() > 64;
        }
    };

    @Override
    public Model getData(ItemStack stack) {
        return modelFor(stack);
    }

    /** The decoded model on a gear stack (cached), or null. */
    public static Model modelFor(ItemStack stack) {
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
        boolean worn = stack.getItem() instanceof com.example.content.GearItem gear && gear.kind().worn();
        boolean bow = stack.getItem() instanceof com.example.content.GearItem gear2 && gear2.kind() == com.example.content.GearKind.BOW;
        Model model = plan == null || plan.parts.isEmpty() ? null : decode(plan, worn, bow);
        CACHE.put(key, Map.entry(new WeakReference<>(data), model == null ? EMPTY : model));
        return model;
    }

    private static final Model EMPTY = new Model(List.of(), List.of(), new float[4], false, Map.of(), null, 0, false, null, 0);

    private static Model decode(BuildPlan plan, boolean worn, boolean bow) {
        // A bow's arrow pieces are not part of the bow: they are the shape of the arrows it shoots.
        List<BuildPlan.Part> arrowParts = new ArrayList<>();
        List<BuildPlan.Part> own = new ArrayList<>();
        for (BuildPlan.Part p : plan.parts) {
            (p.group == BuildPlan.ARROW_GROUP ? arrowParts : own).add(p);
        }
        Model arrow = null;
        if (bow && !arrowParts.isEmpty()) {
            List<Sprite> arrowSprites = new ArrayList<>();
            float reach = 0;
            for (BuildPlan.Part p : arrowParts) {
                arrowSprites.add(spriteOf(p.block));
                reach = (float) Math.max(reach, p.y + p.sy / 2);
            }
            arrow = new Model(arrowParts, arrowSprites, new float[4], false, Map.of(), null, reach, false, null, reach);
        }
        if (own.isEmpty()) {
            own = arrowParts; // only an arrow: show it
        }
        plan = copyWithParts(plan, own);
        List<Sprite> sprites = new ArrayList<>();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float tilt = worn ? 0 : TILT;
        float c = (float) Math.cos(tilt), s = (float) Math.sin(tilt);
        float reach = 0;
        float lowY = Float.MAX_VALUE, highY = -Float.MAX_VALUE;
        for (BuildPlan.Part p : plan.parts) {
            lowY = (float) Math.min(lowY, p.y - p.sy / 2);
            highY = (float) Math.max(highY, p.y + p.sy / 2);
            sprites.add(spriteOf(p.block));
            double r = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
            reach = (float) Math.max(reach, Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z) + r);
            for (int i = 0; i < 4; i++) {
                float x = (float) (p.x + ((i & 1) == 0 ? -r : r));
                float y = (float) (p.y + ((i & 2) == 0 ? -r : r));
                float tx = x * c - y * s, ty = x * s + y * c;
                minX = Math.min(minX, tx); maxX = Math.max(maxX, tx);
                minY = Math.min(minY, ty); maxY = Math.max(maxY, ty);
            }
        }
        Map<Integer, List<BuildPlan.Anim>> anims = new java.util.HashMap<>();
        if (plan.anims != null) {
            for (BuildPlan.Anim a : plan.anims) {
                anims.computeIfAbsent(a.group, k -> new ArrayList<>()).add(a);
            }
        }
        return new Model(plan.parts, sprites, new float[] { minX, minY, maxX, maxY }, worn, anims, plan.aura, reach, bow, arrow,
            plan.parts.isEmpty() ? 0 : highY - lowY);
    }

    private static BuildPlan copyWithParts(BuildPlan plan, List<BuildPlan.Part> parts) {
        BuildPlan copy = new BuildPlan();
        copy.parts = parts;
        copy.anims = plan.anims;
        copy.aura = plan.aura;
        return copy;
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
        if (model == null || model.parts().isEmpty() || mode == ModelTransformationMode.HEAD) {
            return; // worn equipment on the head is drawn by WearRenderer
        }
        matrices.push();
        if (model.worn()) {
            // Helmets, armour and back items as an item: upright, fitted (a little smaller in the hand).
            float[] t = model.tilted();
            float size = Math.max(t[2] - t[0], t[3] - t[1]);
            boolean slot = mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.FIXED;
            float scale = (slot ? 0.95F : 0.6F) / Math.max(0.2F, size);
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(scale, scale, scale);
            matrices.translate(-(t[0] + t[2]) / 2, -(t[1] + t[3]) / 2, 0);
            draw(matrices, vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), model, light, overlay);
            matrices.pop();
            return;
        }
        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.FIXED) {
            // Fit the tilted model into the slot, centred.
            float[] t = model.tilted();
            float size = Math.max(t[2] - t[0], t[3] - t[1]);
            float scale = 0.95F / Math.max(0.2F, size);
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(scale, scale, scale);
            matrices.translate(-(t[0] + t[2]) / 2, -(t[1] + t[3]) / 2, 0);
        } else if (model.bow()) {
            // A bow: its grip where the vanilla bow sprite's grip is, so the bow poses (drawing too) fit it.
            matrices.translate(BOW_GRIP_X, BOW_GRIP_Y, 0.5F);
        } else {
            // In the hand: a 1.45-block AI sword shows about as big as a vanilla sword; big ones grow.
            float scale = handScale(model.length(), mode.isFirstPerson());
            matrices.translate(GRIP_X, GRIP_Y, 0.5F);
            matrices.scale(scale, scale, scale);
        }
        matrices.multiply(new Quaternionf().rotateZ(TILT));
        if (mode.isFirstPerson() && !model.bow() && !model.worn() && model.length() >= TWO_HANDED_LENGTH) {
            // A long weapon in first person: laid a little lower and held further up the shaft (in look only),
            // so its head or blade is in view instead of above the screen.
            matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(FIRST_PERSON_LONG_TILT)));
            matrices.translate(0, -(model.length() - 1.6F) * FIRST_PERSON_LONG_SLIDE, 0);
        }
        draw(matrices, vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), model, light, overlay);
        if (mode != ModelTransformationMode.GUI && mode != ModelTransformationMode.FIXED) {
            drawAura(matrices, vertexConsumers, model, overlay);
        }
        matrices.pop();
    }

    private static final Identifier WHITE = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/white.png");

    /**
     * The aura: two see-through, shining shells around every piece (a small and a wider one),
     * slowly breathing, in the aura's colour. Moving parts carry their shell with them.
     */
    static void drawAura(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Model model, int overlay) {
        BuildPlan.Aura aura = model.aura();
        if (aura == null || model.parts().isEmpty()) {
            return;
        }
        VertexConsumer buffer = vertexConsumers.getBuffer(net.minecraft.client.render.RenderLayer.getEntityTranslucentEmissive(WHITE));
        double seconds = seconds();
        double breathe = 0.8 + 0.2 * Math.sin(2 * Math.PI * 0.6 * seconds);
        double[][] shells = { { 0.025, 0.30 }, { 0.07, 0.13 } };
        for (double[] shell : shells) {
            int alpha = (int) Math.round(255 * Math.min(0.6, shell[1] * aura.strength * breathe));
            int argb = (alpha << 24) | (aura.color & 0xFFFFFF);
            for (BuildPlan.Part part : model.parts()) {
                List<BuildPlan.Anim> anims = part.group == 0 ? null : model.anims().get(part.group);
                matrices.push();
                if (anims != null) {
                    for (BuildPlan.Anim a : anims) {
                        animate(matrices, a, seconds, 0);
                    }
                }
                shellBox(matrices, buffer, part, (float) shell[0], argb, overlay);
                matrices.pop();
            }
        }
    }

    private static void shellBox(MatrixStack matrices, VertexConsumer buffer, BuildPlan.Part p, float margin, int argb, int overlay) {
        matrices.push();
        matrices.translate((float) p.x, (float) p.y, (float) p.z);
        matrices.multiply(new Quaternionf()
            .rotateY((float) Math.toRadians(p.yaw))
            .rotateX((float) Math.toRadians(p.pitch))
            .rotateZ((float) Math.toRadians(p.roll)));
        MatrixStack.Entry e = matrices.peek();
        float hx = (float) p.sx / 2 + margin, hy = (float) p.sy / 2 + margin, hz = (float) p.sz / 2 + margin;
        Vector3f[] corner = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = new Vector3f((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
        }
        for (int[] f : FACES) {
            for (int k = 0; k < 4; k++) {
                buffer.vertex(e, corner[f[k]]).color(argb).texture(0.5F, 0.5F).overlay(overlay).light(0xF000F0)
                    .normal(e, f[4], f[5], f[6]);
            }
        }
        matrices.pop();
    }

    /** Every part of the model as a textured box, in the current matrix, moving parts at this moment. */
    static void draw(MatrixStack matrices, VertexConsumer buffer, Model model, int light, int overlay) {
        double seconds = seconds();
        for (int i = 0; i < model.parts().size(); i++) {
            BuildPlan.Part part = model.parts().get(i);
            List<BuildPlan.Anim> anims = part.group == 0 ? null : model.anims().get(part.group);
            if (anims == null) {
                box(matrices, buffer, part, model.sprites().get(i), light, overlay);
                continue;
            }
            matrices.push();
            int partLight = light;
            for (BuildPlan.Anim a : anims) {
                partLight = animate(matrices, a, seconds, partLight);
            }
            box(matrices, buffer, part, model.sprites().get(i), partLight, overlay);
            matrices.pop();
        }
    }

    /** Game time in seconds (smooth between ticks), so every player sees the same movement. */
    private static double seconds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return System.currentTimeMillis() / 1000.0;
        }
        return (client.world.getTime() + client.getRenderTickCounter().getTickDelta(true)) / 20.0;
    }

    /** Apply one movement to the matrix; returns the light to use (glow = full brightness). */
    private static int animate(MatrixStack m, BuildPlan.Anim a, double t, int light) {
        if ("glow".equals(a.type)) {
            return 0xF000F0;
        }
        double wave = Math.sin(2 * Math.PI * a.speed * t);
        org.joml.Vector3f axis = switch (a.axis == null ? "y" : a.axis) {
            case "x" -> new org.joml.Vector3f(1, 0, 0);
            case "z" -> new org.joml.Vector3f(0, 0, 1);
            default -> new org.joml.Vector3f(0, 1, 0);
        };
        float px = (float) a.px, py = (float) a.py, pz = (float) a.pz;
        switch (a.type) {
            case "spin" -> {
                double turns = (a.speed * t) % 1.0;
                m.translate(px, py, pz);
                m.multiply(new Quaternionf().rotateAxis((float) (turns * 2 * Math.PI), axis));
                m.translate(-px, -py, -pz);
            }
            case "swing" -> {
                m.translate(px, py, pz);
                m.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(a.amount * wave), axis));
                m.translate(-px, -py, -pz);
            }
            case "bob" -> m.translate(axis.x * (float) (a.amount * wave), axis.y * (float) (a.amount * wave),
                axis.z * (float) (a.amount * wave));
            case "pulse" -> {
                float s = (float) (1 + a.amount * wave);
                m.translate(px, py, pz);
                m.scale(s, s, s);
                m.translate(-px, -py, -pz);
            }
            default -> {
            }
        }
        return light;
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
