package com.example.content;

import com.example.ai.build.GearEffects;
import com.example.ai.build.Spark;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

/**
 * An AI-modelled bow: works like a vanilla bow (drawing, power, enchantments). Plain arrows it
 * shoots take the arrow shape and the trail / landing effects ordered with the bow. A bow whose
 * shot is a beam shoots no arrow: a ray reaches at once (up to 64 blocks), stops at the first mob
 * or block and hits like an arrow with the same draw.
 */
public class GearBowItem extends BowItem implements GearItem {

    public GearBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public GearKind kind() {
        return GearKind.BOW;
    }

    @Override
    public ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack,
                                              boolean critical) {
        if (!projectileStack.isOf(Items.ARROW)) {
            return super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical); // tipped, spectral
        }
        AibArrowEntity arrow = new AibArrowEntity(world, shooter, projectileStack.copyWithCount(1), weaponStack);
        arrow.pickVariant(shooter);
        if (critical) {
            arrow.setCritical(true);
        }
        if (world instanceof ServerWorld server) {
            com.example.ai.build.ShotExtras.afterShot(server, shooter, arrow);
        }
        return arrow;
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        Spark.Shot shot = GearEffects.of(stack).shot;
        if (shot == null || !shot.beam() || !(user instanceof PlayerEntity player)) {
            return super.onStoppedUsing(stack, world, user, remainingUseTicks);
        }
        ItemStack ammo = player.getProjectileType(stack);
        if (ammo.isEmpty()) {
            return false;
        }
        float pull = getPullProgress(this.getMaxUseTime(stack, user) - remainingUseTicks);
        if (pull < 0.1F) {
            return false;
        }
        List<ItemStack> loaded = load(stack, ammo, player);
        if (world instanceof ServerWorld server && !loaded.isEmpty()) {
            fireBeam(server, player, stack, loaded.get(0), pull, shot);
            stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F,
            1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
        player.incrementStat(Stats.USED.getOrCreateStat(this));
        return true;
    }

    /** Where a beam from the eyes along the look stops: the first block, or the first entity before it. */
    public static HitResult beamTarget(World world, LivingEntity shooter) {
        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(Spark.Shot.RANGE));
        BlockHitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, shooter));
        Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        Entity vehicle = shooter.getVehicle();
        EntityHitResult entity = ProjectileUtil.raycast(shooter, eye, stop, shooter.getBoundingBox().stretch(stop.subtract(eye)).expand(1.0),
            e -> !e.isSpectator() && e.canHit() && e != vehicle, eye.squaredDistanceTo(stop));
        return entity != null ? entity : block;
    }

    public static void fireBeam(ServerWorld world, PlayerEntity player, ItemStack bow, ItemStack ammo, float pull, Spark.Shot shot) {
        Vec3d look = player.getRotationVec(1.0F);
        HitResult hit = beamTarget(world, player);
        Vec3d at = hit.getPos();
        // An arrow that never flies: it carries the damage, the enchantments and the landing effects.
        AibArrowEntity arrow = new AibArrowEntity(world, player, ammo.copyWithCount(1), bow);
        arrow.setVelocity(look.multiply(pull * 3.0F));
        arrow.setCritical(pull >= 1.0F);
        arrow.setPosition(at.subtract(look.multiply(0.2)));
        Vec3d muzzle = player.getEyePos().add(0, -0.18, 0).add(look.multiply(0.6));
        GearEffects.beam(world, player, muzzle, at, shot);
        com.example.ai.build.ShotExtras.afterBeam(world, player, bow, muzzle, look.multiply(pull * 3.0F), shot, hit);
        if (hit instanceof EntityHitResult entityHit) {
            arrow.beamHit(entityHit);
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            arrow.beamHitBlock(blockHit);
        }
        arrow.discard();
    }
}
