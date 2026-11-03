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

    /** The dot looks side by side (dot, glow, star, streak, shard, rune, ember, ring). */
    private static final Identifier TEXTURE = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/looks.png");
    private static final int LOOK_CELLS = 8;
    /** Smooth pieces: blade band, ground ring, light column, magic circle (cells of forms.png). */
    private static final Identifier FORMS = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/forms.png");
    private static final int FORM_CELLS = 4;
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
        /** Vanilla particles given off so far, the part of one still owed, and the age they were last given at. */
        int emitted;
        double emitAcc;
        double lastAge = -1;
        /** Its sound (and shake) still to come: it starts after a delay. */
        boolean startDue;
        /** A preview: it ends at this time even though it follows and loops (0 = no end). */
        double until;
        /** Ribbon: where it has been ({x, y, z, time}), newest last. */
        final java.util.ArrayDeque<double[]> path = new java.util.ArrayDeque<>();

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
            // Layers may start later than the first (a flash, then a ring, then sparks).
            Effect e = new Effect(s, at, dir, scale, now + s.delay + s.lead, null, "");
            e.startDue = true;
            add(e);
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
        if (s.every > 0 && !"ribbon".equals(s.form)) { // a ribbon is one band that follows, never copies
            if ((time + entity.getId()) % s.every == 0) {
                // Arrows leave theirs behind as a trail; on a wearer or a held item it moves along with them.
                Entity follow = entity instanceof AibArrowEntity ? null : entity;
                Vec3d at = anchorPos(entity, anchor, 1.0F);
                add(new Effect(s, at, dirOf(entity, anchor, 1.0F), 1, now, follow, anchor, true));
                if (s.every >= 10) {
                    playSound(MinecraftClient.getInstance(), s, at); // not for copies every few ticks: that would buzz
                }
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
        particlesThisFrame = 0;
        List<Object[]> forms = new ArrayList<>(); // smooth pieces, drawn after the dots with their own texture
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            Spark s = e.spark;
            if (now < e.start) {
                continue; // a layer that starts later
            }
            if (e.until > 0 && now > e.until) {
                it.remove();
                continue;
            }
            double age = now - e.start;
            if (e.startDue) {
                e.startDue = false;
                playSound(client, s, e.origin);
                if (s.shake > 0 && client.player != null && client.player.getPos().squaredDistanceTo(e.origin) < 36) {
                    ScreenShake.start(s.shake);
                }
            }
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
            if (!s.particle.isEmpty() && emitParticles(client, e, t, age / 20.0, loops)) {
                continue;
            }
            if (!"dots".equals(s.form)) {
                if ("ribbon".equals(s.form)) {
                    remember(e, now, age / 20.0);
                }
                forms.add(new Object[] { e, t, age / 20.0, loops, now });
                continue;
            }
            budget -= draw(buffer, e, t, age / 20.0, cam, camRight, camUp, budget, loops);
            if (budget <= 0) {
                break;
            }
        }
        immediate.draw(layer);
        if (!forms.isEmpty()) {
            RenderLayer formLayer = RenderLayer.getEntityTranslucentEmissive(FORMS);
            VertexConsumer formBuffer = immediate.getBuffer(formLayer);
            for (Object[] f : forms) {
                drawForm(formBuffer, (Effect) f[0], (double) f[1], (double) f[2], (boolean) f[3], (double) f[4], cam);
            }
            immediate.draw(formLayer);
        }
    }

    // ------------------------------------------------------------------ smooth pieces

    /** Ribbon: add where it is now to its path (when it has moved) and forget what is too old. */
    private static void remember(Effect e, double now, double seconds) {
        double[] last = e.path.peekLast();
        Vec3d o = centerOf(e, axes(e), seconds); // an orbiting ribbon follows its orbit
        if (last == null || (o.x - last[0]) * (o.x - last[0]) + (o.y - last[1]) * (o.y - last[1]) + (o.z - last[2]) * (o.z - last[2]) > 0.0025) {
            e.path.addLast(new double[] { o.x, o.y, o.z, now });
        }
        while (!e.path.isEmpty() && (now - e.path.peekFirst()[3] > e.spark.ticks || e.path.size() > 64)) {
            e.path.pollFirst();
        }
    }

    private static double sizeAt(Effect e, double t, double seconds) {
        Spark s = e.spark;
        double size = s.size * e.scale;
        if (s.moves.contains("expand")) {
            size *= 1 + 1.6 * t;
        }
        if (s.moves.contains("shrink")) {
            size *= 1 - 0.9 * t;
        }
        if (s.moves.contains("pulse")) {
            size *= 1 + 0.18 * Math.sin(seconds * Math.PI * 2 * Math.max(0.5, s.speed));
        }
        return size;
    }

    private static double smooth(double edge0, double edge1, double x) {
        double k = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return k * k * (3 - 2 * k);
    }

    /** One smooth piece: a blade band, a ground ring, a light column or ray, a magic circle or a ribbon. */
    private static void drawForm(VertexConsumer b, Effect e, double t, double seconds, boolean loops, double now, Vec3d cam) {
        Spark s = e.spark;
        Axes ax = axes(e);
        Vec3d c = centerOf(e, ax, seconds);
        double size = sizeAt(e, t, seconds);
        // Smooth pieces fade out at the end of their life rather than vanish at once.
        float alpha = loops ? 1 : (float) (s.moves.contains("fade") ? 1 - t * t : 1 - smooth(0.65, 1, t));
        if (s.moves.contains("flicker")) {
            alpha *= 0.5F + 0.5F * (float) Math.abs(Math.sin(seconds * 23));
        }
        int rgb = colorAt(s, t);
        double spin = s.moves.contains("spin") ? seconds * Math.PI * 2 * s.speed : 0;
        Vec3d u = ax.u(), v = ax.v();
        if (spin != 0) {
            Vec3d nu = u.multiply(Math.cos(spin)).add(v.multiply(Math.sin(spin)));
            Vec3d nv = v.multiply(Math.cos(spin)).subtract(u.multiply(Math.sin(spin)));
            u = nu;
            v = nv;
        }
        switch (s.form) {
            case "swipe" -> {
                // The band a blade leaves: it sweeps along the arc quickly, then the tail catches up.
                double head = Math.min(1, t / 0.3), tail = Math.max(0, (t - 0.2) / 0.8);
                double span = Math.toRadians(s.arc);
                int k = 28;
                for (int i = 0; i < k; i++) {
                    double s0 = tail + (head - tail) * i / k, s1 = tail + (head - tail) * (i + 1) / k;
                    Vec3d[] q0 = swipeEdge(c, u, v, size, span, s0, s.width);
                    Vec3d[] q1 = swipeEdge(c, u, v, size, span, s1, s.width);
                    // Brightest at the head (where the blade is), clear at the tail.
                    float a0 = alpha * (float) Math.pow((double) i / k, 0.7), a1 = alpha * (float) Math.pow((double) (i + 1) / k, 0.7);
                    face(b, cam, q0[0], q1[0], q1[1], q0[1], 0, a0, a1, a1, a0, rgb, (float) s0, (float) s1);
                }
            }
            case "shockwave" -> {
                // A ring spreading out over the ground, thinning as it goes.
                double eased = 1 - Math.pow(1 - Math.min(1, t), 3);
                double outer = size * (0.15 + eased);
                double inner = Math.max(0, outer - size * s.width * (1 - 0.6 * t));
                ring(b, cam, c.add(0, 0.03, 0), ax.flatRight(), ax.flatForward(), inner, outer, 1, rgb, alpha * (float) (1 - 0.5 * t));
            }
            case "disc" -> {
                // A magic circle, turning when it spins.
                Vec3d p0 = c.add(u.multiply(-size)).add(v.multiply(-size)), p1 = c.add(u.multiply(size)).add(v.multiply(-size));
                Vec3d p2 = c.add(u.multiply(size)).add(v.multiply(size)), p3 = c.add(u.multiply(-size)).add(v.multiply(size));
                int argb = argb(alpha, rgb);
                quadUv(b, cam, p0, p1, p2, p3, 3, 0, 1, 1, 0, argb, argb, argb, argb);
            }
            case "beam" -> {
                if (s.reach > 0) {
                    // A bow's beam shot: from the bow to where it stopped, there at once, then fading.
                    Vec3d end = c.add(ax.d().multiply(s.reach));
                    double half = s.width * 0.5;
                    alpha *= (float) s.opacity;
                    beam(b, cam, c, end, half, half, argb(alpha * 0.55F, rgb), argb(alpha * 0.55F, rgb));
                    beam(b, cam, c, end, half * 0.35, half * 0.35, argb(alpha, 0xFFFFFF), argb(alpha, 0xFFFFFF));
                } else {
                    double length = size * 4 * Math.min(1, t * 5 + (loops ? 1 : 0));
                    double half = size * s.width * 0.6;
                    beam(b, cam, c, c.add(ax.d().multiply(length)), half, half * 0.6, argb(alpha, rgb), argb(alpha * 0.25F, rgb));
                }
            }
            case "bolt" -> bolt(b, cam, e, c, size, t, seconds, alpha, rgb);
            case "pillar" -> {
                // A column of light up from the ground; it shoots up quickly.
                Vec3d axis = new Vec3d(0, 1, 0);
                double length = size * 4 * Math.min(1, t * 5 + (loops ? 1 : 0));
                Vec3d end = c.add(axis.multiply(length));
                double half = size * s.width * 0.6;
                beam(b, cam, c, end, half, half * 0.6, argb(alpha, rgb), argb(alpha * 0.25F, rgb));
            }
            case "ribbon" -> {
                // A band through where it has been: wide and bright at the newest end, thin and clear at the oldest.
                double[] prev = null;
                double half = size * s.width * 0.5;
                for (double[] p : e.path) {
                    if (prev != null) {
                        float f0 = (float) Math.max(0, 1 - (now - prev[3]) / s.ticks), f1 = (float) Math.max(0, 1 - (now - p[3]) / s.ticks);
                        beam(b, cam, new Vec3d(prev[0], prev[1], prev[2]), new Vec3d(p[0], p[1], p[2]), half * f0, half * f1,
                            argb(alpha * f0, colorAt(s, 1 - f0)), argb(alpha * f1, colorAt(s, 1 - f1)));
                    }
                    prev = p;
                }
            }
            default -> {
            }
        }
    }

    /** The outer and inner edge of the swipe at a point along its arc (thick in the middle, thin at the tips). */
    private static Vec3d[] swipeEdge(Vec3d c, Vec3d u, Vec3d v, double size, double span, double along, double width) {
        double a = Math.PI / 2 - span / 2 + span * along;
        double thick = Math.min(0.95, width * 1.4 * Math.sin(Math.PI * along) + 0.04);
        double cos = Math.cos(a), sin = Math.sin(a) * 0.55;
        Vec3d outer = c.add(u.multiply(cos * size)).add(v.multiply(sin * size));
        Vec3d inner = c.add(u.multiply(cos * size * (1 - thick))).add(v.multiply(sin * size * (1 - thick)));
        return new Vec3d[] { outer, inner };
    }

    /**
     * Lightning: a jagged line from high in the sky down to the point, with a few forks, redrawn in
     * a new zigzag every couple of ticks, flickering, and a flash where it strikes.
     */
    private static void bolt(VertexConsumer b, Vec3d cam, Effect e, Vec3d hit, double size, double t, double seconds, float alpha, int rgb) {
        Spark s = e.spark;
        int frame = (int) Math.floor(seconds * 10); // a new zigzag every 2 ticks
        int seed = (int) (e.start * 31) ^ (int) Math.floor(hit.x * 7) ^ (int) Math.floor(hit.z * 13) * 17;
        // Strobing: bright, dim, bright, as real strikes do.
        float strobe = (frame % 3 == 1) ? 0.55F : 1.0F;
        float a = alpha * strobe;
        double height = 14 + size * 10;
        double spread = 0.6 + size * 1.2;
        double half = Math.max(0.08, s.width * size * 0.5);
        int segments = 14;
        Vec3d[] main = new Vec3d[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double f = (double) i / segments; // 0 = sky, 1 = the point
            double jx = i == segments ? 0 : (rand(i, seed + frame * 101) - 0.5) * spread * (1 - f * 0.7);
            double jz = i == segments ? 0 : (rand(i, seed + frame * 101 + 7) - 0.5) * spread * (1 - f * 0.7);
            main[i] = hit.add(jx, height * (1 - f), jz);
        }
        // The bolt grows down from the sky in the first two ticks, however long it lasts.
        int shown = (int) Math.ceil(segments * Math.min(1, seconds * 10));
        for (int i = 0; i < shown; i++) {
            beam(b, cam, main[i], main[i + 1], half * 2.2, half * 2.2, argb(a * 0.45F, rgb), argb(a * 0.45F, rgb));
            beam(b, cam, main[i], main[i + 1], half * 0.6, half * 0.6, argb(a, 0xFFFFFF), argb(a, 0xFFFFFF));
        }
        // Forks: from a few points of the main line, shorter crooked lines going down and out.
        for (int f = 0; f < 3; f++) {
            int from = 2 + (int) (rand(f, seed + 3) * (segments - 6));
            if (from >= shown) {
                continue;
            }
            Vec3d p = main[from];
            double ang = rand(f, seed + 5) * Math.PI * 2;
            Vec3d out = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
            for (int k = 0; k < 4; k++) {
                Vec3d q = p.add(out.multiply(spread * 0.5)).add((rand(k, seed + f * 13 + frame) - 0.5) * spread * 0.4, -height / segments * 1.2,
                    (rand(k, seed + f * 17 + frame) - 0.5) * spread * 0.4);
                float fade = a * (1 - k / 4F);
                beam(b, cam, p, q, half * 1.4 * (1 - k / 5.0), half * 1.4 * (1 - (k + 1) / 5.0), argb(fade * 0.4F, rgb), argb(fade * 0.3F, rgb));
                beam(b, cam, p, q, half * 0.4, half * 0.3, argb(fade, 0xFFFFFF), argb(fade * 0.8F, 0xFFFFFF));
                p = q;
            }
        }
        // The flash where it strikes: a bright disc on the ground that dies quickly.
        if (shown >= segments) {
            float flash = alpha * (float) Math.max(0, 1 - seconds * 4); // a quarter of a second
            Vec3d x = new Vec3d(1, 0, 0), z = new Vec3d(0, 0, 1);
            ring(b, cam, hit.add(0, 0.05, 0), x, z, 0, 0.6 + size * 1.6, 1, rgb, flash);
            beam(b, cam, hit, hit.add(0, 0.8 + size, 0), 0.5 + size, 0.1, argb(flash, 0xFFFFFF), argb(0, 0xFFFFFF));
        }
    }

    /** A flat ring between two radii (texture across it: outer edge v = 0). */
    private static void ring(VertexConsumer b, Vec3d cam, Vec3d c, Vec3d x, Vec3d z, double inner, double outer, int cell, int rgb, float alpha) {
        int n = 48;
        int argb = argb(alpha, rgb);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3d d0 = x.multiply(Math.cos(a0)).add(z.multiply(Math.sin(a0))), d1 = x.multiply(Math.cos(a1)).add(z.multiply(Math.sin(a1)));
            quadUv(b, cam, c.add(d0.multiply(outer)), c.add(d1.multiply(outer)), c.add(d1.multiply(inner)), c.add(d0.multiply(inner)),
                cell, (float) i / n, (float) (i + 1) / n, 0, 1, argb, argb, argb, argb);
        }
    }

    /** A band of light from one point to another, always turned to face the camera (texture across its width). */
    private static void beam(VertexConsumer b, Vec3d cam, Vec3d from, Vec3d to, double halfFrom, double halfTo, int argbFrom, int argbTo) {
        Vec3d axis = to.subtract(from);
        if (axis.lengthSquared() < 1e-8) {
            return;
        }
        Vec3d mid = from.add(to).multiply(0.5);
        Vec3d side = axis.crossProduct(mid.subtract(cam));
        if (side.lengthSquared() < 1e-8) {
            return;
        }
        side = side.normalize();
        quadUv(b, cam, from.add(side.multiply(halfFrom)), from.subtract(side.multiply(halfFrom)), to.subtract(side.multiply(halfTo)),
            to.add(side.multiply(halfTo)), 2, 1, 0, 1, 0, argbFrom, argbFrom, argbTo, argbTo);
    }

    /** A swipe segment: outer edge v = 0, inner edge v = 1, alpha per corner. */
    private static void face(VertexConsumer b, Vec3d cam, Vec3d o0, Vec3d o1, Vec3d i1, Vec3d i0, int cell, float ao0, float ao1, float ai1,
                             float ai0, int rgb, float s0, float s1) {
        quadUv(b, cam, o0, o1, i1, i0, cell, s0, s1, 0, 1, argb(ao0, rgb), argb(ao1, rgb), argb(ai1, rgb), argb(ai0, rgb));
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * A quad p0 p1 p2 p3 in a forms.png cell: u from uA (p0, p3) to uB (p1, p2) inside the cell, v from
     * vA (p0, p1) to vB (p2, p3). Drawn from both sides.
     */
    private static void quadUv(VertexConsumer b, Vec3d cam, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, int cell, float uA, float uB, float vA,
                               float vB, int c0, int c1, int c2, int c3) {
        float base = cell / (float) FORM_CELLS, w = 1F / FORM_CELLS;
        float ua = base + w * Math.max(0, Math.min(1, uA)), ub = base + w * Math.max(0, Math.min(1, uB));
        vert(b, cam, p0, ua, vA, c0);
        vert(b, cam, p1, ub, vA, c1);
        vert(b, cam, p2, ub, vB, c2);
        vert(b, cam, p3, ua, vB, c3);
        vert(b, cam, p3, ua, vB, c3);
        vert(b, cam, p2, ub, vB, c2);
        vert(b, cam, p1, ub, vA, c1);
        vert(b, cam, p0, ua, vA, c0);
    }

    private static void vert(VertexConsumer b, Vec3d cam, Vec3d p, float u, float v, int argb) {
        b.vertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(argb).texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
    }

    /** The axes an effect is drawn in this frame: its shape plane (u, v, w) and its direction. */
    private record Axes(Vec3d d, Vec3d u, Vec3d v, Vec3d w, Vec3d flatRight, Vec3d flatForward) {
    }

    private static Axes axes(Effect e) {
        Spark s = e.spark;
        Vec3d d = e.dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : e.dir.normalize();
        Vec3d upWorld = new Vec3d(0, 1, 0);
        Vec3d right = d.crossProduct(upWorld);
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(d).normalize();
        Vec3d flatForward = new Vec3d(d.x, 0, d.z);
        flatForward = flatForward.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flatForward.normalize();
        Vec3d flatRight = new Vec3d(-flatForward.z, 0, flatForward.x);
        return switch (s.plane) {
            case "flat" -> new Axes(d, flatRight, flatForward, upWorld, flatRight, flatForward);
            case "side" -> new Axes(d, d, up, right, flatRight, flatForward);
            default -> new Axes(d, right, up, d.multiply(-1), flatRight, flatForward);
        };
    }

    /** A number 0-1 for dot i, the same every frame (and for every player). */
    private static double rand(int i, int salt) {
        long h = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** How far a move along a direction has gone after some seconds (slowing down with drag). */
    private static double travel(Spark s, double seconds) {
        double k = s.drag * 5;
        return k < 1e-3 ? seconds * s.speed : s.speed * (1 - Math.exp(-k * seconds)) / k;
    }

    /** How far a dot has fallen (gravity, slowed by drag like a falling ember). */
    private static double drop(double g, double drag, double seconds) {
        double k = drag * 5;
        return k < 1e-3 ? 0.5 * g * seconds * seconds : g * (seconds / k - (1 - Math.exp(-k * seconds)) / (k * k));
    }

    /** Where the middle of the effect is at this time: it may go forward, back, up or down, or orbit. */
    private static Vec3d centerOf(Effect e, Axes ax, double seconds) {
        Spark s = e.spark;
        List<String> m = s.moves;
        Vec3d center = e.origin;
        double travel = travel(s, seconds);
        if (m.contains("forward")) {
            center = center.add(ax.d().multiply(travel));
        }
        if (m.contains("back")) {
            center = center.subtract(ax.d().multiply(travel));
        }
        if (m.contains("rise")) {
            center = center.add(0, travel, 0);
        }
        if (m.contains("fall")) {
            center = center.add(0, -travel, 0);
        }
        if (m.contains("orbit")) {
            double a = seconds * Math.PI * 2 * Math.max(0.2, s.speed);
            center = center.add(ax.flatRight().multiply(Math.cos(a) * 0.6 * e.scale)).add(ax.flatForward().multiply(Math.sin(a) * 0.6 * e.scale));
        }
        return center;
    }

    /** Where dot i is at this time (world). */
    private static Vec3d dotPos(Effect e, Axes ax, int i, int n, double[] p, double seconds, double t) {
        Spark s = e.spark;
        List<String> m = s.moves;
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
        Vec3d center = centerOf(e, ax, seconds);
        double travel = travel(s, seconds);
        double pu = p[0] * cos - p[1] * sin;
        double pv = p[0] * sin + p[1] * cos;
        double pw = p[2];
        if (m.contains("wave")) {
            pv += 0.15 * Math.sin(p[0] * Math.PI * 2 + seconds * 6);
        }
        double radial = 1;
        if (m.contains("scatter")) {
            radial += travel / Math.max(0.1, size) * (1 + s.vary * (rand(i, 4) - 0.5));
        }
        if (m.contains("gather")) {
            radial *= 1 + 2.5 * (1 - t);
        }
        double k = size * radial;
        Vec3d u = ax.u(), v = ax.v(), w = ax.w();
        double x = center.x + (u.x * pu + v.x * pv + w.x * pw) * k;
        double y = center.y + (u.y * pu + v.y * pv + w.y * pw) * k;
        double z = center.z + (u.z * pu + v.z * pv + w.z * pw) * k;
        if (s.gravity != 0) {
            y -= drop(s.gravity * (1 + s.vary * (rand(i, 3) - 0.5)), s.drag, seconds);
        }
        if (s.turbulence > 0) {
            // Drifting and swirling, growing over the first half second.
            double amp = s.turbulence * 0.4 * Math.min(1, seconds * 2) * (0.6 + 0.8 * rand(i, 5)) * Math.max(0.3, size);
            x += amp * Math.sin(seconds * 2.3 + i * 1.7);
            y += amp * 0.6 * Math.sin(seconds * 1.9 + i * 2.9 + 1);
            z += amp * Math.cos(seconds * 2.7 + i * 0.7);
        }
        return new Vec3d(x, y, z);
    }

    /** The colour at life fraction t: through the colour stops, or from color to color2. */
    static int colorAt(Spark s, double t) {
        if (s.colors != null && s.colors.length >= 2) {
            double f = Math.max(0, Math.min(1, t)) * (s.colors.length - 1);
            int k = Math.min(s.colors.length - 2, (int) Math.floor(f));
            return lerpColor(s.colors[k], s.colors[k + 1], f - k);
        }
        return s.color2 >= 0 ? lerpColor(s.color, s.color2, t) : s.color;
    }

    /** Draws one effect at life fraction t (0-1), age in seconds; returns the dots drawn. */
    private static int draw(VertexConsumer buffer, Effect e, double t, double seconds, Vec3d cam, Vector3f camRight, Vector3f camUp,
                            int budget, boolean loops) {
        Spark s = e.spark;
        List<String> m = s.moves;
        Axes ax = axes(e);
        int cell = Math.max(0, Spark.LOOKS.indexOf(s.look));
        float u0 = cell / (float) LOOK_CELLS, u1 = (cell + 1) / (float) LOOK_CELLS;
        double baseHalf = s.dot * e.scale * 0.5;
        int n = Math.min(e.dots.size(), budget);
        int drawn = 0;
        for (int i = 0; i < n; i++) {
            double[] p = e.dots.get(i);
            // Each dot lives a little shorter or longer than the others when the effect varies.
            double ti = t;
            if (!loops && s.vary > 0) {
                ti = t / (1 - s.vary * 0.55 * rand(i, 2));
                if (ti >= 1) {
                    continue;
                }
            }
            if (m.contains("reveal") && (i + 0.5) / n > Math.min(1, ti * 2.2)) {
                continue;
            }
            Vec3d at = dotPos(e, ax, i, n, p, seconds, ti);
            float a = 1;
            if (m.contains("fade")) {
                a = (float) Math.max(0, 1 - ti * ti);
            }
            if (m.contains("flicker")) {
                a *= 0.35F + 0.65F * (float) Math.abs(Math.sin(seconds * 23 + i * 1.7));
            }
            double half = baseHalf * (m.contains("shrink") ? Math.max(0.3, 1 - ti) : 1) * (1 + s.vary * (rand(i, 1) - 0.5) * 1.2);
            float x = (float) (at.x - cam.x), y = (float) (at.y - cam.y), z = (float) (at.z - cam.z);
            Vector3f ra = new Vector3f(camRight), ua = new Vector3f(camUp);
            switch (s.look) {
                case "glow" -> {
                    half *= 2.2;
                    a *= 0.75F;
                }
                case "ring" -> half *= 1.6;
                case "ember" -> half *= 1.3;
                case "star" -> {
                    a *= 0.6F + 0.4F * (float) Math.abs(Math.sin(seconds * 15 + i * 2.1)); // twinkle
                    turn(ra, ua, seconds * 4 + i * 0.9);
                    half *= 1.4;
                }
                case "shard" -> turn(ra, ua, seconds * 3 + i * 2.4);
                case "rune" -> {
                    turn(ra, ua, seconds * 1.2 + i);
                    half *= 1.3;
                }
                case "streak" -> {
                    // Stretched along the way the dot moves, as seen from the camera.
                    Vec3d before = dotPos(e, ax, i, n, p, Math.max(0, seconds - 0.05), Math.max(0, ti - 0.05 * 20 / Math.max(2, s.ticks)));
                    Vec3d vel = at.subtract(before);
                    Vec3d view = new Vec3d(x, y, z).normalize();
                    Vec3d along = vel.subtract(view.multiply(vel.dotProduct(view)));
                    if (along.lengthSquared() > 1e-8) {
                        double len = Math.min(6, 1.4 + along.length() / 0.05 * 0.15);
                        Vec3d dirA = along.normalize();
                        Vec3d dirB = view.crossProduct(dirA).normalize();
                        ra.set((float) (dirA.x * len), (float) (dirA.y * len), (float) (dirA.z * len));
                        ua.set((float) (dirB.x * 0.7), (float) (dirB.y * 0.7), (float) (dirB.z * 0.7));
                    }
                }
                default -> {
                }
            }
            if (a <= 0.02F) {
                continue;
            }
            int argb = ((int) (Math.min(1, a) * 255) << 24) | colorAt(s, ti);
            quad(buffer, x, y, z, (float) half, ra, ua, argb, u0, u1);
            drawn++;
        }
        return drawn;
    }

    /** Turn the two quad axes by an angle (for spinning stars, shards and runes). */
    private static void turn(Vector3f r, Vector3f u, double angle) {
        float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
        Vector3f nr = new Vector3f(r).mul(c).add(new Vector3f(u).mul(sn));
        Vector3f nu = new Vector3f(u).mul(c).sub(new Vector3f(r).mul(sn));
        r.set(nr);
        u.set(nu);
    }

    // ------------------------------------------------------------------ vanilla particles

    private static final Map<String, net.minecraft.particle.ParticleEffect> PARTICLE_TYPES = new HashMap<>();
    private static final int MAX_PARTICLES_PER_FRAME = 160;
    private static int particlesThisFrame;

    /** The vanilla particle of an effect (dust takes its colours), or null to draw our own dots. */
    private static net.minecraft.particle.ParticleEffect particleOf(Spark s) {
        if (s.particle.isEmpty()) {
            return null;
        }
        if ("dust".equals(s.particle)) {
            float scale = (float) Math.max(0.3, Math.min(4, s.dot * 12));
            int from = colorAt(s, 0), to = colorAt(s, 1);
            return from == to ? new net.minecraft.particle.DustParticleEffect(from, scale)
                : new net.minecraft.particle.DustColorTransitionParticleEffect(from, to, scale);
        }
        return PARTICLE_TYPES.computeIfAbsent(s.particle, id -> {
            var type = net.minecraft.registry.Registries.PARTICLE_TYPE.get(Identifier.ofVanilla(id));
            return type instanceof net.minecraft.particle.SimpleParticleType simple ? simple : null;
        });
    }

    /**
     * Vanilla particles at the shape's dots: a one-off effect gives them all off at once, moving
     * the way its dots move; a looping one keeps giving them off along its animated shape.
     */
    private static boolean emitParticles(MinecraftClient client, Effect e, double t, double seconds, boolean loops) {
        Spark s = e.spark;
        net.minecraft.particle.ParticleEffect type = particleOf(s);
        if (type == null) {
            return false;
        }
        Axes ax = axes(e);
        int n = e.dots.size();
        double ageTicks = seconds * 20;
        int want;
        if (!loops) {
            want = e.emitted == 0 ? n : 0;
        } else {
            e.emitAcc += Math.max(0, ageTicks - Math.max(0, e.lastAge)) * n / Math.max(2.0, s.ticks);
            want = (int) Math.floor(e.emitAcc);
            e.emitAcc -= want;
        }
        e.lastAge = ageTicks;
        for (int k = 0; k < want && particlesThisFrame < MAX_PARTICLES_PER_FRAME; k++) {
            int i = loops ? e.emitted % n : k;
            double[] p = e.dots.get(i);
            Vec3d at = dotPos(e, ax, i, n, p, seconds, t);
            Vec3d before = dotPos(e, ax, i, n, p, Math.max(0, seconds - 0.05), t);
            Vec3d vel = at.subtract(before).multiply(1 / 0.05 / 20.0); // blocks per tick
            double jitter = 0.02 * s.vary;
            vel = vel.add((rand(i, 7) - 0.5) * jitter, (rand(i, 8) - 0.5) * jitter, (rand(i, 9) - 0.5) * jitter);
            client.particleManager.addParticle(type, at.x, at.y, at.z, vel.x, vel.y, vel.z);
            e.emitted++;
            particlesThisFrame++;
        }
        return true;
    }

    // ------------------------------------------------------------------ sound

    /** The effect's sound, where it starts. */
    private static void playSound(MinecraftClient client, Spark s, Vec3d at) {
        if (s.sound.isEmpty() || client.world == null) {
            return;
        }
        var sound = net.minecraft.registry.Registries.SOUND_EVENT.get(Identifier.ofVanilla(s.sound));
        if (sound != null) {
            client.world.playSound(at.x, at.y, at.z, sound, net.minecraft.sound.SoundCategory.PLAYERS, (float) s.volume, (float) s.pitch, false);
        }
    }

    private static void quad(VertexConsumer b, float x, float y, float z, float h, Vector3f r, Vector3f u, int argb, float u0, float u1) {
        float rx = r.x * h, ry = r.y * h, rz = r.z * h, ux = u.x * h, uy = u.y * h, uz = u.z * h;
        b.vertex(x - rx - ux, y - ry - uy, z - rz - uz).color(argb).texture(u0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x + rx - ux, y + ry - uy, z + rz - uz).color(argb).texture(u1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x + rx + ux, y + ry + uy, z + rz + uz).color(argb).texture(u1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
        b.vertex(x - rx + ux, y - ry + uy, z - rz + uz).color(argb).texture(u0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(0, 1, 0);
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** Tests: an effect that follows an entity and loops (like one on equipment), e.g. an orbiting ribbon. */
    public static void attachForTests(Entity entity, String json, String anchor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        for (Spark s : Spark.listFromJson(json)) {
            add(new Effect(s, anchorPos(entity, anchor, 1.0F), dirOf(entity, anchor, 1.0F), 1, now(client), entity, anchor));
        }
    }

    /**
     * Preview equipment's effects without having it: its attack layers play in front of the player as
     * if swung (bows: as if the arrow landed ahead), and its held or worn effects follow the player for
     * a few seconds. Only this game sees it.
     */
    public static void preview(Spark.Effects fx, boolean worn) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || fx == null) {
            return;
        }
        var p = client.player;
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d target = p.getEyePos().add(flat.multiply(3)).add(0, -0.6, 0);
        double now = now(client);
        for (Spark s : fx.attack) {
            Vec3d at = switch (s.start) {
                case "target" -> target;
                case "ground" -> p.getPos().add(flat.multiply(3)).add(0, 0.05, 0);
                case "above" -> target.add(0, 1.5, 0);
                case "self" -> p.getPos().add(0, 0.9, 0);
                default -> p.getEyePos().add(flat.multiply(1.2)).add(0, -0.35, 0);
            };
            Effect e = new Effect(s, at, flat, 1, now + 10 + s.delay, null, "");
            e.startDue = true;
            add(e);
        }
        for (Spark s : fx.idle) {
            String anchor = !s.anchor.isEmpty() ? s.anchor : worn ? "body" : "item";
            Effect e = new Effect(s, anchorPos(p, anchor, 1.0F), dirOf(p, anchor, 1.0F), 1, now, p, anchor);
            e.until = now + 100;
            add(e);
        }
        for (Spark s : fx.trail) {
            // A ribbon or trail drawn once from the player to where the arrow would land.
            Effect e = new Effect(s, target, flat, 1, now + 10, null, "");
            add(e);
        }
        if (fx.shot != null && fx.shot.beam() && fx.shot.opacity > 0) {
            // The beam shot, from the bow to where the landing effects play.
            Vec3d from = p.getEyePos().add(0, -0.18, 0).add(look.multiply(0.6));
            add(new Effect(GearEffects.beamSpark(fx.shot, from.distanceTo(target)), from, target.subtract(from), 1, now + 10, null, ""));
        }
    }

    /** Tests: stop every effect. */
    public static void clearForTests() {
        EFFECTS.clear();
        FOLLOWING.clear();
    }

    /** Tests: how many effects are being drawn. */
    public static int activeCount() {
        return EFFECTS.size();
    }
}
