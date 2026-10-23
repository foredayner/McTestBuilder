package com.example.client;

import com.example.ai.AiCodeSanitizer;
import com.example.ai.AiController;
import com.example.ai.AiHttpService;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.example.ai.build.BuildTaskQueue;
import com.example.ai.build.UndoService;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import com.example.gametest.Demo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Model benchmark: real AI calls through the mod's own pipeline (prompt -> review -> dry run ->
 * plan check -> automatic fix retries), then the accepted design is placed and photographed.
 *
 * <p>Runs only when ANTHROPIC_API_KEY is set (the "bench" workflow); normal CI skips it. It first
 * tries the way a player can use Claude today (provider "OpenAI API" pointed at Anthropic's
 * OpenAI-compatible endpoint). If that endpoint refuses, it notes why and measures the model
 * through the native Messages API instead.
 */
public class AibModelBench implements FabricClientGameTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path OUT = Path.of("bench");
    private static final int RETRIES = 2;

    private record Case(String id, String prompt, boolean object) {
    }

    private static final List<Case> CASES = List.of(
        new Case("b1_cabin", "참나무와 조약돌로 만든 작은 오두막, 굴뚝과 현관 계단", false),
        new Case("b2_house", "거실, 부엌, 침실 두 개가 있는 2층 집", false),
        new Case("o1_apple", "빨간 사과", true),
        new Case("o2_duck", "모자를 쓴 노란 오리", true),
        new Case("o3_unicorn", "흰 유니콘", true),
        new Case("o4_golem", "이끼 낀 돌 골렘", true),
        new Case("o5_chair", "나무 의자", true),
        new Case("o6_lamp", "책상 스탠드 램프", true)
    );

    /** One AI call. */
    static final class Call {
        String route;
        int status;
        long ms;
        int inputTokens;
        int outputTokens;
        String error;
        String failure;
    }

    static final class Result {
        String model;
        String id;
        String prompt;
        boolean object;
        boolean success;
        int calls;
        String finalFailure;
        String summary;
        long totalMs;
        int inputTokens;
        int outputTokens;
        List<Call> attempts = new ArrayList<>();
    }

    private String route = "compat";
    private String compatError;

    @Override
    public void runTest(ClientGameTestContext context) {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            return; // not a bench run
        }
        String models = System.getenv().getOrDefault("AIB_BENCH_MODELS", "claude-haiku-5-5");
        try {
            Files.createDirectories(OUT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AiBuilderConfig config = AiBuilderConfig.get();
        config.provider = AiProvider.OPENAI;
        config.apiUrl = "https://api.anthropic.com/v1";
        config.apiKey = key.trim();
        config.openAiWebSearchEnabled = false;
        config.autoRetries = RETRIES;

        List<Result> results = new ArrayList<>();
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE))
                .create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runCommand("time set noon");
            game.getServer().runCommand("weather clear");
            game.getServer().runCommand("gamerule doDaylightCycle false");
            game.getServer().runCommand("gamerule doMobSpawning false");
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(p.getGameProfile(), 4, false));
            });
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 18");
            context.runOnClient(client -> client.options.hudHidden = true);
            context.waitTicks(5);
            // Remember where the player stands so every case starts from the same spot and facing.
            double[] home = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                return new double[] { p.getX(), p.getY(), p.getZ() };
            });

            for (String model : models.split(",")) {
                model = model.trim();
                if (model.isEmpty()) {
                    continue;
                }
                config.modelName = model;
                for (Case c : CASES) {
                    Result r = runCase(context, game, model, c, key.trim());
                    results.add(r);
                    write(results);
                    if (r.success) {
                        photograph(context, game, model, c, r, home);
                    }
                    String tp = String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 18", home[0], home[1], home[2]);
                    game.getServer().runCommand("gamemode creative @a");
                    game.getServer().runCommand(tp);
                    context.waitTicks(3);
                }
            }
        }
        write(results);
    }

    private Result runCase(ClientGameTestContext context, TestSingleplayerContext game, String model, Case c, String key) {
        Result r = new Result();
        r.model = model;
        r.id = c.id();
        r.prompt = c.prompt();
        r.object = c.object();
        String base = game.getServer().computeOnServer(server ->
            AiController.promptFor(server.getPlayerManager().getPlayerList().get(0), c.prompt(), c.object()));
        String prompt = base;
        long start = System.currentTimeMillis();
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            Call call = new Call();
            r.attempts.add(call);
            r.calls++;
            String code = ask(prompt, model, key, call);
            r.inputTokens += call.inputTokens;
            r.outputTokens += call.outputTokens;
            if (code == null) {
                r.finalFailure = "API: " + call.error;
                break;
            }
            save(model, c.id() + "_try" + attempt + ".groovy", code);
            AiController.Evaluation eval = game.getServer().computeOnServer(server ->
                AiController.evaluate(server.getPlayerManager().getPlayerList().get(0), code));
            if (eval.ok()) {
                r.success = true;
                r.summary = eval.plan().summary();
                BuildRecord record = BuildRecord.create(c.prompt(), "BENCH", model, eval.plan());
                save(model, c.id() + ".record.json", record.toJson());
                break;
            }
            call.failure = eval.failure();
            r.finalFailure = eval.failure();
            prompt = AiController.fixPrompt(base, code, eval.failure());
        }
        r.totalMs = System.currentTimeMillis() - start;
        return r;
    }

    /** Send one prompt; returns the cleaned script, or null with call.error set. */
    private String ask(String prompt, String model, String key, Call call) {
        long t = System.currentTimeMillis();
        try {
            if ("compat".equals(route)) {
                call.route = "openai-compatible";
                HttpRequest request = AiHttpService.buildRequest(prompt, false);
                HttpResponse<String> response = AiHttpService.client().send(request, HttpResponse.BodyHandlers.ofString());
                call.status = response.statusCode();
                call.ms = System.currentTimeMillis() - t;
                if (response.statusCode() / 100 == 2) {
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (root.has("usage") && root.get("usage").isJsonObject()) {
                        JsonObject u = root.getAsJsonObject("usage");
                        call.inputTokens = u.has("prompt_tokens") ? u.get("prompt_tokens").getAsInt() : 0;
                        call.outputTokens = u.has("completion_tokens") ? u.get("completion_tokens").getAsInt() : 0;
                    }
                    return AiCodeSanitizer.sanitize(AiHttpService.extractResponseBody(response.body(), AiProvider.OPENAI));
                }
                // The compatible endpoint refused: note it once, then measure through the native API.
                compatError = "HTTP " + response.statusCode() + ": " + clip(response.body(), 600);
                route = "native";
                t = System.currentTimeMillis();
            }
            call.route = "native-messages";
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 16000);
            JsonArray messages = new JsonArray();
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", prompt);
            messages.add(user);
            body.add("messages", messages);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .header("content-type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(300))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            // Keys that are not scoped to one workspace must say which workspace to bill.
            String workspace = System.getenv("ANTHROPIC_WORKSPACE_ID");
            if (workspace != null && !workspace.isBlank()) {
                builder.header("anthropic-workspace-id", workspace.trim());
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = AiHttpService.client().send(request, HttpResponse.BodyHandlers.ofString());
            call.status = response.statusCode();
            call.ms = System.currentTimeMillis() - t;
            if (response.statusCode() / 100 != 2) {
                call.error = "HTTP " + response.statusCode() + ": " + clip(response.body(), 600);
                return null;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (root.has("usage")) {
                JsonObject u = root.getAsJsonObject("usage");
                call.inputTokens = u.has("input_tokens") ? u.get("input_tokens").getAsInt() : 0;
                call.outputTokens = u.has("output_tokens") ? u.get("output_tokens").getAsInt() : 0;
            }
            StringBuilder text = new StringBuilder();
            for (var part : root.getAsJsonArray("content")) {
                JsonObject o = part.getAsJsonObject();
                if ("text".equals(o.get("type").getAsString())) {
                    text.append(o.get("text").getAsString());
                }
            }
            return AiCodeSanitizer.sanitize(text.toString());
        } catch (Exception e) {
            call.ms = System.currentTimeMillis() - t;
            call.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return null;
        }
    }

    /** Place the accepted design in front of the player, take a picture, save the front view, then undo. */
    private void photograph(ClientGameTestContext context, TestSingleplayerContext game, String model, Case c, Result r, double[] home) {
        BuildRecord record = BuildRecord.fromJson(read(model, c.id() + ".record.json"));
        String camera = game.getServer().computeOnServer(server -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
            var origin = player.getBlockPos();
            Demo.place(player, origin, record.plan, 0, c.prompt());
            int[] b = record.plan.bounds();
            double cx = origin.getX() + (b[0] + b[3] + 1) / 2.0;
            double cy = origin.getY() + (b[1] + b[4] + 1) / 2.0;
            double cz = origin.getZ() + (b[2] + b[5] + 1) / 2.0;
            double size = Math.max(Math.max(b[3] - b[0], b[4] - b[1]), b[5] - b[2]) + 1;
            // Stand in front of it (the player's side, north of it since the player faces south).
            double dist = c.object() ? Math.max(2.2, size * 1.6) : size * 1.3 + 4;
            double camZ = cz - (b[5] - b[2] + 1) / 2.0 - dist;
            double camY = cy + (c.object() ? 0.6 : size * 0.35);
            return String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx, camY, camZ, cx, cy, cz);
        });
        for (int i = 0; i < 400 && BuildTaskQueue.pendingCount() > 0; i++) {
            context.waitTicks(5);
        }
        game.getServer().runCommand("gamemode spectator @a");
        game.getServer().runCommand(camera);
        context.waitTicks(10);
        game.getClientWorld().waitForChunksRender();
        context.takeScreenshot("bench_" + safe(model) + "_" + c.id());
        try {
            BuildPreview preview = record.plan.isObject() ? BuildPreview.computeObject(record.plan) : BuildPreview.compute(record.plan);
            Path dir = OUT.resolve(safe(model));
            preview.writePngs(dir.resolve(c.id() + "_top.png"), dir.resolve(c.id() + "_front.png"));
        } catch (Exception e) {
            r.summary += " (preview failed: " + e.getMessage() + ")";
        }
        String back = String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 18", home[0], home[1], home[2]);
        game.getServer().runCommand(back);
        game.getServer().runOnServer(server -> UndoService.undo(server.getPlayerManager().getPlayerList().get(0)));
        context.waitTicks(5);
    }

    private void write(List<Result> results) {
        JsonObject out = new JsonObject();
        out.addProperty("compatEndpoint", compatError == null ? "ok" : compatError);
        out.add("results", GSON.toJsonTree(results));
        try {
            Files.writeString(OUT.resolve("results.json"), GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void save(String model, String name, String text) {
        try {
            Path dir = OUT.resolve(safe(model));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String read(String model, String name) {
        try {
            return Files.readString(OUT.resolve(safe(model)).resolve(name), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
