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
        recorder = plan;
    }

    public static void stopRecording() {
        recorder = null;
    }
    
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
        recorder.parts.add(new BuildPlan.Part(normalizeBlockId(blockName),
            toDouble(x) + w / 2, toDouble(y) + h / 2, toDouble(z) + l / 2, w, h, l,
            toDouble(yaw), toDouble(pitch), toDouble(roll)));
    }

    /** Upright cylinder (8-sided): centre (cx, cz), bottom at y. Made of 4 crossed boxes. */
    public static void addCylinder(String blockName, Number cx, Number y, Number cz, Number radius, Number height) {
        double r = Math.abs(toDouble(radius));
        double h = Math.abs(toDouble(height));
        double strip = 2 * r * Math.tan(Math.PI / 8);
        for (int i = 0; i < 4; i++) {
            addCenteredPart(blockName, toDouble(cx), toDouble(y) + h / 2, toDouble(cz), 2 * r, h, strip, i * 45.0);
        }
    }

    /** Ball of radius r around (cx, cy, cz), from 5 stacked cylinder slices. */
    public static void addSphere(String blockName, Number cx, Number cy, Number cz, Number radius) {
        double r = Math.abs(toDouble(radius));
        int layers = 5;
        for (int i = 0; i < layers; i++) {
            double t = -1 + (2.0 * i + 1) / layers;
            double sliceR = r * Math.sqrt(1 - t * t);
            double sliceH = 2 * r / layers;
            double strip = 2 * sliceR * Math.tan(Math.PI / 8);
            for (int k = 0; k < 4; k++) {
                addCenteredPart(blockName, toDouble(cx), toDouble(cy) + t * r, toDouble(cz), 2 * sliceR, sliceH, strip, k * 45.0);
            }
        }
    }

    private static void addCenteredPart(String blockName, double cx, double cy, double cz, double w, double h, double l, double yaw) {
        if (recorder == null || ForbiddenBlockRegistry.isForbidden(blockName)) {
            return;
        }
        recorder.parts.add(new BuildPlan.Part(normalizeBlockId(blockName), cx, cy, cz, w, h, l, yaw, 0, 0));
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
        BuildTaskQueue.enqueue(() -> executeBuildCommand(
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
        BuildTaskQueue.enqueue(() -> executeBuildCommand(
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
        return executePlan(plan, at, turns, tag, title, List.of(), MinecraftAI::executeBuildCommand);
    }

    /**
     * Same, run by the server itself instead of a player, so anyone may place an object from an
     * item (no cheats needed). extraTags go on the hitboxes (e.g. which saved object to drop).
     */
    public static int executePlanAsServer(ServerWorld w, BuildPlan plan, BlockPos at, int turns, String tag, String title, List<String> extraTags) {
        java.util.function.Consumer<String> run = command -> {
            ServerCommandSource source = w.getServer().getCommandSource().withWorld(w).withLevel(4).withSilent();
            try {
                w.getServer().getCommandManager().executeWithPrefix(source, command);
            } catch (Exception exception) {
                System.out.println("[ERROR] Object command failed: /" + command + " -> " + exception.getMessage());
            }
        };
        return executePlan(plan, at, turns, tag, title, extraTags, run);
    }

    private static int executePlan(BuildPlan plan, BlockPos at, int turns, String tag, String title, List<String> extraTags,
                                   java.util.function.Consumer<String> runner) {
        int count = 0;
        if (!plan.parts.isEmpty()) {
            List<String> hitboxes = hitboxCommands(plan, at, turns, tag, title, extraTags);
            BuildTaskQueue.enqueue(() -> hitboxes.forEach(runner));
        }
        for (BuildPlan.Op op : plan.ops) {
            int[] a = BuildPlan.rotate(op.x1, op.z1, turns);
            int[] b = BuildPlan.rotate(op.x2, op.z2, turns);
            String block = rotateBlockState(op.block, turns);
            String command = "set".equals(op.kind)
                ? "setblock " + (at.getX() + a[0]) + " " + (at.getY() + op.y1) + " " + (at.getZ() + a[1]) + " " + block + " replace"
                : "fill " + (at.getX() + a[0]) + " " + (at.getY() + op.y1) + " " + (at.getZ() + a[1]) + " "
                    + (at.getX() + b[0]) + " " + (at.getY() + op.y2) + " " + (at.getZ() + b[1]) + " " + block + " " + op.mode;
            BuildTaskQueue.enqueue(() -> runner.accept(command));
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
                BuildTaskQueue.enqueue(() -> run.forEach(runner));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<String> run = List.copyOf(batch);
            BuildTaskQueue.enqueue(() -> run.forEach(runner));
        }
        return count;
    }

    /** Run one command now as the build actor (used by undo). */
    public static void runNow(String command) {
        executeBuildCommand(command);
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
