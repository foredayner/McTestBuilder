package com.example.content;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.world.World;

/**
 * An invisible box around (part of) an AI object, like the vanilla interaction entity but
 * solid: players and mobs bump into it and can stand on it (as on a boat or a shulker). Its
 * size is the interaction's width (square footprint) and height.
 *
 * <p>Collision is worked out on each player's own computer, so it feels solid to players who
 * have this mod installed.
 */
public class SolidHitboxEntity extends InteractionEntity {

    public SolidHitboxEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public boolean isCollidable() {
        return true;
    }
}
