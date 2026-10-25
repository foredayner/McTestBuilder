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

    private record Case(String id, String prompt, String mode, String hit, String idle) {
        Case(String id, String prompt, String mode) {
            this(id, prompt, mode, "", "");
        }

        boolean object() {
            return !"build".equals(mode);
        }

        boolean gear() {
            return mode.startsWith("gear:");
        }
    }

    private static final List<Case> CASES = List.of(
        new Case("a1_holy", "성스러운 빛의 장검", "gear:sword", "빛 입자가 한 점에서 구형으로 퍼져나감", "금빛 후광이 검 전체를 감쌈"),
        new Case("a2_void", "공허의 낫 모양 지팡이", "gear:staff", "보라색 입자가 큰 구에서 한 점으로 빨려 들어감", "검보라색 오라가 일렁임"),
        new Case("a3_crown", "태양 왕관", "gear:hat", "", "황금빛 후광이 왕관을 감싸고 빛남"),
        new Case("b1_scythe", "게임이나 애니에 나오는 대형 전투낫", "gear:sword"),
        new Case("b2_reaper", "사신의 거대한 전투낫, 검은 자루에 은빛 초승달 날", "gear:sword", "검붉은 베기 궤적", "")
    );

    /** Cases to run: AIB_BENCH_CASES (comma-separated ids), or all. */
    private static List<Case> cases() {
        String wanted = System.getenv("AIB_BENCH_CASES");
        if (wanted == null || wanted.isBlank()) {
            return CASES;
        }
        List<String> ids = java.util.Arrays.stream(wanted.split(",")).map(String::trim).toList();
        return CASES.stream().filter(c -> ids.contains(c.id())).toList();
    }

    /** One AI call. */
    static final class Call {
        String route;
        int status;
        long ms;
        int inputTokens;
        int outputTokens;
        int cacheRead;
        int cacheWrite;
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
        int cacheReadTokens;
        int cacheWriteTokens;
        List<Call> attempts = new ArrayList<>();
    }

    private String route = "claude";

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
        // "claude" = the mod's own Claude connection (default); "compat" = OpenAI-compatible endpoint.
        boolean compat = "compat".equals(System.getenv("AIB_BENCH_PROVIDER"));
        config.provider = compat ? AiProvider.OPENAI : AiProvider.CLAUDE;
        config.apiUrl = compat ? "https://api.anthropic.com/v1" : "https://api.anthropic.com";
        route = compat ? "openai-compatible" : "claude";
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
                server.getPlayerManager().sendCommandTree(p); // tells the client it is an operator
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
                for (Case c : cases()) {
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
        com.example.ai.AiPrompt base = game.getServer().computeOnServer(server ->
            AiController.promptFor(server.getPlayerManager().getPlayerList().get(0), c.prompt(), c.mode(), c.hit(), c.idle()));
        com.example.ai.AiPrompt prompt = base;
        long start = System.currentTimeMillis();
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            Call call = new Call();
            r.attempts.add(call);
            r.calls++;
            String code = ask(prompt, call);
            r.inputTokens += call.inputTokens;
            r.cacheReadTokens += call.cacheRead;
            r.cacheWriteTokens += call.cacheWrite;
            r.outputTokens += call.outputTokens;
            if (code == null) {
                r.finalFailure = "API: " + call.error;
                break;
            }
            save(model, c.id() + "_try" + attempt + ".groovy", code);
            AiController.Evaluation eval = game.getServer().computeOnServer(server ->
                AiController.evaluate(server.getPlayerManager().getPlayerList().get(0), code, c.gear() || c.object()));
            String gearProblem = eval.ok() && c.gear()
                ? AiController.gearProblem(eval.plan(), com.example.content.GearKind.of(c.mode().substring(5))) : null;
            if (gearProblem == null && eval.ok() && c.gear()) {
                gearProblem = AiController.effectProblem(eval.plan(), com.example.content.GearKind.of(c.mode().substring(5)),
                    c.hit(), c.idle());
            }
            if (eval.ok() && gearProblem == null) {
                r.success = true;
                r.summary = eval.plan().summary();
                BuildRecord record = BuildRecord.create(c.prompt(), "BENCH", model, eval.plan());
                if (c.gear()) {
                    record.kind = "gear";
                    record.gear = c.mode().substring(5);
                    AiController.applyEffectRequest(record.plan, com.example.content.GearKind.of(record.gear), c.hit(), c.idle());
                    r.summary += " | anims " + record.plan.anims.size() + " " + animTypes(record.plan)
                        + " | hit " + fxText(record.plan.hit) + " | idle " + fxText(record.plan.idle);
                }
                save(model, c.id() + ".record.json", record.toJson());
                break;
            }
            String failure = gearProblem != null ? gearProblem : eval.failure();
            call.failure = failure;
            r.finalFailure = failure;
            prompt = AiController.fixPrompt(base, code, failure);
        }
        r.totalMs = System.currentTimeMillis() - start;
        return r;
    }

    /** Send one prompt the way the game does; returns the cleaned script, or null with call.error set. */
    private String ask(com.example.ai.AiPrompt prompt, Call call) {
        long t = System.currentTimeMillis();
        call.route = route;
        try {
            HttpRequest request = AiHttpService.buildRequest(prompt, false);
            HttpResponse<String> response = AiHttpService.client().send(request, HttpResponse.BodyHandlers.ofString());
            call.status = response.statusCode();
            call.ms = System.currentTimeMillis() - t;
            int[] usage = AiHttpService.usage(response.body());
            call.inputTokens = usage[0];
            call.outputTokens = usage[1];
            call.cacheRead = usage[2];
            call.cacheWrite = usage[3];
            AiProvider provider = AiBuilderConfig.get().provider;
            if (response.statusCode() / 100 != 2) {
                call.error = AiHttpService.extractHttpErrorMessage(response.body(), response.statusCode(), provider)
                    + " | " + clip(response.body(), 400);
                return null;
            }
            return AiCodeSanitizer.sanitize(AiHttpService.extractResponseBody(response.body(), provider));
        } catch (Exception e) {
            call.ms = System.currentTimeMillis() - t;
            call.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return null;
        }
    }

    private static String fxText(com.example.ai.build.BuildPlan.Fx fx) {
        return fx == null ? "none" : fx.particle + " x" + fx.count;
    }

    private static String animTypes(com.example.ai.build.BuildPlan plan) {
        List<String> types = new ArrayList<>();
        for (var a : plan.anims) {
            types.add(a.type);
        }
        return types.toString();
    }

    /** Place the accepted design in front of the player, take a picture, save the front view, then undo. */
    private void photograph(ClientGameTestContext context, TestSingleplayerContext game, String model, Case c, Result r, double[] home) {
        BuildRecord record = BuildRecord.fromJson(read(model, c.id() + ".record.json"));
        com.example.content.GearKind kind = c.gear() ? com.example.content.GearKind.of(c.mode().substring(5)) : null;
        if (kind != null && kind.worn()) {
            // Worn equipment: put it on and take pictures from the front and the back.
            game.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .equipStack(kind.slot, com.example.ai.build.GearItems.stackFor(record)));
            context.runOnClient(client -> {
                client.options.hudHidden = true;
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            });
            context.waitTicks(10);
            context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_front");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_back");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            game.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .equipStack(kind.slot, net.minecraft.item.ItemStack.EMPTY));
            try {
                Path dir = OUT.resolve(safe(model));
                BuildPreview.compute(record.plan).writePngs(dir.resolve(c.id() + "_top.png"), dir.resolve(c.id() + "_front.png"));
            } catch (Exception e) {
                r.summary += " (preview failed: " + e.getMessage() + ")";
            }
            return;
        }
        if (c.gear()) {
            // Equipment: hold it and take a picture in first person and from the front.
            game.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(record)));
            context.runOnClient(client -> {
                client.options.hudHidden = false;
                client.inGameHud.getChatHud().clear(false);
            });
            // A newly held weapon is raised only after its attack cooldown (an axe: about 1 s).
            context.waitTicks(40);
            context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_hand");
            if (record.plan.hit != null) {
                // Hit something 3 blocks ahead and photograph the hit effect a few ticks in.
                game.getServer().runOnServer(server -> {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                    var w = p.getServerWorld();
                    var stand = new net.minecraft.entity.decoration.ArmorStandEntity(w,
                        p.getX(), p.getY(), p.getZ() + 3);
                    stand.addCommandTag("aib_bench_target");
                    w.spawnEntity(stand);
                    com.example.ai.build.GearEffects.onHit(p.getMainHandStack(), stand, p);
                });
                context.waitTicks(8);
                context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_hit");
                context.waitTicks(40);
                game.getServer().runCommand("kill @e[tag=aib_bench_target]");
            }
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_front");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("bench_" + safe(model) + "_" + c.id() + "_back");
            context.runOnClient(client -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.options.hudHidden = true;
            });
            game.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY));
            try {
                Path dir = OUT.resolve(safe(model));
                BuildPreview.compute(record.plan).writePngs(dir.resolve(c.id() + "_top.png"), dir.resolve(c.id() + "_front.png"));
            } catch (Exception e) {
                r.summary += " (preview failed: " + e.getMessage() + ")";
            }
            return;
        }
        String camera = game.getServer().computeOnServer(server -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
            var origin = player.getBlockPos();
            Demo.place(player, origin, record.plan, 0, c.prompt());
            // Objects: their exact box (they are smaller than whole blocks); buildings: block bounds.
            double[] b;
            if (c.object()) {
                b = record.plan.partBounds();
            } else {
                int[] ib = record.plan.bounds();
                b = new double[] { ib[0], ib[1], ib[2], ib[3] + 1, ib[4] + 1, ib[5] + 1 };
            }
            double cx = origin.getX() + (b[0] + b[3]) / 2.0;
            double cy = origin.getY() + (b[1] + b[4]) / 2.0;
            double cz = origin.getZ() + (b[2] + b[5]) / 2.0;
            double size = Math.max(Math.max(b[3] - b[0], b[4] - b[1]), b[5] - b[2]);
            // Stand in front of it (the player's side, north of it since the player faces south).
            double dist = c.object() ? Math.max(1.2, size * 1.3) : size * 1.3 + 4;
            double camZ = cz - (b[5] - b[2]) / 2.0 - dist;
            // tp moves the feet; the eyes are 1.62 higher, so lower the feet to look from camY.
            double camY = cy + (c.object() ? size * 0.25 : size * 0.35) - 1.62;
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
        out.addProperty("route", route);
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
