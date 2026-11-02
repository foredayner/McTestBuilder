package com.example.client;

import com.example.config.AiBuilderConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * A short shake of this player's view for big hits. The view is nudged a little each tick and put
 * back, so where the player looks does not drift. Players can turn it off.
 */
public final class ScreenShake {

    private static final Random RANDOM = new Random();
    private static float strength;
    private static int ticks;
    private static int length;
    private static float yawOffset;
    private static float pitchOffset;

    private ScreenShake() {
    }

    public static void start(double amount) {
        if (!AiBuilderConfig.get().screenShake) {
            return;
        }
        float s = (float) Math.max(0, Math.min(1, amount));
        if (s * 1.0F >= strength * ticks / Math.max(1, length)) {
            strength = s;
            length = 6 + Math.round(s * 8);
            ticks = length;
        }
    }

    public static void tick(MinecraftClient client) {
        var p = client.player;
        if (p == null) {
            ticks = 0;
            yawOffset = pitchOffset = 0;
            return;
        }
        // Take back last tick's nudge, then (while it lasts) give a new, smaller one.
        p.setYaw(p.getYaw() - yawOffset);
        p.setPitch(p.getPitch() - pitchOffset);
        yawOffset = pitchOffset = 0;
        if (ticks > 0) {
            float amp = strength * 1.6F * ticks / length;
            yawOffset = (RANDOM.nextFloat() * 2 - 1) * amp;
            pitchOffset = (RANDOM.nextFloat() * 2 - 1) * amp * 0.7F;
            p.setYaw(p.getYaw() + yawOffset);
            p.setPitch(p.getPitch() + pitchOffset);
            ticks--;
        }
    }
}
