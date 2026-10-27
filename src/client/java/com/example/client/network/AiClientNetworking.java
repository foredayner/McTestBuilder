package com.example.client.network;

import com.example.ai.build.BuildRecord;
import com.example.ai.build.RebuildService;
import com.example.client.AiClientState;
import com.example.client.BuildHistory;
import com.example.config.AiBuilderConfig;
import com.example.config.ForbiddenBlockRegistry;
import com.example.network.AiNetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AiClientNetworking {

    private AiClientNetworking() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.AiLogS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> AiClientState.addLog(payload.message()));
        });

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalPendingS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                AiClientState.setPendingApproval(payload.description());
                AiClientState.addLog("[AI] Approval required.");
                for (String line : payload.description().split("\n")) {
                    if (!line.isEmpty()) {
                        AiClientState.addLog("  " + line);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ApprovalClearS2CPayload.ID, (payload, context) -> {
            context.client().execute(AiClientState::clearPendingApproval);
        });

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.SparkS2CPayload.ID, (payload, context) ->
            context.client().execute(() -> com.example.client.SparkRenderer.play(payload.sparks(),
                new net.minecraft.util.math.Vec3d(payload.x(), payload.y(), payload.z()),
                new net.minecraft.util.math.Vec3d(payload.dx(), payload.dy(), payload.dz()), payload.scale())));

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.ServerAiInfoS2CPayload.ID, (payload, context) ->
            context.client().execute(() -> AiClientState.setServerInfo(payload.provider(), payload.modelName(), payload.apiUrl(),
                payload.webSearchDefault(), payload.canEdit())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(AiClientState::clearServerInfo));

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.NearbyObjectsS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> AiClientState.setNearby(payload.json()));
        });

        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.BuildCompleteS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    BuildRecord record = BuildRecord.fromGzip(payload.data());
                    BuildHistory.save(record);
                    AiClientState.addLog("[History] 보관함에 추가했어요. 보관함에서 \"손에 받기\"로 꺼내 쓰세요.");
                } catch (RuntimeException exception) {
                    AiClientState.addLog("[History] Could not save this build: " + exception.getMessage());
                }
            });
        });
    }

    public static void sendApprovalAction(boolean approve) {
        if (approve) {
            ClientPlayNetworking.send(new AiNetworkPayloads.ApprovalAcceptC2SPayload());
        } else {
            ClientPlayNetworking.send(new AiNetworkPayloads.ApprovalDenyC2SPayload());
        }
    }

    public static void sendRemoveObject(int entityId, String tag) {
        ClientPlayNetworking.send(new AiNetworkPayloads.RemoveObjectC2SPayload(entityId, tag == null ? "" : tag));
    }

    public static void requestNearby() {
        ClientPlayNetworking.send(new AiNetworkPayloads.NearbyRequestC2SPayload());
    }

    public static void sendBreak(int entityId) {
        ClientPlayNetworking.send(new AiNetworkPayloads.BreakObjectC2SPayload(entityId));
    }

    public static void sendAttackSwing() {
        if (ClientPlayNetworking.canSend(AiNetworkPayloads.AttackSwingC2SPayload.ID)) {
            ClientPlayNetworking.send(new AiNetworkPayloads.AttackSwingC2SPayload());
        }
    }

    public static void sendUndo() {
        ClientPlayNetworking.send(new AiNetworkPayloads.UndoC2SPayload());
    }

    /** Send a saved build to the server in parts small enough for client packets. */
    public static void sendRebuild(BuildRecord record) {
        sendRebuild(record, RebuildService.MODE_PLACE);
    }

    /** mode: RebuildService.MODE_PLACE (build in front of me) or MODE_GIVE (blueprint paper). */
    public static void sendRebuild(BuildRecord record, int mode) {
        sendRebuild(record, mode, 100);
    }

    /** scalePercent: 100 = as designed (objects can be placed bigger or smaller). */
    public static void sendRebuild(BuildRecord record, int mode, int scalePercent) {
        byte[] data = record.toGzip();
        int part = RebuildService.PART_SIZE;
        int total = Math.max(1, (data.length + part - 1) / part);
        int id = (int) (System.nanoTime() & 0x7fffffff);
        for (int i = 0; i < total; i++) {
            int from = i * part;
            byte[] chunk = java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + part));
            ClientPlayNetworking.send(new AiNetworkPayloads.RebuildPartC2SPayload(id, i, total, mode, scalePercent, chunk));
        }
    }

    public static void sendSettingsSync(AiBuilderConfig config) {
        ClientPlayNetworking.send(new AiNetworkPayloads.AiSettingsSyncC2SPayload(
            config.provider.name(),
            config.modelName,
            config.apiUrl,
            config.apiKey,
            config.ticksPerBlock,
            config.forbiddenBlocksPath,
            ForbiddenBlockRegistry.encodeForNetwork(config.forbiddenBlocks),
            config.debugLogEnabled ? 1 : 0,
            config.openAiWebSearchEnabled ? 1 : 0,
            config.autoRetries,
            config.keepAliveMinutes
        ));
    }
}
