package com.example.content;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * An invisible box around (part of) an AI object, like the vanilla interaction entity but
 * solid: players and mobs bump into it and can stand on it (as on a boat or a shulker). Its
 * size is the interaction's width (square footprint) and height.
 *
 * <p>Collision is worked out on each player's own computer, so it feels solid to players who
 * have this mod installed. A box summoned with {@code aib_pass:1b} (a curtain) can be walked
 * through but is still hit and mined like the others; that flag is synced to the players.
 */
public class SolidHitboxEntity extends InteractionEntity {

    private static final TrackedData<Boolean> PASS = DataTracker.registerData(SolidHitboxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final String PASS_KEY = "aib_pass";

    public SolidHitboxEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(PASS, false);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(PASS, nbt.getBoolean(PASS_KEY));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (passable()) {
            nbt.putBoolean(PASS_KEY, true);
        }
    }

    /** Whether players walk through it. */
    public boolean passable() {
        return this.dataTracker.get(PASS);
    }

    @Override
    public boolean isCollidable() {
        return !passable();
    }
}
