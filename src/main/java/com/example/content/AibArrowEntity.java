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
    /** Which of the bow's arrow shapes, or which letter of its text, this one is. */
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(AibArrowEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** An extra projectile's way of flying (Motion as JSON); empty for the main shot. */
    private static final TrackedData<String> EXTRA = DataTracker.registerData(AibArrowEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    /** Shots taken in turn ("cycle"): the last one per shooter. */
    private static final java.util.Map<java.util.UUID, Integer> TURNS = new java.util.HashMap<>();
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
        builder.add(VARIANT, 0);
        builder.add(EXTRA, "");
    }

    /** True once it has hit a block or an entity (its landing effect has played). */
    public boolean hasLanded() {
        return landed;
    }

    /** The bow this arrow was shot from (empty if unknown). */
    public ItemStack bow() {
        return this.dataTracker.get(BOW);
    }

    /** Which arrow shape or letter it is (0 = the first). */
    public int variant() {
        return this.dataTracker.get(VARIANT);
    }

    /** How many shapes or letters the bow shoots from. */
    public static int choices(ItemStack bow) {
        Spark.Shot s = bow.isEmpty() ? null : GearEffects.of(bow).shot;
        if (s != null && s.letters().length > 0) {
            return s.letters().length;
        }
        com.example.ai.build.BuildPlan model = com.example.ai.build.GearItems.modelOf(bow);
        return model == null ? 1 : Math.max(1, model.arrowShapes());
    }

    /** Pick this shot's shape or letter: at random, or the next one in turn for this shooter. */
    public void pickVariant(LivingEntity shooter) {
        int n = choices(bow());
        if (n <= 1) {
            return;
        }
        Spark.Shot s = shot();
        int v;
        if (s != null && "cycle".equals(s.order) && shooter != null) {
            synchronized (TURNS) {
                v = (TURNS.getOrDefault(shooter.getUuid(), -1) + 1) % n;
                TURNS.put(shooter.getUuid(), v);
            }
        } else {
            v = this.random.nextInt(n);
        }
        this.dataTracker.set(VARIANT, v);
    }

    /**
     * How an extra projectile flies (both the server and every game steer it the same way, so it
     * needs no extra messages): spiral round the line of the main shot, arc or home into a point
     * or an entity; other paths fly like an arrow.
     */
    public static final class Motion {
        public String path = "";
        /** spiral: the main shot's velocity (the line it turns round); whether it falls. */
        public double bx, by, bz;
        public boolean gravity = true;
        public double radius;
        /** spiral: radians per tick, and where it starts round the line. */
        public double rate;
        public double phase;
        /** arc / homing: where it goes, or what it follows (-1 = none). */
        public double tx, ty, tz;
        public int target = -1;
        /** Drawn size (0 = as the main shot). */
        public double size;
    }

    private String motionJson;
    private Motion motion;
    /** Spiral: the line's velocity now; ticks steered. */
    private Vec3d base;
    private int steered;
    private boolean arrived;
    /** Server: the one a melee weapon hit directly (its extras pass through it). */
    private java.util.UUID passThrough;

    /** Make this an extra projectile of a shot (or a swing). */
    public void makeExtra(Motion m, Entity skip, int variant) {
        this.dataTracker.set(EXTRA, GSON.toJson(m));
        this.dataTracker.set(VARIANT, Math.max(0, variant));
        this.passThrough = skip == null ? null : skip.getUuid();
        this.pickupType = PickupPermission.CREATIVE_ONLY;
        calculateDimensions();
    }

    /** An extra projectile: it hurts half as much and plays no landing effects of its own. */
    public boolean isExtra() {
        return motion() != null;
    }

    public Motion motion() {
        if (this.dataTracker == null) {
            return null;
        }
        String json = this.dataTracker.get(EXTRA);
        if (!json.equals(motionJson)) {
            motionJson = json;
            try {
                motion = json.isEmpty() ? null : GSON.fromJson(json, Motion.class);
            } catch (RuntimeException exception) {
                motion = null;
            }
        }
        return motion;
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && (passThrough == null || !passThrough.equals(entity.getUuid()));
    }

    /** How it flies (from the bow; null = like a normal arrow). An extra may be drawn at its own size. */
    public Spark.Shot shot() {
        if (this.dataTracker == null) {
            return null; // still being made
        }
        ItemStack b = bow();
        Motion m = motion();
        if (b != shotOf) {
            shotOf = b;
            shot = b.isEmpty() ? null : GearEffects.of(b).shot;
            sizedShot = null;
        }
        if (shot != null && m != null && m.size > 0) {
            if (sizedShot == null) {
                sizedShot = Spark.Effects.fromJson("{\"shot\":" + GSON.toJson(shot) + "}").shot;
                sizedShot.size = m.size;
                sizedShot.clamped();
            }
            return sizedShot;
        }
        return shot;
    }

    private Spark.Shot sizedShot;

    /** Steer an extra along its path (before it moves this tick). */
    private void steer(Motion m) {
        switch (m.path) {
            case "spiral" -> {
                if (base == null) {
                    base = new Vec3d(m.bx, m.by, m.bz);
                }
                Vec3d d = base.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : base.normalize();
                Vec3d u = d.crossProduct(new Vec3d(0, 1, 0));
                u = u.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : u.normalize();
                Vec3d v = d.crossProduct(u).normalize();
                double a0 = m.phase + steered * m.rate, a1 = a0 + m.rate;
                Vec3d off0 = u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))).multiply(m.radius);
                Vec3d off1 = u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1))).multiply(m.radius);
                setVelocity(base.add(off1.subtract(off0)));
                base = base.multiply(0.99).add(0, m.gravity ? -0.05 : 0, 0);
            }
            case "arc", "homing" -> {
                Vec3d goal = new Vec3d(m.tx, m.ty, m.tz);
                if (m.target >= 0) {
                    Entity t = getWorld().getEntityById(m.target);
                    if (t != null && t.isAlive()) {
                        goal = t.getPos().add(0, t.getHeight() / 2, 0);
                    }
                }
                Vec3d to = goal.subtract(getPos());
                double speed = Math.max(0.6, getVelocity().length());
                double dist = to.length();
                if (arrived || dist < 1e-3) {
                    return;
                }
                if ("arc".equals(m.path) && dist < Math.max(1.5, speed)) {
                    arrived = true; // at the spot: fly on straight (past it if the target moved)
                }
                // Curving in: gently from afar, firmly when close, so it reaches the spot.
                double k = "homing".equals(m.path) ? Math.max(0.25, speed * 1.2 / dist)
                    : Math.max(0.08 + steered * 0.04, speed * 1.2 / dist);
                k = Math.min(1, k);
                setVelocity(getVelocity().multiply(1 - k).add(to.multiply(speed * k / dist)));
            }
            default -> {
            }
        }
        steered++;
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
        Motion m = motion();
        if (m != null && (m.path.equals("spiral") || m.path.equals("arc") || m.path.equals("homing"))) {
            return 0; // steered: its path has its own fall
        }
        Spark.Shot s = shot();
        return s != null && !s.gravity ? 0 : super.getGravity();
    }

    @Override
    public void tick() {
        Motion m = motion();
        if (m != null && isFlying()) {
            steer(m);
        }
        super.tick();
        Spark.Shot s = shot();
        // Without gravity it would fly on for ever (and extras never stay long).
        if (!getWorld().isClient && isFlying() && this.age > 200 && (s != null && !s.gravity || m != null)) {
            discard();
        }
        if (!getWorld().isClient && m != null && !isFlying() && this.age > 100) {
            discard(); // extras stuck somewhere do not stay
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
        hitDirect = hit.getEntity();
        if (isExtra() && !getWorld().isClient) {
            // Extras land together with the main shot: each one counts (no short invulnerability after a hit).
            hit.getEntity().timeUntilRegen = 0;
        }
        super.onEntityHit(hit);
        land(at);
    }

    /** What the arrow hit directly (its landing effects do not hurt it again). */
    private Entity hitDirect;

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
        Spark.Shot s = shot();
        if (isExtra()) {
            // Extras never stay: they cannot be picked up, and a stuck one would only hang there.
            if (s != null && s.vanishes() && !s.beam()) {
                GearEffects.vanish(world, this, getPos(), s);
            }
            discard();
            return;
        }
        GearEffects.arrowImpact(world, this, at, hitDirect);
        // Extras that burst out where it lands.
        com.example.ai.build.ShotExtras.split(world, this, at.add(0, 0.3, 0));
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
        nbt.putInt("aib_variant", variant());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("aib_bow")) {
            ItemStack.fromNbt(getWorld().getRegistryManager(), nbt.get("aib_bow")).ifPresent(s -> this.dataTracker.set(BOW, s));
        }
        landed = nbt.getBoolean("aib_landed");
        this.dataTracker.set(VARIANT, nbt.getInt("aib_variant"));
    }
}
