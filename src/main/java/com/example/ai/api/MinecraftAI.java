package com.example.ai.api;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.util.BlockRotation;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import com.example.ai.build.BuildPlan;
import com.example.ai.build.Spark;
import com.example.ai.build.BuildTaskQueue;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    /** While set, placements are recorded into this plan instead of being run (dry run). */
    private static BuildPlan recorder;

    public static void startRecording(BuildPlan plan) {
        startRecording(plan, false);
    }

    /**
     * fine: equipment, whose smooth shapes may use a 1/32-block grid (twice the detail of
     * objects; equipment is drawn by the client, not as display entities).
     */
    public static void startRecording(BuildPlan plan, boolean fine) {
        recorder = plan;
        canvas = new ShapeCanvas(fine);
        mirrorX = null;
        group = 0;
        groupIds = new java.util.LinkedHashMap<>();
    }

    /** Ends recording; smooth shapes are turned into parts now (see ShapeCanvas). */
    public static void stopRecording() {
        if (recorder != null && canvas != null && !canvas.isEmpty()) {
            int budget = Math.max(20, BuildPlan.MAX_PARTS - recorder.parts.size() - 5);
            recorder.parts.addAll(canvas.toParts(budget));
        }
        recorder = null;
        canvas = null;
        mirrorX = null;
        group = 0;
    }

    /** Current animation group (0 = none) and the groups by name ("name~m" = its mirrored twin). */
    private static int group;
    private static Map<String, Integer> groupIds = new java.util.LinkedHashMap<>();

    private static int groupId(String name) {
        return groupIds.computeIfAbsent(name, k -> groupIds.size() + 1);
    }

    /** The group mirrored pieces of the current group go into. */
    private static int mirrorGroup() {
        if (group == 0 || group == BuildPlan.ARROW_GROUP) {
            return group;
        }
        for (Map.Entry<String, Integer> e : groupIds.entrySet()) {
            if (e.getValue() == group) {
                return groupId(e.getKey() + "~m");
            }
        }
        return 0;
    }

    /** Pieces made until groupEnd() belong to the named group, which animate() can move. */
    public static void groupStart(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("groupStart needs a name, e.g. ai.groupStart('wing')");
        }
        group = groupId(name.trim());
    }

    public static void groupEnd() {
        group = 0;
    }

    /** Bows: pieces made until arrowEnd() are the shape of the arrows it shoots (not drawn on the bow). */
    public static void arrowStart() {
        group = BuildPlan.ARROW_GROUP;
    }

    public static void arrowEnd() {
        group = 0;
    }

    private static final java.util.Set<String> ANIMS = java.util.Set.of("spin", "swing", "bob", "pulse");

    /**
     * Loop a movement of a group: spin (turns per second), swing (back and forth, amount degrees),
     * bob (up and down, amount blocks), pulse (grow and shrink, amount = fraction). A mirrored
     * twin of the group moves as its mirror image.
     */
    public static void animate(String name, String type, Number px, Number py, Number pz, String axis, Number speed, Number amount) {
        if (recorder == null) {
            return;
        }
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String a = axis == null ? "" : axis.trim().toLowerCase(Locale.ROOT);
        if (!ANIMS.contains(t)) {
            throw new IllegalArgumentException("animate type must be one of " + ANIMS + ", got '" + type + "'");
        }
        if (!a.equals("x") && !a.equals("y") && !a.equals("z")) {
            throw new IllegalArgumentException("animate axis must be 'x', 'y' or 'z', got '" + axis + "'");
        }
        if (name == null || !groupIds.containsKey(name.trim())) {
            throw new IllegalArgumentException("animate: no group '" + name + "' (make it with ai.groupStart('" + name + "') first)");
        }
        double s = Math.max(0.05, Math.min(4, Math.abs(toDouble(speed))));
        double amt = toDouble(amount);
        amt = switch (t) {
            case "swing" -> Math.max(-90, Math.min(90, amt));
            case "bob" -> Math.max(-0.4, Math.min(0.4, amt));
            case "pulse" -> Math.max(-0.4, Math.min(0.4, amt));
            default -> amt;
        };
        String key = name.trim();
        recorder.anims.add(new BuildPlan.Anim(groupIds.get(key), t, toDouble(px), toDouble(py), toDouble(pz), a, s, amt));
        Integer twin = groupIds.get(key + "~m");
        if (twin != null) {
            // Mirror image: pivot across the mirror line, turning the other way around y and z.
            double mx = mirrorX == null ? 0 : mirrorX;
            boolean flip = (t.equals("spin") || t.equals("swing")) && !a.equals("x");
            recorder.anims.add(new BuildPlan.Anim(twin, t, 2 * mx - toDouble(px), toDouble(py), toDouble(pz), a,
                s, flip ? -amt : amt));
            if (flip && t.equals("spin")) {
                recorder.anims.get(recorder.anims.size() - 1).speed = -s;
            }
        }
    }

    /**
     * Mark a named spot (e.g. 'clue:diary', 'npc:mayor', 'scene:study') so code using this build
     * can put something there later. Relative coordinates, like placeBlock.
     */
    public static void mark(String name, Number x, Number y, Number z) {
        if (recorder == null) {
            return;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("mark needs a name, e.g. ai.mark('clue:diary', 2, 1, -5)");
        }
        if (recorder.marks.size() < 64) {
            recorder.marks.add(new BuildPlan.Mark(name.trim(), toInt(x), toInt(y), toInt(z)));
        }
    }

    /** Equipment: the order asked for it to be big, so it may go past the slot's usual size. */
    public static void oversize() {
        if (recorder != null) {
            recorder.oversize = true;
        }
    }

    /** Draw a group at full brightness (glowing even in the dark). */
    public static void glow(String name) {
        if (recorder == null) {
            return;
        }
        if (name == null || !groupIds.containsKey(name.trim())) {
            throw new IllegalArgumentException("glow: no group '" + name + "' (make it with ai.groupStart('" + name + "') first)");
        }
        recorder.anims.add(new BuildPlan.Anim(groupIds.get(name.trim()), "glow", 0, 0, 0, "y", 1, 0));
        Integer twin = groupIds.get(name.trim() + "~m");
        if (twin != null) {
            recorder.anims.add(new BuildPlan.Anim(twin, "glow", 0, 0, 0, "y", 1, 0));
        }
    }

    /**
     * A soft glow around the equipment while it is held or worn (drawn by the mod: a see-through,
     * shining shell around every piece), in a colour; strength 0.2-1.
     */
    public static void aura(String color, Number strength) {
        if (recorder == null) {
            return;
        }
        int rgb;
        try {
            rgb = Integer.parseInt((color == null ? "ffffff" : color).trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("colour must look like '#ffdd66', got '" + color + "'");
        }
        recorder.aura = new BuildPlan.Aura(rgb, Math.max(0.2, Math.min(1, Math.abs(toDouble(strength)))));
    }

    /**
     * A colour-dot effect: when = 'attack' (weapons: when swung; bows: where the arrow lands),
     * 'idle' (while held or worn) or 'trail' (bows: behind the arrow). See Spark for the settings.
     */
    public static void effect(String when, java.util.Map<?, ?> settings) {
        if (recorder == null) {
            return;
        }
        Spark spark = Spark.fromMap(settings);
        String w = when == null ? "" : when.trim().toLowerCase(Locale.ROOT);
        switch (w) {
            case "attack", "hit", "land" -> {
                if (recorder.fx.attack.size() < 3) {
                    recorder.fx.attack.add(spark);
                }
            }
            case "idle", "held", "worn" -> {
                if (recorder.fx.idle.size() < 2) {
                    recorder.fx.idle.add(spark);
                }
            }
            case "trail" -> {
                recorder.fx.trail.clear();
                recorder.fx.trail.add(spark);
            }
            default -> throw new IllegalArgumentException("effect: when must be 'attack', 'idle' or 'trail', got '" + when + "'");
        }
    }

    private static ShapeCanvas canvas;
    /** While set, every object piece is also made mirrored across x = mirrorX. */
    private static Double mirrorX;
    
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

    // ---------------------------------------------------------------- small objects (block_display)

    /** A box-shaped piece of an object: min corner (x, y, z), size (sx, sy, sz); decimals allowed. */
    public static void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz) {
        addPart(blockName, x, y, z, sx, sy, sz, 0, 0, 0);
    }

    /** Same, turned yaw degrees around the vertical axis through its centre. */
    public static void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz, Number yaw) {
        addPart(blockName, x, y, z, sx, sy, sz, yaw, 0, 0);
    }

    /** Same, turned by yaw (vertical axis), then pitch (X axis), then roll (Z axis), in degrees. */
    public static void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz,
                               Number yaw, Number pitch, Number roll) {
        if (recorder == null || blockName == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }
        double w = Math.abs(toDouble(sx));
        double h = Math.abs(toDouble(sy));
        double l = Math.abs(toDouble(sz));
        String block = normalizeBlockId(blockName);
        double cx = toDouble(x) + w / 2;
        BuildPlan.Part part = new BuildPlan.Part(block, cx, toDouble(y) + h / 2, toDouble(z) + l / 2, w, h, l,
            toDouble(yaw), toDouble(pitch), toDouble(roll));
        part.group = group;
        recorder.parts.add(part);
        if (mirrorX != null && Math.abs(cx - mirrorX) > 1e-6) {
            BuildPlan.Part twin = new BuildPlan.Part(mirrorBlockState(block), 2 * mirrorX - cx, part.y, part.z, w, h, l,
                -part.yaw, part.pitch, -part.roll);
            twin.group = group == 0 ? 0 : mirrorGroup();
            recorder.parts.add(twin);
        }
    }

    /** Left-right mirror of a block state: facing=east <-> west. */
    static String mirrorBlockState(String block) {
        if (block == null || !block.contains("[")) {
            return block;
        }
        if (block.contains("facing=east")) {
            return block.replace("facing=east", "facing=west");
        }
        if (block.contains("facing=west")) {
            return block.replace("facing=west", "facing=east");
        }
        return block;
    }

    // Smooth shapes: drawn on a fine grid when recording ends (ShapeCanvas).

    private static void shape(String blockName, ShapeCanvas.Shape shape) {
        if (recorder == null || canvas == null || blockName == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }
        canvas.add(normalizeBlockId(blockName), shape, mirrorX, group, mirrorX != null && group != 0 ? mirrorGroup() : 0);
    }

    /** Upright cylinder: centre (cx, cz), bottom at y. */
    public static void addCylinder(String blockName, Number cx, Number y, Number cz, Number radius, Number height) {
        double r = toDouble(radius);
        shape(blockName, ShapeCanvas.frustum(toDouble(cx), toDouble(y), toDouble(cz), r, r, toDouble(height)));
    }

    /** Ball of radius r around (cx, cy, cz). */
    public static void addSphere(String blockName, Number cx, Number cy, Number cz, Number radius) {
        double r = toDouble(radius);
        shape(blockName, ShapeCanvas.ellipsoid(toDouble(cx), toDouble(cy), toDouble(cz), r, r, r));
    }

    public static void addEllipsoid(String blockName, Number cx, Number cy, Number cz, Number rx, Number ry, Number rz) {
        shape(blockName, ShapeCanvas.ellipsoid(toDouble(cx), toDouble(cy), toDouble(cz), toDouble(rx), toDouble(ry), toDouble(rz)));
    }

    /** Upright cone, point on top. */
    public static void addCone(String blockName, Number cx, Number y, Number cz, Number radius, Number height) {
        shape(blockName, ShapeCanvas.frustum(toDouble(cx), toDouble(y), toDouble(cz), toDouble(radius), 0, toDouble(height)));
    }

    /** Upright cut cone: bottom radius, top radius. */
    public static void addCone(String blockName, Number cx, Number y, Number cz, Number bottomRadius, Number topRadius, Number height) {
        shape(blockName, ShapeCanvas.frustum(toDouble(cx), toDouble(y), toDouble(cz), toDouble(bottomRadius), toDouble(topRadius), toDouble(height)));
    }

    public static void addCapsule(String blockName, Number x1, Number y1, Number z1, Number x2, Number y2, Number z2, Number radius) {
        addCapsule(blockName, x1, y1, z1, x2, y2, z2, radius, radius);
    }

    /** Rounded rod from (x1,y1,z1) with radius r1 to (x2,y2,z2) with radius r2. */
    public static void addCapsule(String blockName, Number x1, Number y1, Number z1, Number x2, Number y2, Number z2,
                                  Number r1, Number r2) {
        List<double[]> points = List.of(
            new double[] { toDouble(x1), toDouble(y1), toDouble(z1) },
            new double[] { toDouble(x2), toDouble(y2), toDouble(z2) });
        shape(blockName, ShapeCanvas.tube(points, toDouble(r1), toDouble(r2)));
    }

    public static void addTube(String blockName, List<?> points, Number radius) {
        addTube(blockName, points, radius, radius);
    }

    /** Smooth curve through 2-16 points [[x,y,z], ...], radius r1 at the start to r2 at the end. */
    public static void addTube(String blockName, List<?> points, Number r1, Number r2) {
        List<double[]> list = new ArrayList<>();
        if (points != null) {
            for (Object o : points) {
                if (o instanceof List<?> p && p.size() >= 3 && list.size() < 16) {
                    list.add(new double[] { num(p.get(0)), num(p.get(1)), num(p.get(2)) });
                }
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("addTube needs points like [[x, y, z], [x, y, z]]");
        }
        if (list.size() == 1) {
            list.add(list.get(0));
        }
        shape(blockName, ShapeCanvas.tube(ShapeCanvas.curve(list), toDouble(r1), toDouble(r2)));
    }

    /** Box from min corner (x, y, z) with size, edges rounded by radius (0 = sharp), on the fine grid. */
    public static void addRoundBox(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz, Number radius) {
        shape(blockName, ShapeCanvas.roundBox(toDouble(x), toDouble(y), toDouble(z), toDouble(sx), toDouble(sy), toDouble(sz), toDouble(radius)));
    }

    /** From now on every object piece is also made mirrored across x = 0 (or x = centerX). */
    public static void mirrorOn() {
        mirrorOn(0);
    }

    public static void mirrorOn(Number centerX) {
        mirrorX = toDouble(centerX);
    }

    public static void mirrorOff() {
        mirrorX = null;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static double toDouble(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    /** /summon command for one part at {@code at}, the object turned {@code turns} quarter turns clockwise. */
    static String summonCommand(BuildPlan.Part part, BlockPos at, int turns, String tag) {
        double r = Math.sqrt(part.sx * part.sx + part.sy * part.sy + part.sz * part.sz) / 2;
        return summonCommand(part, at, turns, tag, part.y - r);
    }

    /**
     * @param floor relative height the entity may not go below: the bottom of the whole object.
     *              The game lights a display entity by the block it stands in, so an entity
     *              pushed into the ground would draw its part black.
     */
    static String summonCommand(BuildPlan.Part part, BlockPos at, int turns, String tag, double floor) {
        double cx = part.x;
        double cz = part.z;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            double t = cx;
            cx = -cz;
            cz = t;
        }
        Quaternionf q = new Quaternionf()
            .rotateY((float) Math.toRadians(-90.0 * turns))
            .rotateY((float) Math.toRadians(part.yaw))
            .rotateX((float) Math.toRadians(part.pitch))
            .rotateZ((float) Math.toRadians(part.roll));
        Vector3f half = q.transform(new Vector3f((float) part.sx / 2, (float) part.sy / 2, (float) part.sz / 2));
        String block = rotateBlockState(part.block, turns);
        // The game hides a display entity when its culling box (width x height, standing on the
        // entity position) is off screen; the default box is empty, so a big part would vanish
        // as soon as its centre left the screen. Stand the entity at the bottom of a box that
        // holds the whole part (any rotation), but not below the object's bottom, and lift the
        // model back up by the same amount.
        double r = Math.sqrt(part.sx * part.sx + part.sy * part.sy + part.sz * part.sz) / 2;
        double base = Math.min(part.y, Math.max(part.y - r, floor));
        double drop = part.y - base;
        double boxH = part.y + r - base;
        return String.format(Locale.ROOT,
            "summon minecraft:block_display %.4f %.4f %.4f {block_state:%s,transformation:{left_rotation:[%.6ff,%.6ff,%.6ff,%.6ff],"
                + "right_rotation:[0f,0f,0f,1f],translation:[%.4ff,%.4ff,%.4ff],scale:[%.4ff,%.4ff,%.4ff]},"
                + "width:%.4ff,height:%.4ff,Tags:[\"aib\",\"%s\"]}",
            at.getX() + cx, at.getY() + base, at.getZ() + cz, blockStateNbt(block),
            q.x, q.y, q.z, q.w, -half.x, -half.y + drop, -half.z, part.sx, part.sy, part.sz, 2 * r, boxH, tag);
    }

    /** World-aligned box around all parts: {minX, minY, minZ, maxX, maxY, maxZ}, relative to at. */
    public static double[] objectBounds(BuildPlan plan, int turns) {
        double[] b = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (double[] p : partBoxes(plan, turns)) {
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], p[i]);
                b[i + 3] = Math.max(b[i + 3], p[i + 3]);
            }
        }
        return b;
    }

    /** One world-aligned box per part (turned), relative to at. */
    public static List<double[]> partBoxes(BuildPlan plan, int turns) {
        List<double[]> boxes = new ArrayList<>();
        for (BuildPlan.Part part : plan.parts) {
            double cx = part.x;
            double cz = part.z;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) {
                double t = cx;
                cx = -cz;
                cz = t;
            }
            Quaternionf q = new Quaternionf()
                .rotateY((float) Math.toRadians(-90.0 * turns))
                .rotateY((float) Math.toRadians(part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
            org.joml.Matrix3f m = new org.joml.Matrix3f().rotation(q);
            double hx = part.sx / 2, hy = part.sy / 2, hz = part.sz / 2;
            double ex = Math.abs(m.m00()) * hx + Math.abs(m.m10()) * hy + Math.abs(m.m20()) * hz;
            double ey = Math.abs(m.m01()) * hx + Math.abs(m.m11()) * hy + Math.abs(m.m21()) * hz;
            double ez = Math.abs(m.m02()) * hx + Math.abs(m.m12()) * hy + Math.abs(m.m22()) * hz;
            boxes.add(new double[] { cx - ex, part.y - ey, cz - ez, cx + ex, part.y + ey, cz + ez });
        }
        return boxes;
    }

    /** Most solid boxes one object gets (before they are cut into squares). */
    public static final int MAX_SOLID_BOXES = 10;

    /**
     * The object's solid hitboxes: its parts merged into a few boxes that follow its shape (a
     * chair gets legs, seat and back), each cut into square pieces because a hitbox entity has a
     * square footprint. They block movement, can be stood on, and are what the player aims at
     * to break or remove the object. The first one carries the title ("aib_obj").
     */
    static List<String> hitboxCommands(BuildPlan plan, BlockPos at, int turns, String tag, String title, List<String> extraTags) {
        List<double[]> squares = new ArrayList<>();
        for (double[] box : BuildPlan.mergeBoxes(partBoxes(plan, turns), MAX_SOLID_BOXES)) {
            squares.addAll(BuildPlan.squares(box));
        }
        String json = new com.google.gson.Gson().toJson(title == null ? "" : title);
        String snbt = json.replace("\\", "\\\\").replace("'", "\\'");
        StringBuilder extra = new StringBuilder();
        for (String t : extraTags) {
            extra.append(",\"").append(t).append('"');
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < squares.size(); i++) {
            double[] s = squares.get(i); // {centreX, bottomY, centreZ, width, height}
            boolean primary = i == 0;
            out.add(String.format(Locale.ROOT,
                "summon %s %.4f %.4f %.4f {width:%.4ff,height:%.4ff,response:1b,Tags:[\"aib\",\"aib_box\",\"%s\"%s%s]%s}",
                HITBOX_ID, at.getX() + s[0], at.getY() + s[1], at.getZ() + s[2], s[3], s[4], tag,
                primary ? ",\"aib_obj\"" : "", extra, primary ? ",CustomName:'" + snbt + "'" : ""));
        }
        return out;
    }

    /** Id of the solid hitbox entity (registered in AibContent). */
    public static final String HITBOX_ID = "minecraft-ai-build-assistant:hitbox";

    /** minecraft:oak_stairs[facing=east] -> {Name:"minecraft:oak_stairs",Properties:{facing:"east"}} */
    static String blockStateNbt(String block) {
        int open = block.indexOf('[');
        String name = open < 0 ? block : block.substring(0, open);
        StringBuilder nbt = new StringBuilder("{Name:\"").append(name).append('"');
        if (open >= 0 && block.endsWith("]")) {
            String inner = block.substring(open + 1, block.length() - 1);
            List<String> props = new ArrayList<>();
            for (String pair : inner.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    props.add(pair.substring(0, eq).trim() + ":\"" + pair.substring(eq + 1).trim() + "\"");
                }
            }
            if (!props.isEmpty()) {
                nbt.append(",Properties:{").append(String.join(",", props)).append('}');
            }
        }
        return nbt.append('}').toString();
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
        if (recorder != null) {
            int x = pos.getX() - origin.getX();
            int y = pos.getY() - origin.getY();
            int z = pos.getZ() - origin.getZ();
            recorder.ops.add(new BuildPlan.Op("set", x, y, z, x, y, z, blockId, "replace"));
            return;
        }
        ServerPlayerEntity who = actor;
        ServerWorld where = world;
        BuildTaskQueue.enqueue(ownerOf(who), () -> executeBuildCommandAs(who, where,
            "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + blockId + " replace"
        ));
    }

    private static void scheduleFill(BlockPos from, BlockPos to, String blockName, String mode) {
        String blockId = normalizeBlockId(blockName);
        String fillMode = mode == null || mode.isBlank() ? "replace" : mode.trim().toLowerCase();
        if (recorder != null) {
            recorder.ops.add(new BuildPlan.Op("fill",
                from.getX() - origin.getX(), from.getY() - origin.getY(), from.getZ() - origin.getZ(),
                to.getX() - origin.getX(), to.getY() - origin.getY(), to.getZ() - origin.getZ(),
                blockId, fillMode));
            return;
        }
        ServerPlayerEntity who = actor;
        ServerWorld where = world;
        BuildTaskQueue.enqueue(ownerOf(who), () -> executeBuildCommandAs(who, where,
            "fill "
                + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                + to.getX() + " " + to.getY() + " " + to.getZ() + " "
                + blockId + " " + fillMode
        ));
    }

    /**
     * Queue a recorded plan at {@code at}, turned {@code turns} quarter turns clockwise (so a
     * saved build faces the way the player faces now). Returns the number of commands queued.
     */
    public static int executePlan(BuildPlan plan, BlockPos at, int turns, String tag) {
        return executePlan(plan, at, turns, tag, "");
    }

    /**
     * Same, and for objects also an invisible interaction entity around the whole object so the
     * crosshair can target it (display entities have no hitbox). It carries the object's title.
     */
    public static int executePlan(BuildPlan plan, BlockPos at, int turns, String tag, String title) {
        // Remember who builds and where now: other players may start their own builds before this
        // one has finished, and every queued command must still run as this player in this world.
        ServerPlayerEntity who = actor;
        ServerWorld where = world;
        return executePlan(plan, at, turns, tag, title, List.of(), command -> executeBuildCommandAs(who, where, command), ownerOf(who));
    }

    /**
     * Same, run by the server itself instead of a player, so anyone may place an object from an
     * item (no cheats needed). extraTags go on the hitboxes (e.g. which saved object to drop).
     */
    public static int executePlanAsServer(ServerWorld w, BuildPlan plan, BlockPos at, int turns, String tag, String title, List<String> extraTags) {
        return executePlanAsServer(w, plan, at, turns, tag, title, extraTags, BuildTaskQueue.SERVER);
    }

    /** As the server, in the given placement lane (so other mods can wait for their own builds). */
    public static int executePlanAsServer(ServerWorld w, BuildPlan plan, BlockPos at, int turns, String tag, String title,
                                          List<String> extraTags, java.util.UUID lane) {
        java.util.function.Consumer<String> run = command -> {
            ServerCommandSource source = w.getServer().getCommandSource().withWorld(w).withLevel(4).withSilent();
            try {
                w.getServer().getCommandManager().executeWithPrefix(source, command);
            } catch (Exception exception) {
                System.out.println("[ERROR] Object command failed: /" + command + " -> " + exception.getMessage());
            }
        };
        return executePlan(plan, at, turns, tag, title, extraTags, run, lane);
    }

    private static java.util.UUID ownerOf(ServerPlayerEntity player) {
        return player == null ? BuildTaskQueue.SERVER : player.getUuid();
    }

    private static int executePlan(BuildPlan plan, BlockPos at, int turns, String tag, String title, List<String> extraTags,
                                   java.util.function.Consumer<String> runner, java.util.UUID owner) {
        int count = 0;
        if (!plan.parts.isEmpty()) {
            List<String> hitboxes = hitboxCommands(plan, at, turns, tag, title, extraTags);
            BuildTaskQueue.enqueue(owner, () -> hitboxes.forEach(runner));
        }
        for (BuildPlan.Op op : plan.ops) {
            int[] a = BuildPlan.rotate(op.x1, op.z1, turns);
            int[] b = BuildPlan.rotate(op.x2, op.z2, turns);
            String block = rotateBlockState(op.block, turns);
            String command = "set".equals(op.kind)
                ? "setblock " + (at.getX() + a[0]) + " " + (at.getY() + op.y1) + " " + (at.getZ() + a[1]) + " " + block + " replace"
                : "fill " + (at.getX() + a[0]) + " " + (at.getY() + op.y1) + " " + (at.getZ() + a[1]) + " "
                    + (at.getX() + b[0]) + " " + (at.getY() + op.y2) + " " + (at.getZ() + b[1]) + " " + block + " " + op.mode;
            BuildTaskQueue.enqueue(owner, () -> runner.accept(command));
            count++;
        }
        // Object parts appear a few at a time so a model pops in quickly but visibly.
        List<String> batch = new ArrayList<>();
        double floor = plan.parts.isEmpty() ? 0 : plan.partBounds()[1];
        for (BuildPlan.Part part : plan.parts) {
            batch.add(summonCommand(part, at, turns, tag, floor));
            count++;
            if (batch.size() == 8) {
                List<String> run = List.copyOf(batch);
                BuildTaskQueue.enqueue(owner, () -> run.forEach(runner));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<String> run = List.copyOf(batch);
            BuildTaskQueue.enqueue(owner, () -> run.forEach(runner));
        }
        return count;
    }

    /** Run one command now as the build actor (used by undo). */
    public static void runNow(String command) {
        executeBuildCommandAs(actor, world, command);
    }

    /** Turn stairs, doors, logs... with the structure. Unknown or stateless blocks pass through. */
    static String rotateBlockState(String block, int turns) {
        int t = Math.floorMod(turns, 4);
        if (t == 0 || block == null) {
            return block;
        }
        try {
            BlockState state = BlockArgumentParser.block(Registries.BLOCK, block, false).blockState();
            BlockRotation rotation = switch (t) {
                case 1 -> BlockRotation.CLOCKWISE_90;
                case 2 -> BlockRotation.CLOCKWISE_180;
                default -> BlockRotation.COUNTERCLOCKWISE_90;
            };
            BlockState rotated = state.rotate(rotation);
            return rotated == state ? block : BlockArgumentParser.stringifyBlockState(rotated);
        } catch (Exception exception) {
            return block;
        }
    }

    /**
     * Run one build command as {@code who} (whose permission was checked when the build started)
     * in {@code where}. If they have left meanwhile, the server finishes their build in that world.
     */
    private static void executeBuildCommandAs(ServerPlayerEntity who, ServerWorld where, String command) {
        MinecraftServer server = who != null ? who.getServer() : where != null ? where.getServer() : null;
        if (server == null) {
            System.out.println("[WARNING] No world for build command: " + command);
            return;
        }
        ServerCommandSource source;
        if (who != null && !who.isRemoved() && who.getServer() != null) {
            if (!canExecuteBuildCommands(who)) {
                System.out.println("[WARNING] Actor lacks permission for: " + command);
                return;
            }
            source = who.getCommandSource().withSilent();
            if (where != null) {
                source = source.withWorld(where); // they may have changed dimension since
            }
        } else if (where != null) {
            source = server.getCommandSource().withWorld(where).withLevel(2).withSilent();
        } else {
            return;
        }
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
