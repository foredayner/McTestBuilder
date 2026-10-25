package com.example.content;

import com.example.ai.build.GearEffects;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * An arrow shot from an AI bow. It carries the bow (synced to clients, which draw the bow's
 * arrow shape and trail) and plays the bow's attack effects where it lands.
 * Otherwise a normal arrow: damage, pickup and enchantments are vanilla.
 */
public class AibArrowEntity extends PersistentProjectileEntity {

    private static final TrackedData<ItemStack> BOW = DataTracker.registerData(AibArrowEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private boolean landed;

    public AibArrowEntity(EntityType<? extends AibArrowEntity> type, World world) {
        super(type, world);
    }

    public AibArrowEntity(World world, LivingEntity owner, ItemStack stack, ItemStack bow) {
        super(AibContent.ARROW, owner, world, stack, bow);
        this.dataTracker.set(BOW, bow.copyWithCount(1));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BOW, ItemStack.EMPTY);
    }

    /** True once it has hit a block or an entity (its landing effect has played). */
    public boolean hasLanded() {
        return landed;
    }

    /** The bow this arrow was shot from (empty if unknown). */
    public ItemStack bow() {
        return this.dataTracker.get(BOW);
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Items.ARROW);
    }

    /** In flight (not stuck in a block): the client draws the bow's trail behind it. */
    public boolean isFlying() {
        return !isInGround() && !landed;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Vec3d at = hit.getEntity().getPos(); // effects are placed from the feet of what was hit
        super.onEntityHit(hit);
        land(at);
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        land(hit.getPos());
    }

    private void land(Vec3d at) {
        if (landed || !(getWorld() instanceof ServerWorld world)) {
            return;
        }
        landed = true;
        GearEffects.arrowImpact(world, this, at);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (!bow().isEmpty()) {
            nbt.put("aib_bow", bow().toNbt(getWorld().getRegistryManager()));
        }
        nbt.putBoolean("aib_landed", landed);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("aib_bow")) {
            ItemStack.fromNbt(getWorld().getRegistryManager(), nbt.get("aib_bow")).ifPresent(s -> this.dataTracker.set(BOW, s));
        }
        landed = nbt.getBoolean("aib_landed");
    }
}
