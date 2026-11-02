package com.example.ai;

import com.example.network.AiNetworkPayloads;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The shared 작업 일지: one entry per request to the AI 공방 ("Jo님이 장비(검)를 요청했어요"),
 * holding the messages that request produced. Kept with the world and sent to every player, so
 * everyone sees everyone's work; the list shows one line per request and the details on a click.
 */
public final class WorkJournal {

    public static final String WORKING = "working";
    public static final String DONE = "done";
    public static final String FAILED = "failed";
    public static final String STOPPED = "stopped";

    /** Kept requests (the oldest go first). */
    public static final int MAX_JOBS = 100;
    static final int MAX_LINES = 120;
    static final int MAX_LINE = 400;

    private static final Gson GSON = new Gson();

    /** One request. */
    public static final class Job {
        public String id = "";
        public String player = "";
        /** "build", "object" or "gear". */
        public String kind = "build";
        /** Gear: its kind id (sword, axe...). */
        public String gear = "";
        public String prompt = "";
        public String fxHit = "";
        public String fxIdle = "";
        public String motion = "";
        /** The AI that was asked (when the request was sent); empty in older entries. */
        public String provider = "";
        public String model = "";
        public long time;
        public String status = WORKING;
        public List<String> lines = new ArrayList<>();

        /** "Jo님이 장비(검)를 요청했어요" */
        public String summary() {
            String what = switch (kind == null ? "" : kind) {
                case "gear" -> "장비(" + com.example.content.GearKind.of(gear).korean + ")를";
                case "object" -> "소품을";
                default -> "건물을";
            };
            return (player == null || player.isEmpty() ? "누군가" : player) + "님이 " + what + " 요청했어요";
        }
    }

    /** What a JournalS2C payload carries: everything (joining) or one changed request. */
    public static final class Update {
        public boolean reset;
        public Job job;
    }

    private static final List<Job> JOBS = new ArrayList<>();
    private static final Map<UUID, Job> CURRENT = new HashMap<>();
    private static MinecraftServer loadedFor;

    private WorkJournal() {
    }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("ai_builder").resolve("journal.json");
    }

    /** The kept journal of this server's world (read once per world). */
    private static void ensureLoaded(MinecraftServer server) {
        if (server == null || loadedFor == server) {
            return;
        }
        loadedFor = server;
        JOBS.clear();
        CURRENT.clear();
        try {
            Path f = file(server);
            if (Files.isRegularFile(f)) {
                Job[] kept = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Job[].class);
                if (kept != null) {
                    for (Job j : kept) {
                        if (j != null) {
                            if (WORKING.equals(j.status)) {
                                j.status = STOPPED; // the world closed while it was being made
                            }
                            if (j.lines == null) {
                                j.lines = new ArrayList<>();
                            }
                            JOBS.add(j);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.out.println("[AI] Could not read the work journal: " + exception.getMessage());
        }
    }

    private static void save(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Path f = file(server);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(JOBS), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.out.println("[AI] Could not write the work journal: " + exception.getMessage());
        }
    }

    /** A request was sent to the AI 공방. mode: "build", "object" or "gear:<kind>". */
    public static synchronized Job start(ServerPlayerEntity player, String prompt, String mode, String fxHit, String fxIdle, String motion) {
        MinecraftServer server = player.getServer();
        ensureLoaded(server);
        Job previous = CURRENT.remove(player.getUuid());
        if (previous != null && WORKING.equals(previous.status)) {
            previous.status = STOPPED; // a new request before the last one finished
            broadcast(server, previous);
        }
        Job job = new Job();
        job.time = System.currentTimeMillis();
        job.id = job.time + "_" + player.getUuid().toString().substring(0, 8);
        job.player = player.getName().getString();
        String m = mode == null ? "build" : mode;
        job.kind = m.startsWith("gear:") ? "gear" : "object".equals(m) ? "object" : "build";
        job.gear = m.startsWith("gear:") ? m.substring(5) : "";
        job.prompt = cut(prompt);
        job.fxHit = cut(fxHit);
        job.fxIdle = cut(fxIdle);
        job.motion = motion == null ? "" : motion;
        com.example.config.AiBuilderConfig config = com.example.config.AiBuilderConfig.get();
        job.provider = config.provider == null ? "" : config.provider.getDisplayName();
        job.model = config.modelName == null ? "" : config.modelName;
        JOBS.add(job);
        while (JOBS.size() > MAX_JOBS) {
            JOBS.remove(0);
        }
        CURRENT.put(player.getUuid(), job);
        save(server);
        broadcast(server, job);
        return job;
    }

    /** A message for a player: goes into their running request, which it may finish. */
    public static synchronized void log(ServerPlayerEntity player, String message) {
        if (player == null || message == null) {
            return;
        }
        Job job = CURRENT.get(player.getUuid());
        if (job == null) {
            return;
        }
        if (job.lines.size() < MAX_LINES) {
            job.lines.add(cut(message.replace("\r", "")));
        }
        String end = endOf(message);
        if (end != null) {
            job.status = end;
            CURRENT.remove(player.getUuid());
            save(player.getServer());
        }
        broadcast(player.getServer(), job);
    }

    /** DONE or FAILED when this message ends a request, else null. */
    static String endOf(String message) {
        String m = message.stripLeading();
        if (m.startsWith("[AI] 완성!")) {
            return DONE;
        }
        if (m.startsWith("[AI] Error") || m.startsWith("[AI] Script error") || m.startsWith("[AI] Gave up")
            || m.startsWith("[Security Alert]") || m.startsWith("§c[Security Alert]")) {
            return FAILED;
        }
        return null;
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_LINE ? s.substring(0, MAX_LINE) + "…" : s;
    }

    /** Joining: everything kept, one request per packet (after a reset). */
    public static synchronized void sendAllTo(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, AiNetworkPayloads.JournalS2CPayload.ID)) {
            return;
        }
        ensureLoaded(player.getServer());
        Update reset = new Update();
        reset.reset = true;
        ServerPlayNetworking.send(player, new AiNetworkPayloads.JournalS2CPayload(gzip(GSON.toJson(reset))));
        for (Job job : JOBS) {
            ServerPlayNetworking.send(player, new AiNetworkPayloads.JournalS2CPayload(encode(job)));
        }
    }

    private static void broadcast(MinecraftServer server, Job job) {
        if (server == null) {
            return;
        }
        byte[] data = encode(job);
        Runnable send = () -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(p, AiNetworkPayloads.JournalS2CPayload.ID)) {
                    ServerPlayNetworking.send(p, new AiNetworkPayloads.JournalS2CPayload(data));
                }
            }
        };
        if (server.isOnThread()) {
            send.run();
        } else {
            server.execute(send);
        }
    }

    private static byte[] encode(Job job) {
        Update u = new Update();
        u.job = job;
        return gzip(GSON.toJson(u));
    }

    public static byte[] gzip(String json) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Update decode(byte[] data) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return GSON.fromJson(new String(in.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8), Update.class);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** Tests: the kept requests. */
    public static synchronized List<Job> jobs() {
        return List.copyOf(JOBS);
    }
}
