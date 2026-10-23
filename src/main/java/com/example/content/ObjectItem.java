package com.example.content;

import com.example.ai.build.ObjectItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;

/**
 * An AI-made object as an item. Right-click the top of a block to set it there (facing you);
 * mine it like stone to get the item back. Which object it is, and its icon picture, are in
 * the stack's custom data (see ObjectItems).
 */
public class ObjectItem extends Item {

    public ObjectItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getSide() != Direction.UP) {
            if (!context.getWorld().isClient() && context.getPlayer() != null) {
                context.getPlayer().sendMessage(Text.literal("블록 윗면을 클릭하면 그 위에 놓여요").formatted(Formatting.GRAY), true);
            }
            return ActionResult.FAIL;
        }
        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return ActionResult.PASS;
        }
        return ObjectItems.use(player, context.getStack(), context.getBlockPos()) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }
}
