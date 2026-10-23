package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.example.network.AiNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Undo" for the last few builds of each player. Before a build runs, the blocks in the area it
 * will touch are copied; undo puts them back and removes the object's display entities.
 * (Chest contents and other block-entity data in that area are not restored.)
 */
public final class UndoService {

    private static final int MAX_STEPS = 5;
    private static final long MAX_VOLUME = 500_000L;

    private record Entry(ServerWorld world, BlockPos min, int sx, int sy, int sz, BlockState[] states, String tag, String title) {
    }

    private static final Map<UUID, Deque<Entry>> STACKS = new HashMap<>();

    private UndoService() {
    }

    /** Call right before executePlan with the same arguments. */
    public static void capture(ServerPlayerEntity player, BuildPlan plan, BlockPos at, int turns, String tag, String title) {
        ServerWorld world = player.getServerWorld();
        BlockState[] states = null;
        BlockPos min = at;
        int sx = 0, sy = 0, sz = 0;
        if (!plan.ops.isEmpty()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BuildPlan.Op op : plan.ops) {
                for (int[] c : new int[][] { BuildPlan.rotate(op.x1, op.z1, turns), BuildPlan.rotate(op.x2, op.z2, turns) }) {
                    minX = Math.min(minX, c[0]);
                    maxX = Math.max(maxX, c[0]);
                    minZ = Math.min(minZ, c[1]);
                    maxZ = Math.max(maxZ, c[1]);
                }
                minY = Math.min(minY, Math.min(op.y1, op.y2));
                maxY = Math.max(maxY, Math.max(op.y1, op.y2));
            }
            sx = maxX - minX + 1;
            sy = maxY - minY + 1;
            sz = maxZ - minZ + 1;
            min = at.add(minX, minY, minZ);
            if ((long) sx * sy * sz <= MAX_VOLUME) {
                states = new BlockState[sx * sy * sz];
                BlockPos.Mutable pos = new BlockPos.Mutable();
                int i = 0;
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            states[i++] = world.getBlockState(pos.set(min.getX() + x, min.getY() + y, min.getZ() + z));
                        }
                    }
                }
            } else {
                AiNetworking.sendLog(player, "[AI] This build is too large to undo its blocks.");
            }
        }
        Deque<Entry> stack = STACKS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
        stack.push(new Entry(world, min, sx, sy, sz, states, plan.parts.isEmpty() ? null : tag, title));
        while (stack.size() > MAX_STEPS) {
            stack.removeLast();
        }
    }

    public static void undo(ServerPlayerEntity player) {
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.sendLog(player, "[AI] Undo requires cheats/OP (permission level 2).");
            return;
        }
        Deque<Entry> stack = STACKS.get(player.getUuid());
        Entry entry = stack == null ? null : stack.poll();
        if (entry == null) {
            AiNetworking.sendLog(player, "[AI] Nothing to undo.");
            return;
        }
        BuildTaskQueue.clear(); // stop a build that is still going
        int restored = 0;
        if (entry.states() != null) {
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int i = 0;
            for (int y = 0; y < entry.sy(); y++) {
                for (int z = 0; z < entry.sz(); z++) {
                    for (int x = 0; x < entry.sx(); x++) {
                        BlockState old = entry.states()[i++];
                        pos.set(entry.min().getX() + x, entry.min().getY() + y, entry.min().getZ() + z);
                        if (entry.world().getBlockState(pos) != old) {
                            entry.world().setBlockState(pos, old, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                            restored++;
                        }
                    }
                }
            }
        }
        if (entry.tag() != null) {
            MinecraftAI.init(entry.world(), player.getBlockPos(), player.getYaw(), player.getPitch(), player);
            MinecraftAI.runNow("kill @e[type=minecraft:block_display,tag=" + entry.tag() + "]");
        }
        AiNetworking.sendLog(player, "[AI] Undid \"" + entry.title() + "\" (" + restored + " block(s) restored"
            + (entry.tag() != null ? ", object removed" : "") + "). " + stack.size() + " more undo step(s).");
    }
}
