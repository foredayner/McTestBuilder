package com.example.ai.api;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import com.example.ai.build.BuildTaskQueue;
import com.example.config.ForbiddenBlockRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Build helpers used by AI Groovy scripts.
 *
 * <p>Placement goes through vanilla {@code /setblock} and {@code /fill} as the
 * requesting player, so cheats/OP permission is required. This avoids bypassing
 * Survival rules via direct {@code setBlockState}.
 */
public class MinecraftAI {
    private static ServerWorld world;
    private static BlockPos origin;
    private static float playerYaw;
    private static float playerPitch;
    private static ServerPlayerEntity actor;
    
    public static void init(ServerWorld w, BlockPos o) {
        init(w, o, 0f, 0f, null);
    }

    public static void init(ServerWorld w, BlockPos o, float yaw, float pitch) {
        init(w, o, yaw, pitch, null);
    }

    public static void init(ServerWorld w, BlockPos o, float yaw, float pitch, ServerPlayerEntity player) {
        world = w;
        origin = o;
        playerYaw = yaw;
        playerPitch = pitch;
        actor = player;
        System.out.println("[MinecraftAI] Initialized at " + o + " facing " + TerrainVision.compassFromYaw(yaw)
            + (player != null ? " as " + player.getName().getString() : ""));
    }

    public static boolean canExecuteBuildCommands(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }

        // 1.21.4: plain permission levels (2 = game masters: /setblock, /fill).
        return player.hasPermissionLevel(2);
    }
    
    public static String getWorldInfo(Number radius) {
        return getWorldInfo(radius, 0f, 0f);
    }

    public static String getWorldInfo(Number radius, float yaw, float pitch) {
        int r = toInt(radius);
        if (world == null || origin == null) {
            return "World not initialized";
        }
        
        StringBuilder info = new StringBuilder();
        
        int surfaceRelativeY = getGroundLevel(0, 0);
        info.append("Player feet (relative 0,0,0) at world Y=").append(origin.getY()).append("\n");
        info.append("Ground surface at (0,0): relative Y=").append(surfaceRelativeY).append("\n");
        
        String biome = world.getBiome(origin)
            .getKey()
            .map(key -> key.getValue().toString())
            .orElse("unknown");
        info.append("Biome: ").append(biome).append("\n");
        
        Map<String, Integer> blockCount = new HashMap<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = origin.getY() - 5; y <= origin.getY() + 5; y++) {
                    BlockPos pos = origin.add(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    String name = Registries.BLOCK.getId(block).toString();
                    blockCount.merge(name, 1, Integer::sum);
                }
            }
        }
        
        info.append("Available materials: ");
        String materials = blockCount.entrySet().stream()
            .filter(e -> e.getValue() > 5 && !e.getKey().contains("air"))
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(", "));
        
        if (materials.isEmpty()) {
            info.append("stone, dirt, grass_block");
        } else {
            info.append(materials);
        }

        TerrainVision.appendReport(info, world, origin, yaw, pitch);
        
        return info.toString();
    }
    
    
    public static void placeBlock(Number x, Number y, Number z, String blockName) {
        if (world == null || origin == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }

        BlockPos pos = origin.add(toInt(x), toInt(y), toInt(z));
        scheduleSetBlock(pos, blockName);
    }

    public static void placeBlock(String blockName, Number x, Number y, Number z) {
        placeBlock(x, y, z, blockName);
    }
    
    public static void placeBox(Number x, Number y, Number z, Number w, Number h, Number l, String blockName) {
        int ix = toInt(x);
        int iy = toInt(y);
        int iz = toInt(z);
        int iw = Math.max(1, Math.abs(toInt(w)));
        int ih = Math.max(1, Math.abs(toInt(h)));
        int il = Math.max(1, Math.abs(toInt(l)));

        if (world == null || origin == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }

        BlockPos from = origin.add(ix, iy, iz);
        BlockPos to = origin.add(ix + iw - 1, iy + ih - 1, iz + il - 1);
        scheduleFill(from, to, blockName, "outline");
        System.out.println("[AI] Queued /fill outline box of " + normalizeBlockId(blockName));
    }

    public static void placeBox(String blockName, Number x, Number y, Number z, Number w, Number h, Number l) {
        placeBox(x, y, z, w, h, l, blockName);
    }
    
    public static void placePillar(Number x, Number y, Number z, Number h, String blockName) {
        int ix = toInt(x);
        int iy = toInt(y);
        int iz = toInt(z);
        int ih = Math.max(1, Math.abs(toInt(h)));

        if (world == null || origin == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }

        BlockPos from = origin.add(ix, iy, iz);
        BlockPos to = origin.add(ix, iy + ih - 1, iz);
        scheduleFill(from, to, blockName, "replace");
    }

    public static void placePillar(String blockName, Number x, Number y, Number z, Number h) {
        placePillar(x, y, z, h, blockName);
    }

    public static void placeFloor(Number x, Number y, Number z, Number w, Number l, String blockName) {
        int ix = toInt(x);
        int iy = toInt(y);
        int iz = toInt(z);
        int iw = Math.max(1, Math.abs(toInt(w)));
        int il = Math.max(1, Math.abs(toInt(l)));

        if (world == null || origin == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }

        BlockPos from = origin.add(ix, iy, iz);
        BlockPos to = origin.add(ix + iw - 1, iy, iz + il - 1);
        scheduleFill(from, to, blockName, "replace");
    }

    public static void placeFloor(String blockName, Number x, Number y, Number z, Number w, Number l) {
        placeFloor(x, y, z, w, l, blockName);
    }

    public static void placeFloor(Number x, Number y, Number z, Number w, Number l) {
        placeFloor(x, y, z, w, l, "oak_planks");
    }
    
    public static void placeRoof(Number x, Number y, Number z, Number w, Number l, String blockName) {
        placeFloor(x, y, z, w, l, blockName);
    }

    public static void placeRoof(String blockName, Number x, Number y, Number z, Number w, Number l) {
        placeRoof(x, y, z, w, l, blockName);
    }

    public static void placeRoof(Number x, Number y, Number z, Number w, Number l) {
        placeRoof(x, y, z, w, l, "oak_planks");
    }
    
    public static void placeWall(Number x, Number y, Number z, Number h, Number l, String first, String second) {
        if (isWallDirection(first)) {
            placeWallInternal(x, y, z, h, l, second, first);
        } else {
            placeWallInternal(x, y, z, h, l, first, second);
        }
    }

    public static void placeWall(String blockName, Number x, Number y, Number z, Number h, Number l, String direction) {
        placeWallInternal(x, y, z, h, l, blockName, direction);
    }

    private static void placeWallInternal(Number x, Number y, Number z, Number h, Number l, String blockName, String direction) {
        int ix = toInt(x);
        int iy = toInt(y);
        int iz = toInt(z);
        int ih = Math.max(1, Math.abs(toInt(h)));
        int il = Math.max(1, Math.abs(toInt(l)));

        if (world == null || origin == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }

        String axis = normalizeWallDirection(direction);
        if ("x".equals(axis)) {
            BlockPos from = origin.add(ix, iy, iz);
            BlockPos to = origin.add(ix + il - 1, iy + ih - 1, iz);
            scheduleFill(from, to, blockName, "replace");
        } else if ("z".equals(axis)) {
            BlockPos from = origin.add(ix, iy, iz);
            BlockPos to = origin.add(ix, iy + ih - 1, iz + il - 1);
            scheduleFill(from, to, blockName, "replace");
        }
    }

    private static boolean isWallDirection(String value) {
        if (value == null) {
            return false;
        }

        return switch (value.toLowerCase()) {
            case "north", "south", "east", "west", "x", "z" -> true;
            default -> false;
        };
    }

    private static String normalizeWallDirection(String direction) {
        if (direction == null) {
            return "";
        }

        return switch (direction.toLowerCase()) {
            case "x", "north", "south" -> "x";
            case "z", "east", "west" -> "z";
            default -> direction.toLowerCase();
        };
    }



    public static String getFacing() {
        return TerrainVision.compassFromYaw(playerYaw);
    }

    public static int getForwardX(Number distance) {
        return TerrainVision.forwardX(playerYaw, toInt(distance));
    }

    public static int getForwardZ(Number distance) {
        return TerrainVision.forwardZ(playerYaw, toInt(distance));
    }

    public static String inspectColumn(Number x, Number z) {
        if (world == null || origin == null) {
            return "uninitialized";
        }

        int ix = toInt(x);
        int iz = toInt(z);
        int groundY = getGroundLevel(ix, iz);
        int headroom = TerrainVision.countHeadroom(world, origin, ix, iz, groundY);
        boolean blocked = TerrainVision.isBlockedAtBodyHeight(world, origin, ix, iz, groundY);
        String blockAtFeet = getBlock(ix, 0, iz);
        String blockAboveGround = getBlock(ix, groundY + 1, iz);

        return "column(" + ix + "," + iz + ")"
            + " groundY=" + groundY
            + " headroom=" + headroom
            + " blocked=" + blocked
            + " feet=" + shortName(blockAtFeet)
            + " aboveGround=" + shortName(blockAboveGround);
    }

    public static String scanForward(Number maxDistance) {
        if (world == null || origin == null) {
            return "uninitialized";
        }

        int max = Math.clamp(toInt(maxDistance), 1, 16);
        StringBuilder report = new StringBuilder();
        report.append("forwardScan max=").append(max).append(" facing=").append(getFacing()).append('\n');

        for (int distance = 1; distance <= max; distance++) {
            int x = getForwardX(distance);
            int z = getForwardZ(distance);
            report.append(" d=").append(distance)
                .append(" -> ").append(inspectColumn(x, z))
                .append('\n');
        }

        return report.toString().trim();
    }

    public static String scanArea(Number x, Number z, Number width, Number length) {
        if (world == null || origin == null) {
            return "uninitialized";
        }

        int ix = toInt(x);
        int iz = toInt(z);
        int iw = Math.clamp(toInt(width), 1, 15);
        int il = Math.clamp(toInt(length), 1, 15);

        StringBuilder report = new StringBuilder();
        report.append("areaScan origin=(").append(ix).append(",").append(iz).append(")")
            .append(" size=").append(iw).append("x").append(il)
            .append(" (values=ground relative Y)\n");

        for (int dz = 0; dz < il; dz++) {
            for (int dx = 0; dx < iw; dx++) {
                report.append(getGroundLevel(ix + dx, iz + dz)).append(' ');
            }
            report.append('\n');
        }

        return report.toString().trim();
    }

    private static String shortName(String blockId) {
        int colon = blockId.indexOf(':');
        return colon >= 0 ? blockId.substring(colon + 1) : blockId;
    }
    
    public static boolean canPlace(Number x, Number y, Number z) {
        if (world == null) {
            return false;
        }

        BlockPos pos = origin.add(toInt(x), toInt(y), toInt(z));
        return world.getBlockState(pos).getBlock() == Blocks.AIR;
    }
    
    public static boolean isSolid(Number x, Number y, Number z) {
        if (world == null) {
            return false;
        }

        BlockPos pos = origin.add(toInt(x), toInt(y), toInt(z));
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
    
    public static String getBlock(Number x, Number y, Number z) {
        if (world == null) {
            return "air";
        }

        BlockPos pos = origin.add(toInt(x), toInt(y), toInt(z));
        return Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
    }
    
    public static int getGroundLevel(Number x, Number z) {
        int ix = toInt(x);
        int iz = toInt(z);

        if (world == null || origin == null) {
            return 0;
        }

        BlockPos column = origin.add(ix, 0, iz);
        int bottomY = world.getBottomY();

        for (int y = origin.getY(); y >= bottomY; y--) {
            BlockPos pos = column.withY(y);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                return y - origin.getY();
            }
        }

        return 0;
    }


    public static String listBlocks() {
        return listBlocks(0, 50);
    }

    public static String listBlocks(Number offset, Number limit) {
        return BlockCatalog.listPage(toInt(offset), toInt(limit));
    }

    public static String searchBlocks(String query) {
        return searchBlocks(query, 40);
    }

    public static String searchBlocks(String query, Number limit) {
        return BlockCatalog.search(query, toInt(limit));
    }

    public static int getBlockListCount() {
        return BlockCatalog.countAvailable();
    }

    public static boolean isBlockAllowed(String blockName) {
        return BlockCatalog.isAllowed(blockName);
    }

    public static void clearArea(Number x, Number y, Number z, Number w, Number l) {
        clearArea(x, y, z, w, 1, l);
    }

    public static void clearArea(Number x, Number y, Number z, Number w, Number h, Number l) {
        int ix = toInt(x);
        int iy = toInt(y);
        int iz = toInt(z);
        int iw = Math.max(1, Math.abs(toInt(w)));
        int ih = Math.max(1, Math.abs(toInt(h)));
        int il = Math.max(1, Math.abs(toInt(l)));

        if (world == null || origin == null) {
            return;
        }

        BlockPos from = origin.add(ix, iy, iz);
        BlockPos to = origin.add(ix + iw - 1, iy + ih - 1, iz + il - 1);
        scheduleFill(from, to, "minecraft:air", "replace");
    }

    private static void scheduleSetBlock(BlockPos pos, String blockName) {
        String blockId = normalizeBlockId(blockName);
        BuildTaskQueue.enqueue(() -> executeBuildCommand(
            "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + blockId + " replace"
        ));
    }

    private static void scheduleFill(BlockPos from, BlockPos to, String blockName, String mode) {
        String blockId = normalizeBlockId(blockName);
        String fillMode = mode == null || mode.isBlank() ? "replace" : mode.trim().toLowerCase();
        BuildTaskQueue.enqueue(() -> executeBuildCommand(
            "fill "
                + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                + to.getX() + " " + to.getY() + " " + to.getZ() + " "
                + blockId + " " + fillMode
        ));
    }

    private static void executeBuildCommand(String command) {
        if (actor == null || !actor.isAlive()) {
            System.out.println("[WARNING] No actor for build command: " + command);
            return;
        }

        MinecraftServer server = actor.getServer();
        if (server == null) {
            return;
        }

        if (!canExecuteBuildCommands(actor)) {
            System.out.println("[WARNING] Actor lacks permission for: " + command);
            return;
        }

        ServerCommandSource source = actor.getCommandSource().withSilent();
        try {
            server.getCommandManager().executeWithPrefix(source, command); // 1.21.4 name of parseAndExecute
        } catch (Exception exception) {
            System.out.println("[ERROR] Build command failed: /" + command + " -> " + exception.getMessage());
        }
    }
    
    
    private static int toInt(Number value) {
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }

    private static String normalizeBlockId(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return "minecraft:stone";
        }
        String trimmed = blockName.trim();
        if (!trimmed.contains(":")) {
            return "minecraft:" + trimmed.toLowerCase();
        }
        return trimmed.toLowerCase();
    }

    private static Block getBlock(String blockName) {
        String id = normalizeBlockId(blockName);
        
        try {
            Block block = Registries.BLOCK.get(Identifier.of(id));
            if (block == Blocks.AIR && !id.equals("minecraft:air") && !id.equals("air")) {
                return Blocks.STONE;
            }
            return block;
        } catch (Exception e) {
            System.out.println("[WARNING] Block not found: " + blockName + ", using STONE");
            return Blocks.STONE;
        }
    }
    
    public static void setOrigin(BlockPos newOrigin) {
        origin = newOrigin;
        System.out.println("[MinecraftAI] Origin set to " + origin);
    }
    
    public static BlockPos getOrigin() {
        return origin;
    }
}