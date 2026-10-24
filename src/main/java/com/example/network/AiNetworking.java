package com.example.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.example.ai.approval.BuildApproval;
import com.example.ai.build.BuildRecord;
import com.example.ai.build.RebuildService;
import com.example.ai.build.UndoService;
import com.example.ai.build.ObjectService;
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
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.BuildCompleteS2CPayload.ID, AiNetworkPayloads.BuildCompleteS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.RebuildPartC2SPayload.ID, AiNetworkPayloads.RebuildPartC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.UndoC2SPayload.ID, AiNetworkPayloads.UndoC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.AttackSwingC2SPayload.ID, AiNetworkPayloads.AttackSwingC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.BreakObjectC2SPayload.ID, AiNetworkPayloads.BreakObjectC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.RemoveObjectC2SPayload.ID, AiNetworkPayloads.RemoveObjectC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.NearbyRequestC2SPayload.ID, AiNetworkPayloads.NearbyRequestC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.NearbyObjectsS2CPayload.ID, AiNetworkPayloads.NearbyObjectsS2CPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalAcceptC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (allowed(context.player())) {
                    BuildApproval.approveFor(context.player());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalDenyC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (allowed(context.player())) {
                    BuildApproval.denyFor(context.player());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.AiSettingsSyncC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // The server's AI settings (and API key) are only changed by operators.
                if (allowed(context.player())) {
                    applySettings(payload);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.RemoveObjectC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!allowed(context.player())) {
                    return;
                }
                if (payload.tag().isEmpty()) {
                    ObjectService.removeByEntity(context.player(), payload.entityId());
                } else {
                    ObjectService.removeByTag(context.player(), payload.tag());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.NearbyRequestC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (allowed(context.player())) {
                    ObjectService.sendNearby(context.player());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.BreakObjectC2SPayload.ID, (payload, context) ->
            context.server().execute(() -> com.example.ai.build.ObjectItems.breakObject(context.player(), payload.entityId())));
        // Using what was made is open to everyone: swinging an AI weapon plays its effect.
        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.AttackSwingC2SPayload.ID, (payload, context) ->
            context.server().execute(() -> com.example.ai.build.GearEffects.onSwing(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.UndoC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (allowed(context.player())) {
                    UndoService.undo(context.player());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.RebuildPartC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (allowed(context.player())) {
                    RebuildService.receivePart(context.player(), payload.id(), payload.index(), payload.total(),
                        payload.mode(), payload.scalePercent(), payload.data());
                }
            });
        });
    }

    /** Permission level that may use the AI 공방 (requests, settings, 손에 받기, undo, removal). */
    public static final int OP_LEVEL = 2;
    public static final String OP_ONLY = "관리자(OP)만 사용할 수 있어요.";

    /**
     * Everything that talks to the AI or changes the server's settings is for operators only.
     * Using what was made (placing object items, wearing gear, mining objects) stays open to all.
     */
    public static boolean allowed(ServerPlayerEntity player) {
        if (player != null && player.hasPermissionLevel(OP_LEVEL)) {
            return true;
        }
        if (player != null) {
            player.sendMessage(Text.literal(OP_ONLY), true);
        }
        return false;
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
        AiBuilderConfig.get().autoRetries = payload.autoRetries();
        AiBuilderConfig.get().keepAliveMinutes = payload.keepAliveMinutes();
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

    public static void sendBuildComplete(ServerPlayerEntity player, BuildRecord record) {
        runOnServerThread(player, () -> {
            if (player.networkHandler != null) {
                ServerPlayNetworking.send(player, new AiNetworkPayloads.BuildCompleteS2CPayload(record.toGzip()));
            }
        });
    }

    public static void sendNearby(ServerPlayerEntity player, String json) {
        runOnServerThread(player, () -> {
            if (player.networkHandler != null) {
                ServerPlayNetworking.send(player, new AiNetworkPayloads.NearbyObjectsS2CPayload(json));
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

        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                System.out.println("[AI] Could not send to " + player.getName().getString() + ": " + exception.getMessage());
            }
        };
        if (server.isOnThread()) {
            safe.run();
        } else {
            server.execute(safe);
        }
    }
}
