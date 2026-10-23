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

    /** What the client wants done with a saved build. */
    public static final int MODE_PLACE = 0;
    public static final int MODE_GIVE = 1;

    public static void receivePart(ServerPlayerEntity player, int id, int index, int total, int mode, byte[] data) {
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
        BuildRecord record = readChecked(player, all.toByteArray());
        if (record == null) {
            return;
        }
        if (mode == MODE_GIVE) {
            BlueprintStore.give(player, record);
        } else {
            placeInFront(player, record);
        }
    }

    /** Decode and check a saved build sent by a client; null (with a message) if refused. */
    static BuildRecord readChecked(ServerPlayerEntity player, byte[] data) {
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.sendLog(player, "[History] 치트(OP 권한 2)가 켜져 있어야 배치할 수 있어요.");
            return null;
        }
        BuildRecord record;
        try {
            record = BuildRecord.fromGzip(data);
        } catch (RuntimeException exception) {
            AiNetworking.sendLog(player, "[History] 저장된 설계를 읽지 못했어요: " + exception.getMessage());
            return null;
        }
        String problem = validate(record.plan);
        if (problem != null) {
            AiNetworking.sendLog(player, "[History] 설계를 쓸 수 없어요: " + problem);
            return null;
        }
        return record;
    }

    static String title(BuildRecord record) {
        String p = record.prompt.replace('\n', ' ');
        return p.length() > 40 ? p.substring(0, 40) + "..." : p;
    }

    static String facingOf(ServerPlayerEntity player) {
        return BuildPlan.normalizeFacing(TerrainVision.compassFromYaw(player.getYaw()));
    }

    /** "배치": exactly as it was designed relative to the player, turned to where they look. */
    public static void placeInFront(ServerPlayerEntity player, BuildRecord record) {
        String facing = facingOf(player);
        place(player, record, player.getBlockPos(), BuildPlan.turnsBetween(record.plan.facing, facing), facing);
    }

    /**
     * Blueprint use: the build starts at {@code target} (the block next to the clicked face) and
     * extends away from the player. The near edge of its footprint, centred, sits on the target.
     */
    public static void placeAt(ServerPlayerEntity player, BuildRecord record, BlockPos target) {
        String facing = facingOf(player);
        int turns = BuildPlan.turnsBetween(record.plan.facing, facing);
        int[] a = anchor(record.plan, turns, facing);
        place(player, record, target.add(-a[0], -a[1], -a[2]), turns, facing);
    }

    /** Point of the turned build that should land on the clicked spot: middle of its near edge, bottom. */
    static int[] anchor(BuildPlan plan, int turns, String facing) {
        int[] b = plan.bounds();
        int[] p = BuildPlan.rotate(b[0], b[2], turns);
        int[] q = BuildPlan.rotate(b[3], b[5], turns);
        int minX = Math.min(p[0], q[0]), maxX = Math.max(p[0], q[0]);
        int minZ = Math.min(p[1], q[1]), maxZ = Math.max(p[1], q[1]);
        int midX = Math.floorDiv(minX + maxX, 2), midZ = Math.floorDiv(minZ + maxZ, 2);
        return switch (facing) {
            case "south" -> new int[] { midX, b[1], minZ };
            case "east" -> new int[] { minX, b[1], midZ };
            case "west" -> new int[] { maxX, b[1], midZ };
            default -> new int[] { midX, b[1], maxZ }; // north: the near side is the south edge
        };
    }

    private static void place(ServerPlayerEntity player, BuildRecord record, BlockPos origin, int turns, String facing) {
        MinecraftAI.init(player.getServerWorld(), origin, player.getYaw(), player.getPitch(), player);
        String title = title(record);
        String tag = "aib_" + record.id + "_" + Long.toString(System.currentTimeMillis(), 36);
        UndoService.capture(player, record.plan, origin, turns, tag, title);
        int commands = MinecraftAI.executePlan(record.plan, origin, turns, tag, title);
        AiNetworking.sendLog(player, "[History] \"" + title + "\" 배치 중 (" + facingKo(facing) + "쪽 방향, 명령 " + commands + "개)...");
        BuildTaskQueue.whenEmpty(() -> AiNetworking.sendLog(player, "[History] 배치 완료! 되돌리기로 취소할 수 있어요."));
    }

    static String facingKo(String facing) {
        return switch (facing) {
            case "east" -> "동";
            case "south" -> "남";
            case "west" -> "서";
            default -> "북";
        };
    }

    /** Only plain setblock/fill with sane coordinates, known modes and allowed blocks. */
    static String validate(BuildPlan plan) {
        if (plan.isEmpty()) {
            return "it has no blocks";
        }
        if (plan.parts.size() > BuildPlan.MAX_PARTS) {
            return "too many object parts (" + plan.parts.size() + ")";
        }
        for (BuildPlan.Part part : plan.parts) {
            if (part.block == null || !BLOCK.matcher(part.block).matches()) {
                return "bad block " + part.block;
            }
            String id = part.block.contains("[") ? part.block.substring(0, part.block.indexOf('[')) : part.block;
            if (ForbiddenBlockRegistry.isForbidden(id)) {
                return "forbidden block " + id;
            }
            double big = Math.max(part.sx, Math.max(part.sy, part.sz));
            double far = Math.max(Math.abs(part.x), Math.max(Math.abs(part.y), Math.abs(part.z)));
            if (!(big > 0 && big <= BuildPlan.MAX_PART_SIZE) || !(far <= BuildPlan.MAX_PART_OFFSET)
                || !Double.isFinite(part.yaw + part.pitch + part.roll)) {
                return "object part out of range";
            }
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
