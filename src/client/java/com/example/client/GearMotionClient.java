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
 * Draws the attack motion ordered for an AI sword instead of the normal swing, in first person
 * (the held item) and third person (arms and body). A motion has its own clock (longer than the
 * vanilla 6-tick swing) and four phases: wind up, strike, hold, recover. Only the look changes;
 * damage, reach and attack speed stay vanilla.
 */
public final class GearMotionClient {

    /** How long a motion takes (ticks). */
    public static final float MOTION_TICKS = 12.0F;
    /** Tests: show the motion frozen at this point (0-1); negative = off. */
    public static float forceProgress = -1;

    private static final Map<LivingEntity, float[]> SWINGS = new WeakHashMap<>();

    private GearMotionClient() {
    }

    /** The ordered motion of a sword stack, or "". */
    public static String motionOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof GearItem gear) || gear.kind() != GearKind.SWORD) {
            return "";
        }
        return GearMotions.of(stack);
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
        float t = (now - s[0]) / MOTION_TICKS;
        return t >= 0 && t < 1 ? t : -1;
    }

    private static float easeOut(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float easeIn(float x) {
        return x * x * x;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    /**
     * Blend between the rest pose (all zero), the wind-up pose and the strike pose: wind up
     * (0-0.3, slowing at the end), strike (0.3-0.48, fast), hold (to 0.62), recover (to 1).
     * {@code end} is the pose it recovers to (rest, or a full turn for spins).
     */
    private static float[] blend(float t, float[] wind, float[] strike, float[] end) {
        float[] out = new float[wind.length];
        float[] from;
        float[] to;
        float a;
        if (t < 0.3F) {
            from = null;
            to = wind;
            a = easeOut(t / 0.3F);
        } else if (t < 0.48F) {
            from = wind;
            to = strike;
            a = easeIn((t - 0.3F) / 0.18F);
        } else if (t < 0.62F) {
            from = strike;
            to = strike;
            a = 1;
        } else {
            from = strike;
            to = end;
            a = smooth((t - 0.62F) / 0.38F);
        }
        for (int i = 0; i < out.length; i++) {
            float f = from == null ? 0 : from[i];
            float g = to == null ? 0 : to[i];
            out[i] = f + (g - f) * a;
        }
        return out;
    }

    // First person poses: {x, y, z, yaw, pitch, roll} (blocks, degrees), mirrored for the left hand. Turns are
    // made in a frame turned 45 degrees like the vanilla swing (along the blade's diagonal); small moves keep
    // the weapon in view (moving it toward the camera hides it).
    private static final float[] FP_THRUST_WIND = { 0.16F, -0.12F, 0.08F, 20, -65, -80 };
    private static final float[] FP_THRUST_STRIKE = { -0.06F, 0.02F, -0.5F, 0, -75, -80 };
    private static final float[] FP_SLASH_WIND = { 0.22F, 0.08F, 0.0F, 65, -35, -55 };
    private static final float[] FP_SLASH_STRIKE = { -0.28F, -0.02F, -0.12F, -85, -40, -55 };
    private static final float[] FP_SMASH_WIND = { 0.02F, 0.16F, 0.04F, 0, 35, 0 };
    private static final float[] FP_SMASH_STRIKE = { -0.04F, -0.16F, -0.18F, 0, -105, 0 };
    private static final float[] FP_SPIN_WIND = { 0.18F, 0.04F, 0.0F, 45, -35, -55 };
    private static final float[] FP_SPIN_STRIKE = { -0.1F, 0.04F, -0.12F, -320, -35, -55 };
    private static final float[] FP_SPIN_END = { 0, 0, 0, -360, 0, 0 };

    /** First person: in place of the normal swing turn, around the hand. */
    public static void firstPerson(MatrixStack matrices, Arm arm, String motion, float t) {
        if (t < 0) {
            return;
        }
        int side = arm == Arm.RIGHT ? 1 : -1;
        float[] p = switch (motion) {
            case "thrust" -> blend(t, FP_THRUST_WIND, FP_THRUST_STRIKE, null);
            case "slash" -> blend(t, FP_SLASH_WIND, FP_SLASH_STRIKE, null);
            case "smash" -> blend(t, FP_SMASH_WIND, FP_SMASH_STRIKE, null);
            case "spin" -> blend(t, FP_SPIN_WIND, FP_SPIN_STRIKE, FP_SPIN_END);
            default -> null;
        };
        if (p == null) {
            return;
        }
        matrices.translate(side * p[0], p[1], p[2]);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * p[5]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[4]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (p[3] - 45.0F)));
    }

    // Third person poses: {arm pitch, arm yaw, arm roll, body yaw, body pitch, other arm pitch, other arm yaw} (radians).
    private static final float[] TP_THRUST_WIND = { -0.25F, 0.35F, 0, 0.7F, 0, -0.9F, -0.5F };
    private static final float[] TP_THRUST_STRIKE = { -1.15F, -0.1F, 0, -0.45F, 0.3F, 0.45F, 0.1F };
    private static final float[] TP_SLASH_WIND = { -1.35F, 1.3F, 0, 0.75F, 0, -0.4F, -0.3F };
    private static final float[] TP_SLASH_STRIKE = { -1.4F, -1.25F, 0, -0.75F, 0.1F, 0.35F, 0.2F };
    private static final float[] TP_SMASH_WIND = { -3.0F, 0.15F, 0, 0.1F, -0.25F, -2.6F, 0.1F };
    private static final float[] TP_SMASH_STRIKE = { -0.55F, 0, 0, 0, 0.5F, -0.4F, 0 };
    private static final float[] TP_SPIN_WIND = { -1.5F, 0.4F, 0.3F, 0.7F, 0, -0.3F, 0 };
    private static final float[] TP_SPIN_STRIKE = { -1.5F, -0.3F, 0.3F, -5.6F, 0, -0.3F, 0 };
    private static final float[] TP_SPIN_END = { 0, 0, 0, -MathHelper.TAU, 0, 0, 0 };

    /**
     * Third person: arms and body for the motion, starting from the pose they are in (walking,
     * holding). Arms are moved with the body's turn like the vanilla swing does.
     */
    public static void thirdPerson(ModelPart arm, ModelPart other, ModelPart body, boolean right, String motion, float t) {
        float[] p = switch (motion) {
            case "thrust" -> blend(t, TP_THRUST_WIND, TP_THRUST_STRIKE, null);
            case "slash" -> blend(t, TP_SLASH_WIND, TP_SLASH_STRIKE, null);
            case "smash" -> blend(t, TP_SMASH_WIND, TP_SMASH_STRIKE, null);
            case "spin" -> blend(t, TP_SPIN_WIND, TP_SPIN_STRIKE, TP_SPIN_END);
            default -> null;
        };
        if (p == null) {
            return;
        }
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
