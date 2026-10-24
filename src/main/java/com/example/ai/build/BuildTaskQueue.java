package com.example.ai.build;

import com.example.config.AiBuilderConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Placement commands, a few per tick. Each player has their own lane, so two people building
 * at the same time both progress (one step each per turn), undo stops only your own build, and
 * "배치 완료" is told to the right person.
 */
public final class BuildTaskQueue {

    /** Lane for work that belongs to nobody in particular (object items, tests). */
    public static final UUID SERVER = new UUID(0L, 0L);

    private static final class Lane {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Runnable onEmpty;
    }

    private static final Map<UUID, Lane> LANES = new LinkedHashMap<>();
    private static int tickCounter = 0;
    private static MinecraftServer server;

    private BuildTaskQueue() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(activeServer -> {
            server = activeServer;
            tick();
        });
    }

    private static Lane lane(UUID owner) {
        return LANES.computeIfAbsent(owner == null ? SERVER : owner, k -> new Lane());
    }

    public static void enqueue(Runnable task) {
        enqueue(SERVER, task);
    }

    public static void enqueue(UUID owner, Runnable task) {
        if (task == null) {
            return;
        }
        if (AiBuilderConfig.get().ticksPerBlock <= 1) {
            task.run();
            notifyIfIdle(owner == null ? SERVER : owner);
            return;
        }
        lane(owner).tasks.add(task);
    }

    /** Stop everything (all players). */
    public static void clear() {
        LANES.clear();
        tickCounter = 0;
    }

    /** Stop one player's build. */
    public static void clear(UUID owner) {
        LANES.remove(owner == null ? SERVER : owner);
    }

    public static int pendingCount() {
        int n = 0;
        for (Lane lane : LANES.values()) {
            n += lane.tasks.size();
        }
        return n;
    }

    public static void whenEmpty(Runnable callback) {
        whenEmpty(SERVER, callback);
    }

    /** Run {@code callback} once this owner's lane has finished. */
    public static void whenEmpty(UUID owner, Runnable callback) {
        lane(owner).onEmpty = callback;
        notifyIfIdle(owner == null ? SERVER : owner);
    }

    private static void tick() {
        if (LANES.isEmpty()) {
            tickCounter = 0;
            return;
        }
        tickCounter++;
        if (tickCounter < AiBuilderConfig.get().ticksPerBlock) {
            return;
        }
        tickCounter = 0;
        for (UUID owner : new ArrayList<>(LANES.keySet())) {
            Lane lane = LANES.get(owner);
            if (lane == null) {
                continue;
            }
            Runnable task = lane.tasks.poll();
            if (task != null) {
                task.run();
            }
            notifyIfIdle(owner);
        }
    }

    private static void notifyIfIdle(UUID owner) {
        Lane lane = LANES.get(owner);
        if (lane == null || !lane.tasks.isEmpty()) {
            return;
        }
        LANES.remove(owner);
        if (lane.onEmpty != null && server != null) {
            server.execute(lane.onEmpty);
        } else if (lane.onEmpty != null) {
            lane.onEmpty.run();
        }
    }
}
