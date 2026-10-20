package com.example.ai.approval;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import com.example.ai.api.MinecraftAI;
import com.example.ai.build.BuildTaskQueue;
import com.example.ai.script.ScriptExecutor;
import com.example.network.AiNetworking;

public final class BuildApproval {

    private static String pendingCode = null;
    private static String pendingDescription = null;
    private static ServerPlayerEntity pendingPlayer = null;
    private static BlockPos pendingOrigin = null;

    private BuildApproval() {
    }

    public static void requestApproval(ServerPlayerEntity player, String code, String description, BlockPos origin) {
        pendingCode = code;
        pendingDescription = description;
        pendingPlayer = player;
        pendingOrigin = origin;

        AiNetworking.sendDebugLog(player, "[AI] Review the planned build below.");
        AiNetworking.sendApprovalPending(player, description);
    }

    public static void approveFor(ServerPlayerEntity player) {
        if (pendingCode == null || pendingPlayer == null) {
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
            AiNetworking.notify(player, "\u00a7c[AI] Cheats/OP required to place blocks.\u00a7r");
            clearPending(player);
            return;
        }

        BuildTaskQueue.clear();
        MinecraftAI.init(world, origin, player.getYaw(), player.getPitch(), player);

        AiNetworking.sendLog(player, "[AI] Building started (via /setblock and /fill)...");
        AiNetworking.sendLog(player, "[AI] Origin: X=" + origin.getX() + " Y=" + origin.getY() + " Z=" + origin.getZ());
        System.out.println("[APPROVAL] Building approved by " + player.getName().getString() + " at " + origin);

        ScriptExecutor.ExecutionResult result = ScriptExecutor.executeScript(pendingCode);
        if (!result.success()) {
            if (result.securityBlocked()) {
                AiNetworking.sendLog(player, "[Security Alert] " + result.message());
                AiNetworking.notify(player, "\u00a7c[Security Alert] " + result.message() + "\u00a7r");
            } else {
                AiNetworking.sendLog(player, "[AI] Script error: " + result.message());
            }
            clearPending(player);
            return;
        }

        int queuedCommands = BuildTaskQueue.pendingCount();
        if (queuedCommands == 0) {
            AiNetworking.sendLog(player, "[AI] Complete!");
            clearPending(player);
            return;
        }

        AiNetworking.sendLog(player, "[AI] Running " + queuedCommands + " build command(s)...");
        BuildTaskQueue.whenEmpty(() -> {
            AiNetworking.sendLog(player, "[AI] Complete!");
            clearPending(player);
        });
    }

    public static void denyFor(ServerPlayerEntity player) {
        if (pendingCode == null || pendingPlayer == null) {
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
        pendingCode = null;
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
        return pendingCode != null && pendingPlayer != null;
    }

    public static String getPendingDescription() {
        return pendingDescription == null ? "" : pendingDescription;
    }

    public static BlockPos getPendingOrigin() {
        return pendingOrigin;
    }
}
