package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.example.ai.api.TerrainVision;
import com.example.config.ForbiddenBlockRegistry;
import com.example.network.AiNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Rebuilds a structure from the player's history (no AI call). The client sends the saved
 * record in parts (a client packet is limited to ~32 KB); once all parts arrive it is checked
 * and placed in front of the player, turned to the way they face now.
 */
public final class RebuildService {

    public static final int PART_SIZE = 30_000;
    private static final int MAX_PARTS = 256;
    private static final int MAX_OPS = 50_000;
    private static final int MAX_OFFSET = 512;
    private static final Pattern BLOCK = Pattern.compile("^[a-z0-9_.\\-]+:[a-z0-9_./\\-]+(\\[[a-z0-9_=,]*\\])?$");
    private static final Set<String> MODES = Set.of("replace", "outline", "hollow", "keep", "destroy");

    private static final class Assembly {
        final int id;
        final byte[][] parts;
        int received;

        Assembly(int id, int total) {
            this.id = id;
            this.parts = new byte[total][];
        }
    }

    private static final Map<UUID, Assembly> ASSEMBLIES = new HashMap<>();

    private RebuildService() {
    }

    public static void receivePart(ServerPlayerEntity player, int id, int index, int total, byte[] data) {
        if (total <= 0 || total > MAX_PARTS || index < 0 || index >= total || data == null || data.length > PART_SIZE) {
            return;
        }
        Assembly assembly = ASSEMBLIES.get(player.getUuid());
        if (assembly == null || assembly.id != id || assembly.parts.length != total) {
            assembly = new Assembly(id, total);
            ASSEMBLIES.put(player.getUuid(), assembly);
        }
        if (assembly.parts[index] == null) {
            assembly.parts[index] = data;
            assembly.received++;
        }
        if (assembly.received < total) {
            return;
        }
        ASSEMBLIES.remove(player.getUuid());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : assembly.parts) {
            all.writeBytes(part);
        }
        rebuild(player, all.toByteArray());
    }

    private static void rebuild(ServerPlayerEntity player, byte[] data) {
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.sendLog(player, "[History] Rebuild requires cheats/OP (permission level 2).");
            return;
        }
        BuildRecord record;
        try {
            record = BuildRecord.fromGzip(data);
        } catch (RuntimeException exception) {
            AiNetworking.sendLog(player, "[History] Could not read the saved build: " + exception.getMessage());
            return;
        }
        String problem = validate(record.plan);
        if (problem != null) {
            AiNetworking.sendLog(player, "[History] Saved build rejected: " + problem);
            return;
        }

        BlockPos origin = player.getBlockPos();
        String facing = BuildPlan.normalizeFacing(TerrainVision.compassFromYaw(player.getYaw()));
        int turns = BuildPlan.turnsBetween(record.plan.facing, facing);
        MinecraftAI.init(player.getServerWorld(), origin, player.getYaw(), player.getPitch(), player);
        int commands = MinecraftAI.executePlan(record.plan, origin, turns);

        String title = record.prompt.length() > 40 ? record.prompt.substring(0, 40) + "..." : record.prompt;
        AiNetworking.sendLog(player, "[History] Rebuilding \"" + title + "\" facing " + facing + " (" + commands + " commands)...");
        BuildTaskQueue.whenEmpty(() -> AiNetworking.sendLog(player, "[History] Rebuild complete!"));
    }

    /** Only plain setblock/fill with sane coordinates, known modes and allowed blocks. */
    static String validate(BuildPlan plan) {
        if (plan.ops.isEmpty()) {
            return "it has no blocks";
        }
        if (plan.ops.size() > MAX_OPS) {
            return "too many commands (" + plan.ops.size() + ")";
        }
        for (BuildPlan.Op op : plan.ops) {
            if (!"set".equals(op.kind) && !"fill".equals(op.kind)) {
                return "unknown command " + op.kind;
            }
            if (op.block == null || !BLOCK.matcher(op.block).matches()) {
                return "bad block " + op.block;
            }
            String id = op.block.contains("[") ? op.block.substring(0, op.block.indexOf('[')) : op.block;
            if (ForbiddenBlockRegistry.isForbidden(id)) {
                return "forbidden block " + id;
            }
            if ("fill".equals(op.kind) && (op.mode == null || !MODES.contains(op.mode))) {
                return "bad fill mode " + op.mode;
            }
            for (int v : new int[] { op.x1, op.y1, op.z1, op.x2, op.y2, op.z2 }) {
                if (Math.abs(v) > MAX_OFFSET) {
                    return "coordinates too far from the player";
                }
            }
        }
        return null;
    }
}
