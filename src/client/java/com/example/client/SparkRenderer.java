package com.example.client;

import com.example.AiBuildAssistantMod;
import com.example.ai.build.GearEffects;
import com.example.ai.build.Spark;
import com.example.content.AibArrowEntity;
import com.example.content.GearItem;
import com.example.content.GearKind;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the colour-dot effects of AI equipment: attack and landing effects the server announces,
 * and, found by this game itself, the effects of equipment held or worn by anyone nearby and the
 * trails of arrows from AI bows. Every dot is a small glowing sprite facing the camera.
 */
public final class SparkRenderer {

    private static final Identifier TEXTURE = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/spark.png");
    private static final int MAX_EFFECTS = 160;
    private static final int MAX_DOTS_PER_FRAME = 9000;
    private static final double RANGE = 48;

    /** One effect being drawn. */
    private static final class Effect {
        final Spark spark;
        final List<double[]> dots;
        Vec3d origin;
        Vec3d dir;
        final double scale;
        final double start;
        /** Loops in place, following an entity (idle effects and trails without copies). */
        final Entity follow;
        final String anchor;
        /** Plays once while following (an effect given off every few ticks moves with what gave it off). */
        final boolean once;
        boolean seen = true;

        Effect(Spark spark, Vec3d origin, Vec3d dir, double scale, double start, Entity follow, String anchor) {
            this(spark, origin, dir, scale, start, follow, anchor, false);
        }

        Effect(Spark spark, Vec3d origin, Vec3d dir, double scale, double start, Entity follow, String anchor, boolean once) {
            this.once = once;
            this.spark = spark;
            this.dots = spark.dots();
            this.origin = origin;
            this.dir = dir;
            this.scale = scale;
            this.start = start;
            this.follow = follow;
            this.anchor = anchor;
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    /** Following effects by "entity id / slot / index", kept while the equipment stays on. */
    private static final Map<String, Effect> FOLLOWING = new HashMap<>();
    private static final Map<NbtComponent, Spark.Effects> CACHE = new WeakHashMap<>();

    private SparkRenderer() {
    }

    /** The server says: these effects play here now. */
    public static void play(String json, Vec3d at, Vec3d dir, double scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        double now = now(client);
        for (Spark s : Spark.listFromJson(json)) {
            add(new Effect(s, at, dir, scale, now, null, ""));
        }
    }

    private static void add(Effect e) {
        if (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(e);
    }

    private static double now(MinecraftClient client) {
        return client.world.getTime() + client.getRenderTickCounter().getTickDelta(true);
    }

    private static Spark.Effects effectsOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GearItem)) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(GearEffects.KEY)) {
            return null;
        }
        return CACHE.computeIfAbsent(data, d -> GearEffects.of(stack));
    }

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /** Every client tick: effects of equipment held or worn nearby, and arrow trails. */
    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            EFFECTS.clear();
            FOLLOWING.clear();
            return;
        }
        long time = client.world.getTime();
        double now = time;
        for (Effect e : FOLLOWING.values()) {
            e.seen = false;
        }
        Vec3d eye = client.player.getEyePos();
        for (Entity entity : client.world.getEntities()) {
            if (entity.squaredDistanceTo(eye) > RANGE * RANGE) {
                continue;
            }
            if (entity instanceof LivingEntity living) {
                for (EquipmentSlot slot : SLOTS) {
                    ItemStack stack = living.getEquippedStack(slot);
                    Spark.Effects fx = effectsOf(stack);
                    if (fx == null || fx.idle.isEmpty()) {
                        continue;
                    }
                    GearKind kind = ((GearItem) stack.getItem()).kind();
                    boolean armorSlot = slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND;
                    if (kind.worn() != armorSlot || (kind.worn() && kind.slot != slot)) {
                        continue; // a worn piece only counts where it is worn; hand items only in hands
                    }
                    for (int i = 0; i < fx.idle.size(); i++) {
                        Spark s = fx.idle.get(i);
                        String anchor = !s.anchor.isEmpty() ? s.anchor : defaultAnchor(kind, slot);
                        if ("item".equals(anchor) && slot == EquipmentSlot.OFFHAND) {
                            anchor = "item_off";
                        }
                        emitOrFollow(entity, s, anchor, slot.name() + i, time, now);
                    }
                }
            } else if (entity instanceof AibArrowEntity arrow && arrow.isFlying()) {
                Spark.Effects fx = effectsOf(arrow.bow());
                if (fx != null && !fx.trail.isEmpty()) {
                    emitOrFollow(arrow, fx.trail.get(0), "arrow", "trail", time, now);
                }
            }
        }
        FOLLOWING.values().removeIf(e -> {
            if (!e.seen) {
                EFFECTS.remove(e);
                return true;
            }
            return false;
        });
    }

    private static void emitOrFollow(Entity entity, Spark s, String anchor, String key, long time, double now) {
        if (s.every > 0) {
            if ((time + entity.getId()) % s.every == 0) {
                // Arrows leave theirs behind as a trail; on a wearer or a held item it moves along with them.
                Entity follow = entity instanceof AibArrowEntity ? null : entity;
                add(new Effect(s, anchorPos(entity, anchor, 1.0F), dirOf(entity, anchor, 1.0F), 1, now, follow, anchor, true));
            }
            return;
        }
        String id = entity.getId() + "/" + key;
        Effect e = FOLLOWING.get(id);
        if (e == null || e.spark != s) {
            if (e != null) {
                EFFECTS.remove(e);
            }
            e = new Effect(s, anchorPos(entity, anchor, 1.0F), dirOf(entity, anchor, 1.0F), 1, now, entity, anchor);
            FOLLOWING.put(id, e);
            add(e);
        }
        e.seen = true;
    }

    private static String defaultAnchor(GearKind kind, EquipmentSlot slot) {
        if (!kind.worn()) {
            return "item";
        }
        if (kind.slot == EquipmentSlot.HEAD) {
            return "head";
        }
        if (kind.slot == EquipmentSlot.LEGS) {
            return "legs";
        }
        if (kind.slot == EquipmentSlot.FEET) {
            return "feet";
        }
        return kind == GearKind.BACK ? "back" : "body";
    }

    private static Vec3d lookOf(Entity entity) {
        if (entity instanceof AibArrowEntity arrow) {
            Vec3d v = arrow.getVelocity();
            return v.lengthSquared() < 1e-6 ? arrow.getRotationVec(1.0F) : v.normalize();
        }
        float yaw = (entity instanceof LivingEntity l ? l.getBodyYaw() : entity.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    /** Where an effect sits on an entity: on the head as it turns and nods, on the held item as the arm moves. */
    static Vec3d anchorPos(Entity entity, String anchor, float delta) {
        Vec3d base = entity.getLerpedPos(delta);
        Vec3d forward = lookOf(entity);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        double h = entity.getHeight();
        return switch (anchor) {
            case "arrow" -> base;
            case "head" -> {
                // Above the head, tilting with it: up is the head's own up, not the world's.
                Vec3d eye = entity.getCameraPosVec(delta);
                Vec3d[] axes = headAxes(entity, delta);
                yield eye.add(axes[1].multiply(0.45)).add(axes[0].multiply(-0.05));
            }
            case "body", "around" -> base.add(0, h * 0.55, 0);
            case "back" -> base.add(0, h * 0.62, 0).subtract(forward.multiply(0.4));
            case "feet" -> base.add(0, 0.05, 0);
            case "legs" -> base.add(0, h * 0.28, 0);
            case "item_off" -> heldItem(entity, delta, false, right, forward, base, h);
            default -> heldItem(entity, delta, true, right, forward, base, h);
        };
    }

    /** Which way an effect faces: where the head looks for head effects, along the item for held ones, else the body. */
    static Vec3d dirOf(Entity entity, String anchor, float delta) {
        if ("head".equals(anchor)) {
            return headAxes(entity, delta)[0];
        }
        return lookOf(entity);
    }

    /** The head's forward and up directions (it turns with the head yaw and nods with the pitch). */
    private static Vec3d[] headAxes(Entity entity, float delta) {
        Vec3d look = entity.getRotationVec(delta).normalize();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        Vec3d side = flat.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : new Vec3d(-flat.z, 0, flat.x).normalize();
        Vec3d up = side.crossProduct(look).normalize();
        if (up.y < 0) {
            up = up.multiply(-1);
        }
        return new Vec3d[] { look, up };
    }

    /**
     * The middle of the item held in a hand, following the arm: it swings with walking, lifts for
     * an attack and points ahead while a bow is drawn (the same arm movements the player model makes).
     */
    private static Vec3d heldItem(Entity entity, float delta, boolean mainHand, Vec3d right, Vec3d forward, Vec3d base, double h) {
        boolean rightSide = mainHand;
        if (entity instanceof LivingEntity living && living.getMainArm() == net.minecraft.util.Arm.LEFT) {
            rightSide = !mainHand;
        }
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d shoulder = base.add(0, h * 0.75, 0).add(right.multiply(rightSide ? -0.37 : 0.37));
        double pitch = -Math.PI / 10; // the arm holding an item, a little forward
        double along = 0.5;
        if (entity instanceof LivingEntity living) {
            float limbPos = living.limbAnimator.getPos(delta);
            float limbSpeed = Math.min(1.0F, living.limbAnimator.getSpeed(delta));
            pitch += MathHelper.cos(limbPos * 0.6662F + (rightSide ? (float) Math.PI : 0)) * limbSpeed * 0.5;
            if (mainHand) {
                float swing = living.getHandSwingProgress(delta);
                if (swing > 0) {
                    pitch -= Math.sin(swing * Math.PI) * 1.3; // up and over for the attack
                }
            }
            if (living.isUsingItem() && (living.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND) == mainHand) {
                pitch = -Math.PI / 2 - living.getPitch(delta) * MathHelper.RADIANS_PER_DEGREE; // aimed where it looks
            }
            ItemStack stack = mainHand ? living.getMainHandStack() : living.getOffHandStack();
            GearModelRenderer.Model model = stack.getItem() instanceof GearItem ? GearModelRenderer.modelFor(stack) : null;
            if (model != null) {
                along = Math.max(0.2, Math.min(1.6, model.length() * 0.5));
            }
        }
        // The arm hangs down at pitch 0 and turns forward as the pitch goes negative.
        Vec3d arm = up.multiply(-Math.cos(pitch)).add(forward.multiply(-Math.sin(pitch)));
        Vec3d hand = shoulder.add(arm.multiply(0.62));
        // The item sticks out of the fist at a right angle to the arm.
        Vec3d item = forward.multiply(Math.cos(pitch)).add(up.multiply(-Math.sin(pitch)));
        return hand.add(item.multiply(along));
    }

    // ------------------------------------------------------------------ drawing

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || EFFECTS.isEmpty()) {
            return;
        }
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        Quaternionf rot = camera.getRotation();
        Vector3f camRight = rot.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = rot.transform(new Vector3f(0, 1, 0));
        float delta = client.getRenderTickCounter().getTickDelta(true);
        double now = client.world.getTime() + delta;
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
        VertexConsumer buffer = immediate.getBuffer(layer);
        int budget = MAX_DOTS_PER_FRAME;
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            Spark s = e.spark;
            double age = Math.max(0, now - e.start);
            boolean loops = e.follow != null && !e.once;
            double t = age / s.ticks;
            if (!loops && t >= 1) {
                it.remove();
                continue;
            }
            if (e.follow != null) {
                if (e.follow.isRemoved()) {
                    if (!loops) {
                        it.remove();
                    }
                    continue;
                }
                e.origin = anchorPos(e.follow, e.anchor, delta);
                e.dir = dirOf(e.follow, e.anchor, delta);
            }
            if (loops) {
                t = t - Math.floor(t);
            }
            budget -= draw(buffer, e, t, age / 20.0, cam, camRight, camUp, budget);
            if (budget <= 0) {
                break;
            }
        }
        immediate.draw(layer);
    }

    /** Draws one effect at life fraction t (0-1), age in seconds; returns the dots drawn. */
    private static int draw(VertexConsumer buffer, Effect e, double t, double seconds, Vec3d cam, Vector3f camRight, Vector3f camUp,
                            int budget) {
        Spark s = e.spark;
        List<String> m = s.moves;
        Vec3d d = e.dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : e.dir.normalize();
        Vec3d upWorld = new Vec3d(0, 1, 0);
        Vec3d right = d.crossProduct(upWorld);
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(d).normalize();
        Vec3d flatForward = new Vec3d(d.x, 0, d.z);
        flatForward = flatForward.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flatForward.normalize();
        Vec3d flatRight = new Vec3d(-flatForward.z, 0, flatForward.x);
        // Shape axes in the world: u, v (the shape's plane) and w (out of it).
        Vec3d u, v, w;
        switch (s.plane) {
            case "flat" -> {
                u = flatRight;
                v = flatForward;
                w = upWorld;
            }
            case "side" -> {
                u = d;
                v = up;
                w = right;
            }
            default -> {
                u = right;
                v = up;
                w = d.multiply(-1);
            }
        }
        double size = s.size * e.scale;
        if (m.contains("expand")) {
            size *= 1 + 1.6 * t;
        }
        if (m.contains("shrink")) {
            size *= 1 - 0.9 * t;
        }
        if (m.contains("pulse")) {
            size *= 1 + 0.18 * Math.sin(seconds * Math.PI * 2 * Math.max(0.5, s.speed));
        }
        double spin = m.contains("spin") ? seconds * Math.PI * 2 * s.speed : 0;
        double cos = Math.cos(spin), sin = Math.sin(spin);
        Vec3d center = e.origin;
        double travel = seconds * s.speed;
        if (m.contains("forward")) {
            center = center.add(d.multiply(travel));
        }
        if (m.contains("back")) {
            center = center.subtract(d.multiply(travel));
        }
        if (m.contains("rise")) {
            center = center.add(0, travel, 0);
        }
        if (m.contains("fall")) {
            center = center.add(0, -travel, 0);
        }
        if (m.contains("orbit")) {
            double a = seconds * Math.PI * 2 * Math.max(0.2, s.speed);
            center = center.add(flatRight.multiply(Math.cos(a) * 0.6 * e.scale)).add(flatForward.multiply(Math.sin(a) * 0.6 * e.scale));
        }
        float alpha = 1;
        if (m.contains("fade")) {
            alpha = (float) Math.max(0, 1 - t * t);
        }
        int rgb = s.color;
        if (s.color2 >= 0) {
            rgb = lerpColor(s.color, s.color2, t);
        }
        double half = s.dot * e.scale * 0.5 * (m.contains("shrink") ? Math.max(0.3, 1 - t) : 1);
        int n = Math.min(e.dots.size(), budget);
        int drawn = 0;
        for (int i = 0; i < n; i++) {
            double[] p = e.dots.get(i);
            if (m.contains("reveal") && (i + 0.5) / n > Math.min(1, t * 2.2)) {
                continue;
            }
            double pu = p[0] * cos - p[1] * sin;
            double pv = p[0] * sin + p[1] * cos;
            double pw = p[2];
            if (m.contains("wave")) {
                pv += 0.15 * Math.sin(p[0] * Math.PI * 2 + seconds * 6);
            }
            double radial = 1;
            if (m.contains("scatter")) {
                radial += travel / Math.max(0.1, size) * 1.0;
            }
            if (m.contains("gather")) {
                radial *= 1 + 2.5 * (1 - t);
            }
            double k = size * radial;
            double x = center.x + (u.x * pu + v.x * pv + w.x * pw) * k;
            double y = center.y + (u.y * pu + v.y * pv + w.y * pw) * k;
            double z = center.z + (u.z * pu + v.z * pv + w.z * pw) * k;
            float a = alpha;
            if (m.contains("flicker")) {
                a *= 0.35F + 0.65F * (float) Math.abs(Math.sin(seconds * 23 + i * 1.7));
            }
            if (a <= 0.02F) {
                continue;
            }
            int argb = ((int) (a * 255) << 24) | rgb;
            quad(buffer, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), (float) half, camRight, camUp, argb);
            drawn++;
        }
        return drawn;
    }

    private static void quad(VertexConsumer b, float x, float y, float z, float h, Vector3f r, Vector3f u, int argb) {
        float rx = r.x * h, ry = r.y * h, rz = r.z * h, ux = u.x * h, uy = u.y * h, uz = u.z * h;
        b.vertex(x - rx - ux, y - ry - uy, z - rz - uz).color(argb).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x + rx - ux, y + ry - uy, z + rz - uz).color(argb).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x + rx + ux, y + ry + uy, z + rz + uz).color(argb).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x - rx + ux, y - ry + uy, z - rz + uz).color(argb).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** Tests: how many effects are being drawn. */
    public static int activeCount() {
        return EFFECTS.size();
    }
}
