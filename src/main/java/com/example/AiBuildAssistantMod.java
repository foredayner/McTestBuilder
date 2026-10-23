package com.example;

import com.example.ai.build.BuildTaskQueue;
import com.example.command.AiApprovalCommands;
import com.example.command.AiBuildCommand;
import com.example.config.AiBuilderConfig;
import com.example.event.ChatMessageHandler;
import com.example.network.AiNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiBuildAssistantMod implements ModInitializer {

    public static final String MOD_ID = "minecraft-ai-build-assistant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Minecraft AI Build Assistant starting...");

        AiBuilderConfig.load();
        com.example.content.AibContent.register();
        BuildTaskQueue.register();

        AiNetworking.registerPayloads();
        AiNetworking.registerServerReceivers();

        AiBuildCommand.register();
        AiApprovalCommands.register();
        ChatMessageHandler.registerChatFilter();

        // Blueprint papers (buildings): right-click the top of a block to build there.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (com.example.ai.build.BlueprintStore.idOf(stack) == null) {
                return net.minecraft.util.ActionResult.PASS;
            }
            if (world.isClient()) {
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            com.example.ai.build.BlueprintStore.use((net.minecraft.server.network.ServerPlayerEntity) player, stack,
                hit.getBlockPos(), hit.getSide());
            return net.minecraft.util.ActionResult.SUCCESS;
        });
    }
}
