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
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.ServerAiInfoS2CPayload.ID, AiNetworkPayloads.ServerAiInfoS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.RebuildPartC2SPayload.ID, AiNetworkPayloads.RebuildPartC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.UndoC2SPayload.ID, AiNetworkPayloads.UndoC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.AttackSwingC2SPayload.ID, AiNetworkPayloads.AttackSwingC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.SparkS2CPayload.ID, AiNetworkPayloads.SparkS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.BreakObjectC2SPayload.ID, AiNetworkPayloads.BreakObjectC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.RemoveObjectC2SPayload.ID, AiNetworkPayloads.RemoveObjectC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiNetworkPayloads.NearbyRequestC2SPayload.ID, AiNetworkPayloads.NearbyRequestC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiNetworkPayloads.NearbyObjectsS2CPayload.ID, AiNetworkPayloads.NearbyObjectsS2CPayload.CODEC);
    }

    public static void registerServerReceivers() {
        // Everyone learns what the server's AI is when they join (for the web search button and the settings tab).
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            sendServerInfo(handler.player));

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
                // The server's AI settings (and API key) are changed only by the server's owner: a
                // friend's own (default) settings must never replace them.
                ServerPlayerEntity player = context.player();
                if (!canEditSettings(player)) {
                    player.sendMessage(Text.literal(SETTINGS_OWNER_ONLY), true);
                    sendServerInfo(player);
                    return;
                }
                applySettings(payload);
                for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                    sendServerInfo(p);
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

    public static final String SETTINGS_OWNER_ONLY = "AI 설정은 서버 주인만 바꿀 수 있어요.";

    /**
     * Whether a player may change the server's AI settings: the player who opened the world (single
     * player or LAN), or on a dedicated server an operator of the highest level (4).
     */
    public static boolean canEditSettings(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        var server = player.getServer();
        if (!server.isDedicated()) {
            return server.isHost(player.getGameProfile());
        }
        return player.hasPermissionLevel(4);
    }

    /** Tell a player what the server's AI is (never the key) and whether they may change it. */
    public static void sendServerInfo(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, AiNetworkPayloads.ServerAiInfoS2CPayload.ID)) {
            return;
        }
        AiBuilderConfig c = AiBuilderConfig.get();
        ServerPlayNetworking.send(player, new AiNetworkPayloads.ServerAiInfoS2CPayload(c.provider.name(), c.modelName, c.apiUrl,
            c.openAiWebSearchEnabled, canEditSettings(player)));
    }

    private static void applySettings(AiNetworkPayloads.AiSettingsSyncC2SPayload payload) {
        AiProvider provider;
        try {
            provider = AiProvider.valueOf(payload.provider());
        } catch (IllegalArgumentException exception) {
            provider = AiProvider.OLLAMA;
        }

        // An empty key keeps the one the server has (a settings screen that does not know the key
        // must not wipe it).
        String key = payload.apiKey() == null || payload.apiKey().isBlank() ? AiBuilderConfig.get().apiKey : payload.apiKey();
        AiBuilderConfig.get().applyFrom(
            provider,
            payload.modelName(),
            payload.apiUrl(),
            key,
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
