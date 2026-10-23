package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.example.network.AiNetworking;
import com.google.gson.Gson;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Placed objects in the world: every display entity of one object (and its invisible
 * interaction hitbox) shares a tag "aib_<id>_<time>". Removing an object removes all of them.
 */
public final class ObjectService {

    public static final double NEARBY_RADIUS = 48;
    private static final double REACH = 8;
    private static final Pattern TAG = Pattern.compile("^aib_[0-9a-z_]{1,60}$");

    /** One placed object, for the nearby list. */
    public static final class Nearby {
        public String tag;
        public String name;
        public double x;
        public double y;
        public double z;
        public double distance;
        public int parts;
    }

    private ObjectService() {
    }

    /** The object tag on an entity, or null. */
    public static String objectTag(Entity entity) {
        for (String t : entity.getCommandTags()) {
            if (!t.equals("aib_obj") && !t.equals("aib_box") && TAG.matcher(t).matches()) {
                return t;
            }
        }
        return null;
    }

    /** Delete key while aiming at an object's hitbox. */
    public static void removeByEntity(ServerPlayerEntity player, int entityId) {
        Entity target = player.getServerWorld().getEntityById(entityId);
        if (!(target instanceof InteractionEntity) || objectTag(target) == null
            || !(target.getCommandTags().contains("aib_obj") || target.getCommandTags().contains("aib_box"))) {
            AiNetworking.sendLog(player, "[History] 이건 AI로 놓은 소품이 아니에요.");
            return;
        }
        if (target.squaredDistanceTo(player) > REACH * REACH) {
            AiNetworking.sendLog(player, "[History] 너무 멀어요. 소품에 더 가까이 가서 지워 주세요.");
            return;
        }
        remove(player, objectTag(target));
    }

    /** "제거" in the nearby list. */
    public static void removeByTag(ServerPlayerEntity player, String tag) {
        if (tag == null || !TAG.matcher(tag).matches()) {
            return;
        }
        remove(player, tag);
    }

    private static void remove(ServerPlayerEntity player, String tag) {
        if (tag == null) {
            return;
        }
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.sendLog(player, "[History] 치트(OP 권한 2)가 켜져 있어야 지울 수 있어요.");
            return;
        }
        ServerWorld world = player.getServerWorld();
        Box area = new Box(player.getBlockPos()).expand(NEARBY_RADIUS + 16);
        List<Entity> found = world.getEntitiesByClass(Entity.class, area, e -> e.getCommandTags().contains(tag));
        String name = null;
        for (Entity e : found) {
            if (e instanceof InteractionEntity && e.hasCustomName()) {
                name = e.getCustomName().getString();
            }
            e.discard();
        }
        if (found.isEmpty()) {
            AiNetworking.sendLog(player, "[History] 지울 소품을 찾지 못했어요 (이미 지워졌을 수 있어요).");
        } else {
            AiNetworking.sendLog(player, "[History] \"" + (name == null || name.isBlank() ? "소품" : name) + "\"을(를) 지웠어요.");
        }
        sendNearby(player);
    }

    /** List objects near the player (also ones placed before hitboxes existed). */
    public static List<Nearby> nearby(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Box area = new Box(player.getBlockPos()).expand(NEARBY_RADIUS);
        Map<String, List<Entity>> groups = new LinkedHashMap<>();
        for (Entity e : world.getEntitiesByType(EntityType.BLOCK_DISPLAY, area, e -> e.getCommandTags().contains("aib"))) {
            String tag = objectTag(e);
            if (tag != null) {
                groups.computeIfAbsent(tag, k -> new ArrayList<>()).add(e);
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (Entity e : world.getEntitiesByClass(InteractionEntity.class, area, e -> e.getCommandTags().contains("aib_obj"))) {
            String tag = objectTag(e);
            if (tag != null && e.hasCustomName()) {
                names.put(tag, e.getCustomName().getString());
            }
        }
        List<Nearby> out = new ArrayList<>();
        for (Map.Entry<String, List<Entity>> g : groups.entrySet()) {
            Vec3d sum = Vec3d.ZERO;
            for (Entity e : g.getValue()) {
                sum = sum.add(e.getPos());
            }
            Vec3d c = sum.multiply(1.0 / g.getValue().size());
            Nearby n = new Nearby();
            n.tag = g.getKey();
            n.name = names.getOrDefault(g.getKey(), "");
            n.x = c.x;
            n.y = c.y;
            n.z = c.z;
            n.distance = Math.sqrt(c.squaredDistanceTo(player.getPos()));
            n.parts = g.getValue().size();
            out.add(n);
        }
        out.sort(Comparator.comparingDouble(n -> n.distance));
        return out.size() > 30 ? new ArrayList<>(out.subList(0, 30)) : out;
    }

    public static void sendNearby(ServerPlayerEntity player) {
        AiNetworking.sendNearby(player, new Gson().toJson(nearby(player)));
    }
}
