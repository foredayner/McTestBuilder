package com.example.content;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

/**
 * An AI-modelled bow: works like a vanilla bow (drawing, power, enchantments). Plain arrows it
 * shoots take the arrow shape and the trail / landing effects ordered with the bow.
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
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        com.example.ai.build.GearEffects.onTick(stack, entity, selected, GearKind.BOW);
    }

    @Override
    public ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack,
                                              boolean critical) {
        if (!projectileStack.isOf(Items.ARROW)) {
            return super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical); // tipped, spectral
        }
        AibArrowEntity arrow = new AibArrowEntity(world, shooter, projectileStack.copyWithCount(1), weaponStack);
        if (critical) {
            arrow.setCritical(true);
        }
        return arrow;
    }
}
