package com.example.ai.build;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * Older versions drew the magic circle hit effect with glowing display pieces tagged
 * {@value #TAG}. Effects are now colour dots drawn by each player's game; any such pieces
 * left in a world are removed when they load.
 */
public final class HitEffects {

    public static final String TAG = "aib_fx";

    private HitEffects() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(TAG)) {
                entity.discard();
            }
        });
    }
}
