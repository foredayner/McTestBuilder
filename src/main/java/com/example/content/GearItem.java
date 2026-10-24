package com.example.content;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * An AI-modelled weapon or tool. Its stats (damage, speed, mining, durability, enchantability)
 * are copied from an iron vanilla item when it is registered, and right-click and hit
 * behaviour is passed on to that item (an axe strips logs, a shovel makes paths, a hoe tills).
 * The look comes from the model stored on each stack (drawn by the client).
 */
public class GearItem extends Item {

    private final GearKind kind;

    public GearItem(GearKind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    public GearKind kind() {
        return kind;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return kind.base.useOnBlock(context);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return kind.base.postHit(stack, target, attacker);
    }

    @Override
    public void postDamageEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        kind.base.postDamageEntity(stack, target, attacker);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return kind.base.canMine(state, world, pos, miner);
    }
}
