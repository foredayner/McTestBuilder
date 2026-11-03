package com.example.content;

import com.example.ai.build.GearEffects;
import com.example.ai.build.Spark;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
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

import java.util.Optional;

/**
 * An arrow shot from an AI bow. It carries the bow (synced to clients, which draw the bow's
 * arrow shape and trail) and plays the bow's attack effects where it lands.
 * Otherwise a normal arrow: damage, pickup and enchantments are vanilla.
 */
public class AibArrowEntity extends PersistentProjectileEntity {

    private static final TrackedData<ItemStack> BOW = DataTracker.registerData(AibArrowEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private boolean landed;
    /** The bow's shot settings, read once per bow stack. */
    private ItemStack shotOf;
    private Spark.Shot shot;
    /** Client only: how far a tumbling shot has turned (kept when it sticks). */
    public float spin;

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

    /** How it flies (from the bow; null = like a normal arrow). */
    public Spark.Shot shot() {
        if (this.dataTracker == null) {
            return null; // still being made
        }
        ItemStack b = bow();
        if (b != shotOf) {
            shotOf = b;
            shot = b.isEmpty() ? null : GearEffects.of(b).shot;
        }
        return shot;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (BOW.equals(data)) {
            calculateDimensions();
        }
    }

    /** A bigger shot has a bigger box (about half its drawn size). */
    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        Spark.Shot s = shot();
        if (s == null || s.size <= 0) {
            return super.getDimensions(pose);
        }
        float h = (float) s.hitbox();
        return EntityDimensions.fixed(h, h);
    }

    @Override
    protected double getGravity() {
        Spark.Shot s = shot();
        return s != null && !s.gravity ? 0 : super.getGravity();
    }

    @Override
    public void tick() {
        super.tick();
        Spark.Shot s = shot();
        // Without gravity it would fly on for ever.
        if (!getWorld().isClient && s != null && !s.gravity && isFlying() && this.age > 200) {
            discard();
        }
    }

    /** A bigger shot hits what its box touches, not only what its middle passes through. */
    @Override
    protected EntityHitResult getEntityCollision(Vec3d from, Vec3d to) {
        Spark.Shot s = shot();
        if (s == null || s.size <= 0) {
            return super.getEntityCollision(from, to);
        }
        double margin = Math.max(0.3, s.hitbox() / 2);
        EntityHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().stretch(getVelocity()).expand(1 + margin), this::canHit)) {
            Optional<Vec3d> at = e.getBoundingBox().expand(margin).raycast(from, to);
            if (at.isPresent() && from.squaredDistanceTo(at.get()) < bestDistance) {
                bestDistance = from.squaredDistanceTo(at.get());
                best = new EntityHitResult(e, at.get());
            }
        }
        return best;
    }

    /** A beam shot reached an entity: it is hit like by this arrow (vanilla damage, enchantments, knockback). */
    public void beamHit(EntityHitResult hit) {
        onEntityHit(hit);
    }

    /** A beam shot reached a block: only the landing effects. */
    public void beamHitBlock(BlockHitResult hit) {
        land(hit.getPos());
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
        Spark.Shot s = shot();
        if (s != null && s.vanishes() && !s.beam()) {
            GearEffects.vanish(world, this, getPos(), s);
            discard();
        }
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
