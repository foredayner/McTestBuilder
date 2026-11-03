package com.example.client;

import com.example.content.GearItem;
import com.example.content.GearKind;
import com.example.content.GearMotions;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the attack motion ordered for an AI weapon instead of the normal swing, in first person
 * (the held item) and third person (arms and body). A motion has its own clock (quicker for short
 * blades, slower for heavy weapons) and keyframes: wind up, a fast strike, a follow-through past
 * it, settling, and back to rest; the body turns and leans into it. Only the look changes;
 * damage, reach and attack speed stay vanilla.
 */
public final class GearMotionClient {

    /** How long a normal motion takes (ticks); light and heavy weapons differ (GearMotions.ticks). */
    public static final float MOTION_TICKS = 14.0F;
    /** Tests: show the motion frozen at this point (0-1); negative = off. */
    public static float forceProgress = -1;

    private static final Map<LivingEntity, float[]> SWINGS = new WeakHashMap<>();

    private GearMotionClient() {
    }

    /** The ordered motion of a held weapon or tool, or "". */
    public static String motionOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof GearItem gear) || !GearMotions.applies(gear.kind())) {
            return "";
        }
        return GearMotions.of(stack);
    }

    /** How long this weapon's motion takes: quick for short blades, slow for hammers and long weapons. */
    static float ticksOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof GearItem gear)) {
            return MOTION_TICKS;
        }
        GearModelRenderer.Model model = GearModelRenderer.modelFor(stack);
        return GearMotions.ticks(gear.kind(), model == null ? 1.5 : model.length());
    }

    /**
     * Where this entity is in its motion (0-1), or -1 when not in one. A motion starts when a
     * vanilla swing starts and then runs on its own clock.
     */
    public static float motionProgress(LivingEntity entity, float tickDelta) {
        if (forceProgress >= 0) {
            return forceProgress;
        }
        float now = entity.age + tickDelta;
        float p = entity.getHandSwingProgress(tickDelta);
        float[] s = SWINGS.computeIfAbsent(entity, k -> new float[] { -1000, 0 });
        if (p > 0 && (s[1] <= 0 || p < s[1] - 0.05F)) {
            s[0] = now; // a new swing
        }
        s[1] = p;
        float t = (now - s[0]) / ticksOf(entity.getMainHandStack());
        return t >= 0 && t < 1 ? t : -1;
    }

    // ---------------------------------------------------------------- keyframes

    /** How a stretch between two keys is timed. */
    private static final int SMOOTH = 0;  // starts and ends slowly
    private static final int IN = 1;      // speeds up: into a strike
    private static final int OUT = 2;     // starts fast and slows: the follow-through, a wind-up settling
    private static final int SWING = 3;   // the swing itself: picks up speed, sweeps, and is still fast at its end

    /** One key: when (0-1), the pose there, and how the stretch leading to it is timed. */
    private record Key(float t, float[] pose, int ease) {
    }

    private static Key key(float t, int ease, float... pose) {
        return new Key(t, pose, ease);
    }

    private static float ease(int kind, float x) {
        return switch (kind) {
            case IN -> x * x * x;
            case OUT -> 1 - (1 - x) * (1 - x) * (1 - x);
            case SWING -> x * x;
            default -> x * x * (3 - 2 * x);
        };
    }

    /**
     * The pose at time t: between the keys around it, timed by the later key's ease. A motion
     * starts from rest (all zero); the last key is where it ends (rest, or a full turn for spins).
     */
    private static float[] at(Key[] keys, float t) {
        int n = keys[0].pose().length;
        float[] from = new float[n];
        float t0 = 0;
        for (Key k : keys) {
            if (t <= k.t()) {
                float a = ease(k.ease(), k.t() <= t0 ? 1 : (t - t0) / (k.t() - t0));
                float[] out = new float[n];
                for (int i = 0; i < n; i++) {
                    out[i] = from[i] + (k.pose()[i] - from[i]) * a;
                }
                return out;
            }
            from = k.pose();
            t0 = k.t();
        }
        return from.clone();
    }

    // First person poses: {x, y, z, yaw, pitch, roll} (blocks, degrees), mirrored for the left hand. Turns are
    // made in a frame turned 45 degrees like the vanilla swing (along the blade's diagonal); small moves keep
    // the weapon in view (moving it toward the camera hides it). Every motion: wind up (to 0.22), a long,
    // visible swing (to 0.45), a little past it (0.55), held there a while (to 0.8), then back to rest.
    private static final float W = 0.22F, S = 0.45F, F = 0.55F, H = 0.8F;

    /** wind, strike, follow-through (held), then back to rest (or to {@code end}). */
    private static Key[] motion(float[] wind, float[] strike, float[] follow, float[] end) {
        return new Key[] { new Key(W, wind, OUT), new Key(S, strike, SWING), new Key(F, follow, OUT), new Key(H, follow, SMOOTH),
            new Key(1.0F, end == null ? new float[wind.length] : end, SMOOTH) };
    }

    private static float[] p(float... v) {
        return v;
    }

    // Poses also turn the whole item round the view's axes at the hand: {.., view roll (in the picture, + turns the
    // tip toward the left), view pitch (+ tips it back toward the eye, - forward and down)}.
    // Slash: the blade turned level (tip to the left) and swept from the right of the view across to the left.
    private static final Key[] FP_SLASH = motion(p(0.34F, 0.16F, 0.05F, 0, 0, 0, 82, -10), p(-0.22F, 0.15F, -0.08F, 0, 0, 0, 86, -18),
        p(-0.42F, 0.13F, -0.02F, 0, 0, 0, 90, -22), null);
    // Smash: the arm alone brings the weapon down from overhead (tipped back) to the front (tipped forward and down).
    private static final Key[] FP_SMASH = motion(p(0.02F, 0.26F, 0.06F, 0, 0, 0, 0, 18), p(-0.02F, -0.1F, -0.15F, 0, 0, 0, 0, -70),
        p(-0.03F, -0.18F, -0.1F, 0, 0, 0, 0, -95), null);
    // Thrust: the arm drops back and low (tip back), then drives forward and up (tip ahead).
    private static final Key[] FP_THRUST = motion(p(0.1F, -0.3F, 0.15F, 0, 0, 0, -10, 15), p(-0.04F, 0.06F, -0.5F, 0, 0, 0, 30, -80),
        p(-0.05F, 0.1F, -0.58F, 0, 0, 0, 30, -85), null);
    private static final Key[] FP_SPIN = motion(p(0.2F, 0.05F, 0.0F, 50, -35, -55, 0, 0), p(-0.1F, 0.04F, -0.12F, -240, -35, -55, 0, 0),
        p(-0.12F, 0.0F, -0.08F, -330, -30, -50, 0, 0), p(0, 0, 0, -360, 0, 0, 0, 0));

    /** First person: in place of the normal swing turn, around the hand. */
    public static void firstPerson(MatrixStack matrices, Arm arm, String motion, float t) {
        if (t < 0) {
            return;
        }
        int side = arm == Arm.RIGHT ? 1 : -1;
        Key[] keys = switch (motion) {
            case "thrust" -> FP_THRUST;
            case "slash" -> FP_SLASH;
            case "smash" -> FP_SMASH;
            case "spin" -> FP_SPIN;
            default -> null;
        };
        if (keys == null) {
            return;
        }
        float[] p = at(keys, t);
        matrices.translate(side * p[0], p[1], p[2]);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[7]));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * p[6]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * p[5]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[4]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (p[3] - 45.0F)));
    }

    // Third person poses: {arm pitch, arm yaw, arm roll, body yaw, body pitch, other arm pitch, other arm yaw,
    // other arm roll} (radians).
    // Slash: the arm held out ahead and turned so the blade lies flat, swept from the right side across to the left.
    private static final Key[] TP_SLASH = motion(p(-1.45F, 1.15F, 1.35F, 0.5F, 0, -0.4F, 0.3F, -0.2F),
        p(-1.45F, -0.9F, 1.35F, -0.45F, 0.08F, 0.3F, 0.15F, 0.1F), p(-1.3F, -1.4F, 1.25F, -0.7F, 0.1F, 0.4F, 0.1F, 0.2F), null);
    // Smash: only the weapon arm, from straight up down to the front.
    private static final Key[] TP_SMASH = motion(p(-2.95F, 0.1F, 0, 0, 0, 0, 0, 0), p(-0.65F, 0, 0, 0, 0, 0, 0, 0),
        p(-0.35F, 0, 0, 0, 0, 0, 0, 0), null);
    // Thrust: the opposite of the smash: the arm swings back and low, then forward and up; the body stays.
    private static final Key[] TP_THRUST = motion(p(0.75F, 0.1F, 0, 0, 0, 0, 0, 0), p(-1.9F, 0, 0, 0, 0, 0, 0, 0),
        p(-2.15F, 0, 0, 0, 0, 0, 0, 0), null);
    private static final Key[] TP_SPIN = motion(p(-1.5F, 0.6F, 0.3F, 0.9F, 0, -0.3F, 0, -0.3F),
        p(-1.5F, -0.4F, 0.3F, -4.6F, 0.05F, -0.3F, 0, -0.3F), p(-1.2F, -0.6F, 0.2F, -5.9F, 0.05F, -0.2F, 0, -0.2F),
        p(0, 0, 0, -MathHelper.TAU, 0, 0, 0, 0));

    /**
     * Third person: arms and body for the motion, starting from the pose they are in (walking,
     * holding). Arms are moved with the body's turn like the vanilla swing does.
     */
    public static void thirdPerson(ModelPart arm, ModelPart other, ModelPart body, boolean right, String motion, float t) {
        Key[] keys = switch (motion) {
            case "thrust" -> TP_THRUST;
            case "slash" -> TP_SLASH;
            case "smash" -> TP_SMASH;
            case "spin" -> TP_SPIN;
            default -> null;
        };
        if (keys == null || t < 0) {
            return;
        }
        float[] p = at(keys, t);
        float side = right ? 1 : -1;
        float bodyYaw = side * p[3];
        body.yaw = bodyYaw;
        body.pitch = p[4];
        // Shoulders follow the body's turn (as in the vanilla swing).
        ModelPart rightArm = right ? arm : other;
        ModelPart leftArm = right ? other : arm;
        rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
        rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
        leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
        leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
        arm.pitch = p[0];
        arm.yaw = side * p[1] + bodyYaw;
        arm.roll = side * p[2];
        other.pitch = p[5];
        other.yaw = side * p[6] + bodyYaw;
        other.roll = side * p[7];
    }

    /** Third person: both hands on the grip of a long weapon, held ready in front of the body. */
    public static void twoHandedHold(ModelPart holding, ModelPart other, boolean right) {
        float side = right ? 1 : -1;
        holding.pitch = -0.55F;
        holding.yaw = side * -0.15F;
        holding.roll = 0;
        other.pitch = -0.75F;
        other.yaw = side * 0.6F;
        other.roll = 0;
    }
}
