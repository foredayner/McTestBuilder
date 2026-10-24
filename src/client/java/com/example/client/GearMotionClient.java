package com.example.client;

import com.example.content.GearItem;
import com.example.content.GearKind;
import com.example.content.GearMotions;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the attack motion ordered for an AI sword instead of the normal swing: in first person
 * (the held item) and in third person (the arm). Only the look changes.
 */
public final class GearMotionClient {

    /** Tests: show the motion frozen at this point of the swing (0-1); negative = off. */
    public static float forceProgress = -1;

    private GearMotionClient() {
    }

    /** The ordered motion of a sword stack, or "". */
    public static String motionOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof GearItem gear) || gear.kind() != GearKind.SWORD) {
            return "";
        }
        return GearMotions.of(stack);
    }

    public static float progress(float swingProgress) {
        return forceProgress >= 0 ? forceProgress : swingProgress;
    }

    private static float smooth(float p) {
        return p * p * (3 - 2 * p);
    }

    /** First person: in place of the normal swing turn, around the hand. */
    public static void firstPerson(MatrixStack matrices, Arm arm, String motion, float p) {
        int side = arm == Arm.RIGHT ? 1 : -1;
        float e = MathHelper.sin(p * MathHelper.PI); // 0 -> 1 -> 0 over the swing
        switch (motion) {
            case "slash" -> {
                // The blade lies on its side and sweeps from the right across to the left.
                matrices.translate(side * -0.25F * e, 0.1F * e, -0.15F * e);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (60.0F - 150.0F * smooth(p)) * e));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 75.0F * e));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F * e));
            }
            case "smash" -> {
                // Raised high, then brought straight down.
                float up = p < 0.35F ? smooth(p / 0.35F) : 1 - smooth((p - 0.35F) / 0.65F) * 2.2F;
                matrices.translate(0, 0.2F * Math.max(0, up), -0.1F * e);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(55.0F * up));
            }
            case "thrust" -> {
                // Turned to point ahead and pushed forward.
                matrices.translate(side * -0.15F * e, 0.05F * e, -0.55F * e);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75.0F * e));
            }
            case "spin" -> {
                // A full circle around the hand, blade out to the side.
                matrices.translate(side * -0.1F * e, 0.05F * e, -0.2F * e);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -360.0F * smooth(p)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 70.0F * e));
            }
            default -> {
            }
        }
    }

    /** Third person: arm (and body) angles for the swinging arm, in place of the normal swing. */
    public static void thirdPerson(ModelPart arm, ModelPart body, boolean right, String motion, float p) {
        float side = right ? 1 : -1;
        float e = MathHelper.sin(p * MathHelper.PI);
        float halfPi = MathHelper.HALF_PI;
        switch (motion) {
            case "slash" -> {
                body.yaw = side * (0.5F - 1.1F * smooth(p)) * e;
                arm.pitch = -halfPi * 0.95F * e;
                arm.yaw = side * (0.9F - 2.0F * smooth(p)) * e;
                arm.roll = 0;
            }
            case "smash" -> {
                float up = p < 0.35F ? smooth(p / 0.35F) : 1 - smooth((p - 0.35F) / 0.65F);
                arm.pitch = -2.9F * up - 0.5F * e * (1 - up);
                arm.yaw = 0;
                arm.roll = 0;
                body.pitch = 0.25F * e * (1 - up);
            }
            case "thrust" -> {
                body.yaw = side * 0.35F * e;
                arm.pitch = -halfPi * 1.05F * e;
                arm.yaw = side * -0.15F * e;
                arm.roll = 0;
            }
            case "spin" -> {
                arm.pitch = -halfPi * 0.9F * e;
                arm.yaw = side * MathHelper.TAU * -smooth(p) * 0.5F;
                arm.roll = side * 0.4F * e;
                body.yaw = side * -MathHelper.TAU * smooth(p) * 0.15F;
            }
            default -> {
            }
        }
    }
}
