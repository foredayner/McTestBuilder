package com.example.ai;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/** Loads Minecraft's registries once so block names and states can be checked in plain JUnit. */
public final class Boot {
    private static boolean done;

    private Boot() {
    }

    public static synchronized void minecraft() {
        if (!done) {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            done = true;
        }
    }
}
