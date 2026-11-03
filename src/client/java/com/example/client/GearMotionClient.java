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
    public static final float MOTION_TICKS = 12.0F;
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
    // the weapon in view (moving it toward the camera hides it). Each motion: wind up, a fast strike that
    // crosses the view, a follow-through past it, settling, and back to rest.
    private static final Key[] FP_SLASH = {
        key(0.28F, OUT, 0.24F, 0.12F, 0.04F, 75, -30, -55),
        key(0.44F, IN, -0.16F, 0.0F, -0.14F, -55, -40, -55),
        key(0.56F, OUT, -0.34F, -0.08F, -0.08F, -105, -46, -62),
        key(0.76F, SMOOTH, -0.12F, -0.04F, 0, -35, -16, -22),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0) };
    private static final Key[] FP_SMASH = {
        key(0.30F, OUT, 0.03F, 0.22F, 0.06F, 0, 45, 0),
        key(0.44F, IN, -0.03F, -0.08F, -0.2F, 0, -80, 0),
        key(0.52F, OUT, -0.04F, -0.2F, -0.14F, 0, -112, 0),
        key(0.76F, SMOOTH, 0, -0.08F, -0.04F, 0, -40, 0),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0) };
    private static final Key[] FP_THRUST = {
        key(0.30F, OUT, 0.16F, -0.1F, 0.14F, 20, -65, -80),
        key(0.42F, IN, -0.06F, 0.02F, -0.55F, 0, -75, -80),
        key(0.52F, OUT, -0.07F, 0.02F, -0.62F, -2, -76, -80),
        key(0.74F, SMOOTH, 0, 0, -0.2F, 5, -38, -40),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0) };
    private static final Key[] FP_SPIN = {
        key(0.24F, OUT, 0.2F, 0.05F, 0.0F, 50, -35, -55),
        key(0.50F, IN, -0.1F, 0.04F, -0.12F, -240, -35, -55),
        key(0.64F, OUT, -0.12F, 0.0F, -0.08F, -330, -30, -50),
        key(1.0F, SMOOTH, 0, 0, 0, -360, 0, 0) };

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
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * p[5]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[4]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (p[3] - 45.0F)));
    }

    // Third person poses: {arm pitch, arm yaw, arm roll, body yaw, body pitch, other arm pitch, other arm yaw,
    // other arm roll} (radians). The body turns and leans into the strike; the free arm balances it.
    private static final Key[] TP_SLASH = {
        key(0.28F, OUT, -1.55F, 1.25F, 0.35F, 0.7F, -0.06F, -0.55F, 0.35F, -0.2F),
        key(0.44F, IN, -1.45F, -0.85F, -0.2F, -0.5F, 0.14F, 0.3F, 0.15F, 0.1F),
        key(0.56F, OUT, -1.05F, -1.45F, -0.45F, -0.85F, 0.18F, 0.5F, 0.1F, 0.25F),
        key(0.76F, SMOOTH, -0.6F, -0.75F, -0.15F, -0.35F, 0.07F, 0.15F, 0, 0.05F),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0, 0, 0) };
    private static final Key[] TP_SMASH = {
        key(0.30F, OUT, -3.1F, 0.1F, 0, 0.15F, -0.3F, -2.9F, 0.1F, 0),
        key(0.44F, IN, -0.95F, 0, 0, 0, 0.42F, -0.85F, 0, 0),
        key(0.52F, OUT, -0.45F, 0, 0, 0, 0.6F, -0.42F, 0, 0),
        key(0.76F, SMOOTH, -0.4F, 0, 0, 0, 0.28F, -0.3F, 0, 0),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0, 0, 0) };
    private static final Key[] TP_THRUST = {
        key(0.30F, OUT, -0.2F, 0.5F, 0, 0.75F, -0.06F, -1.0F, -0.6F, 0),
        key(0.42F, IN, -1.55F, -0.05F, 0, -0.5F, 0.25F, 0.5F, 0.2F, 0.1F),
        key(0.52F, OUT, -1.6F, -0.1F, 0, -0.62F, 0.3F, 0.6F, 0.2F, 0.15F),
        key(0.74F, SMOOTH, -0.9F, 0, 0, -0.2F, 0.1F, 0.2F, 0, 0),
        key(1.0F, SMOOTH, 0, 0, 0, 0, 0, 0, 0, 0) };
    private static final Key[] TP_SPIN = {
        key(0.24F, OUT, -1.5F, 0.6F, 0.3F, 0.9F, 0, -0.3F, 0, -0.3F),
        key(0.50F, IN, -1.5F, -0.4F, 0.3F, -4.6F, 0.05F, -0.3F, 0, -0.3F),
        key(0.64F, OUT, -1.2F, -0.6F, 0.2F, -5.9F, 0.05F, -0.2F, 0, -0.2F),
        key(1.0F, SMOOTH, 0, 0, 0, -MathHelper.TAU, 0, 0, 0, 0) };

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
