package com.example.ai.approval;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import com.example.ai.api.MinecraftAI;
import com.example.ai.build.BuildRecord;
import com.example.ai.build.BuildTaskQueue;
import com.example.ai.build.UndoService;
import com.example.network.AiNetworking;

/**
 * Holds a recorded build until the player accepts it. Accepting runs exactly the blocks that
 * were recorded during the dry run (the script is not run again), then sends the finished
 * build to the player's history.
 */
public final class BuildApproval {

    private static BuildRecord pendingRecord = null;
    private static String pendingDescription = null;
    private static ServerPlayerEntity pendingPlayer = null;
    private static BlockPos pendingOrigin = null;

    private BuildApproval() {
    }

    public static void requestApproval(ServerPlayerEntity player, BuildRecord record, String description, BlockPos origin) {
        pendingRecord = record;
        pendingDescription = description;
        pendingPlayer = player;
        pendingOrigin = origin;

        AiNetworking.sendDebugLog(player, "[AI] Review the planned build below.");
        AiNetworking.sendApprovalPending(player, description);
    }

    public static void approveFor(ServerPlayerEntity player) {
        if (pendingRecord == null || pendingPlayer == null) {
            AiNetworking.sendLog(player, "[AI] Nothing to approve.");
            return;
        }

        if (!pendingPlayer.getUuid().equals(player.getUuid())) {
            AiNetworking.sendLog(player, "[AI] Only the requesting player can approve.");
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos origin = pendingOrigin != null ? pendingOrigin : player.getBlockPos();

        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.sendLog(player, "[AI] Build requires cheats/OP (permission level 2).");
            AiNetworking.sendLog(player, "[AI] Enable cheats for this world, or run as an operator.");
            AiNetworking.notify(player, "§c[AI] Cheats/OP required to place blocks.§r");
            clearPending(player);
            return;
        }

        // Accepting registers the design in the player's 보관함; placing is a separate step there.
        BuildRecord record = pendingRecord;
        System.out.println("[APPROVAL] Design registered by " + player.getName().getString());
        clearPending(player);
        AiNetworking.sendLog(player, "[AI] 보관함에 등록했어요. 보관함에서 배치하거나 설계도로 받아 쓰세요.");
        AiNetworking.sendBuildComplete(player, record);
    }

    static String shortTitle(String prompt) {
        String p = prompt == null ? "" : prompt.replace('\n', ' ');
        return p.length() > 40 ? p.substring(0, 40) + "..." : p;
    }

    public static void denyFor(ServerPlayerEntity player) {
        if (pendingRecord == null || pendingPlayer == null) {
            AiNetworking.sendLog(player, "[AI] Nothing to cancel.");
            return;
        }

        if (!pendingPlayer.getUuid().equals(player.getUuid())) {
            AiNetworking.sendLog(player, "[AI] Only the requesting player can cancel.");
            return;
        }

        AiNetworking.sendLog(player, "[AI] Cancelled.");
        System.out.println("[APPROVAL] Building cancelled by " + player.getName().getString());
        clearPending(player);
    }

    private static void clearPending(ServerPlayerEntity player) {
        pendingRecord = null;
        pendingDescription = null;
        pendingPlayer = null;
        pendingOrigin = null;
        AiNetworking.sendApprovalClear(player);
    }

    @Deprecated
    public static void approve() {
        if (pendingPlayer != null) {
            approveFor(pendingPlayer);
        }
    }

    @Deprecated
    public static void deny() {
        if (pendingPlayer != null) {
            denyFor(pendingPlayer);
        }
    }

    public static boolean hasPending() {
        return pendingRecord != null && pendingPlayer != null;
    }

    public static String getPendingDescription() {
        return pendingDescription == null ? "" : pendingDescription;
    }

    public static BlockPos getPendingOrigin() {
        return pendingOrigin;
    }
}
