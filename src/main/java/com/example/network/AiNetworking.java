package com.example.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.example.ai.approval.BuildApproval;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import com.example.config.ForbiddenBlockRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class AiNetworking {

    private static boolean payloadsRegistered = false;

    private AiNetworking() {
    }

    public static void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }
        payloadsRegistered = true;

        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.AiLogS2CPayload.ID, AiNetworkPayloads.AiLogS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.ApprovalPendingS2CPayload.ID, AiNetworkPayloads.ApprovalPendingS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.ApprovalClearS2CPayload.ID, AiNetworkPayloads.ApprovalClearS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.ApprovalAcceptC2SPayload.ID, AiNetworkPayloads.ApprovalAcceptC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.ApprovalDenyC2SPayload.ID, AiNetworkPayloads.ApprovalDenyC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.AiSettingsSyncC2SPayload.ID, AiNetworkPayloads.AiSettingsSyncC2SPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalAcceptC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> BuildApproval.approveFor(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalDenyC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> BuildApproval.denyFor(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.AiSettingsSyncC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> applySettings(payload));
        });
    }

    private static void applySettings(AiNetworkPayloads.AiSettingsSyncC2SPayload payload) {
        AiProvider provider;
        try {
            provider = AiProvider.valueOf(payload.provider());
        } catch (IllegalArgumentException exception) {
            provider = AiProvider.OLLAMA;
        }

        AiBuilderConfig.get().applyFrom(
            provider,
            payload.modelName(),
            payload.apiUrl(),
            payload.apiKey(),
            payload.ticksPerBlock(),
            payload.forbiddenBlocksPath(),
            ForbiddenBlockRegistry.decodeFromNetwork(payload.forbiddenBlocksData()),
            payload.isDebugLogEnabled(),
            payload.isOpenAiWebSearchEnabled()
        );
        AiBuilderConfig.save();
    }

    public static void sendLog(ServerPlayerEntity player, String message) {
        sendLog(player, message, false);
    }

    public static void sendDebugLog(ServerPlayerEntity player, String message) {
        sendLog(player, message, true);
    }

    public static void sendLog(ServerPlayerEntity player, String message, boolean debugOnly) {
        if (debugOnly && !AiBuilderConfig.get().debugLogEnabled) {
            return;
        }

        runOnServerThread(player, () -> {
            if (player.networkHandler != null) {
                ServerPlayNetworking.send(player, new AiNetworkPayloads.AiLogS2CPayload(message));
            }
        });
    }

    public static void sendApprovalPending(ServerPlayerEntity player, String description) {
        runOnServerThread(player, () -> {
            if (player.networkHandler != null) {
                ServerPlayNetworking.send(player, new AiNetworkPayloads.ApprovalPendingS2CPayload(description));
            }
        });
    }

    public static void sendApprovalClear(ServerPlayerEntity player) {
        runOnServerThread(player, () -> {
            if (player.networkHandler != null) {
                ServerPlayNetworking.send(player, new AiNetworkPayloads.ApprovalClearS2CPayload());
            }
        });
    }

    public static void notify(ServerPlayerEntity player, String message) {
        sendLog(player, message);
        runOnServerThread(player, () -> player.sendMessage(Text.literal(message)));
    }

    private static void runOnServerThread(ServerPlayerEntity player, Runnable action) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        if (server.isOnThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }
}
