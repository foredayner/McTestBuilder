package com.example.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.example.ai.api.MinecraftAI;
import com.example.ai.approval.BuildApproval;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.example.ai.script.ScriptExecutor;
import com.example.ai.script.ScriptLinter;
import java.util.List;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import com.example.network.AiNetworking;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiController {

    /** One build request, kept across automatic retries. */
    private record BuildRequest(String userPrompt, AiPrompt basePrompt, boolean webSearch, long startTime) {
    }

    public static void requestBuild(ServerPlayerEntity player, String prompt) {
        requestBuild(player, prompt, false);
    }

    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch) {
        requestBuild(player, prompt, useWebSearch, false);
    }

    /** objectMode: a small decorative object from scaled block pieces instead of a block build. */
    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch, boolean objectMode) {
        long startTime = System.currentTimeMillis();
        AiBuilderConfig config = AiBuilderConfig.get();
        boolean webSearch = config.provider.supportsWebSearch()
            && (useWebSearch || config.openAiWebSearchEnabled);

        if (webSearch && config.provider == AiProvider.OPENAI && !OpenAiWebSearchSupport.isModelSupported(config.modelName)) {
            String message = OpenAiWebSearchSupport.unsupportedModelMessage(config.modelName);
            debug("[ERROR] " + message);
            AiNetworking.sendLog(player, "[AI] Error: " + message);
            return;
        }

        AiNetworking.sendDebugLog(player, "[AI] Planning...");
        debug("[TIMER] ===== AI BUILD START =====");
        debug("[INPUT] Prompt: " + prompt);
        if (webSearch) {
            debug("[WEB] Web search enabled for this request");
            AiNetworking.sendDebugLog(player, "[AI] Web search enabled");
        }

        try {
            ServerWorld world = player.getServerWorld();
            BlockPos origin = player.getBlockPos();

            debug("[DEBUG] Initializing MinecraftAI...");
            MinecraftAI.init(world, origin, player.getYaw(), player.getPitch());

            debug("[DEBUG] Getting world info...");
            String worldInfo = MinecraftAI.getWorldInfo(30, player.getYaw(), player.getPitch());
            debug("[DEBUG] World info:\n" + worldInfo);

            debug("[DEBUG] Building prompt...");
            AiPrompt promptText = objectMode
                ? objectPrompt(prompt)
                : buildingPrompt(prompt, worldInfo, webSearch);

            debug("[DEBUG] Sending to AI provider...");
            AiNetworking.sendDebugLog(player, "[AI] Requesting from AI model (" + config.provider.getDisplayName() + ")...");

            sendToAi(player, new BuildRequest(prompt, promptText, webSearch, startTime), promptText, 0);

        } catch (Exception exception) {
            debug("[ERROR] Fatal error: " + exception.getMessage());
            exception.printStackTrace();
            AiNetworking.sendLog(player, "[AI] Error: " + exception.getMessage());
        }
    }

    private static void sendToAi(ServerPlayerEntity player, BuildRequest req, AiPrompt promptText, int attempt) {
        var request = AiHttpService.buildRequest(promptText, req.webSearch());
        AiHttpService.client().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> runOnServer(player, () -> handleAiResponse(player, response, req, attempt)))
            .exceptionally(ex -> {
                runOnServer(player, () -> handleAiFailure(player, ex, req.startTime()));
                return null;
            });
    }

    private static void handleAiResponse(ServerPlayerEntity player, HttpResponse<String> response, BuildRequest req, int attempt) {
        long startTime = req.startTime();
        boolean webSearch = req.webSearch();
        try {
            AiProvider provider = AiBuilderConfig.get().provider;
            String endpoint = AiHttpService.resolveEndpointForProvider(provider, AiBuilderConfig.get().apiUrl, webSearch);

            debug("[DEBUG] Response status: " + response.statusCode());
            debug("[DEBUG] Request endpoint: " + endpoint);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMessage = AiHttpService.extractHttpErrorMessage(response.body(), response.statusCode(), provider);
                AiNetworking.sendLog(player, "[AI] Error: " + errorMessage);
                return;
            }

            String rawBody = response.body();
            debug("[DEBUG] Response size: " + rawBody.length() + " chars");

            if (webSearch) {
                for (String query : AiHttpService.extractWebSearchQueries(rawBody, provider)) {
                    debug("[WEB] Search query: " + query);
                    AiNetworking.sendDebugLog(player, "[AI] Web search: " + query);
                }
            }

            String aiCode = AiCodeSanitizer.sanitize(AiHttpService.extractResponseBody(rawBody, provider));
            debug("[AI CODE OUTPUT]\n" + aiCode);

            if (aiCode.isBlank()) {
                AiNetworking.sendLog(player, "[AI] Error: AI returned empty build script.");
                return;
            }

            planAndApprove(player, req, aiCode, attempt);
            debug("[TIMER] Total AI time: " + (System.currentTimeMillis() - startTime) + "ms");
        } catch (Exception exception) {
            debug("[ERROR] Exception: " + exception.getMessage());
            exception.printStackTrace();
            AiNetworking.sendLog(player, "[AI] Error: " + exception.getMessage());
        }
    }

    /**
     * Dry-run the script, recording what it would place. A script that fails to compile or run
     * (or places nothing) goes back to the AI with the error, up to autoRetries times; a good one
     * is shown for approval. Nothing touches the world until the player accepts.
     */
    private static void planAndApprove(ServerPlayerEntity player, BuildRequest req, String aiCode, int attempt) {
        BlockPos origin = player.getBlockPos();
        Evaluation eval = evaluate(player, aiCode);
        BuildPlan plan = eval.plan();
        ScriptExecutor.ExecutionResult result = eval.result();
        String failure = eval.failure();
        if (!eval.review().isEmpty()) {
            AiNetworking.sendLog(player, "[Review] " + eval.review().size() + " problem(s) found before running:\n- " + String.join("\n- ", eval.review()));
        } else {
            AiNetworking.sendDebugLog(player, "[Review] Code check passed.");
            if (failure != null && failure.startsWith(PLAN_PROBLEMS)) {
                AiNetworking.sendLog(player, "[Review] " + failure);
            }
        }
        if (failure == null) {
            AiBuilderConfig config = AiBuilderConfig.get();
            String description = generateDescription(aiCode, origin) + "\n" + plan.summary();
            BuildRecord record = BuildRecord.create(req.userPrompt(), config.provider.name(), config.modelName, plan);
            BuildApproval.requestApproval(player, record, description, origin);
            debug("[TIMER] ===== PENDING USER APPROVAL =====");
            return;
        }

        int maxRetries = AiBuilderConfig.get().autoRetries;
        if (attempt < maxRetries) {
            AiNetworking.sendLog(player, "[AI] Script failed: " + firstLine(failure));
            AiNetworking.sendLog(player, "[AI] Asking the AI to fix it (retry " + (attempt + 1) + "/" + maxRetries + ")...");
            sendToAi(player, req, fixPrompt(req.basePrompt(), aiCode, failure), attempt + 1);
            return;
        }

        if (result != null && result.securityBlocked()) {
            AiNetworking.sendLog(player, "[Security Alert] " + failure);
            AiNetworking.notify(player, "\u00a7c[Security Alert] " + firstLine(failure) + "\u00a7r");
        } else {
            AiNetworking.sendLog(player, "[AI] Script error: " + failure);
            if (maxRetries > 0) {
                AiNetworking.sendLog(player, "[AI] Gave up after " + maxRetries + " retries. Try a simpler request or a larger model.");
            }
        }
    }

    private static final String PLAN_PROBLEMS = "The build plan has problems:";

    /** What checking one AI script gave: the recorded plan, or why it was rejected (failure != null). */
    public record Evaluation(BuildPlan plan, String failure, List<String> review, ScriptExecutor.ExecutionResult result) {
        public boolean ok() {
            return failure == null;
        }
    }

    /**
     * 1) Review the code (no AI call), 2) dry run it in recording mode, 3) check what it would
     * place. Nothing touches the world. Used by the game and by the model benchmark.
     */
    public static Evaluation evaluate(ServerPlayerEntity player, String aiCode) {
        MinecraftAI.init(player.getServerWorld(), player.getBlockPos(), player.getYaw(), player.getPitch(), player);
        BuildPlan plan = new BuildPlan();
        plan.facing = BuildPlan.normalizeFacing(MinecraftAI.getFacing());

        ScriptExecutor.ExecutionResult result = null;
        String failure;
        List<String> review = ScriptLinter.lint(aiCode);
        if (!review.isEmpty()) {
            failure = "Code review found problems:\n- " + String.join("\n- ", review);
        } else {
            MinecraftAI.startRecording(plan);
            try {
                result = ScriptExecutor.executeScript(aiCode);
            } finally {
                MinecraftAI.stopRecording();
            }
            plan.useObjectFrame();
            failure = !result.success() ? result.message()
                : plan.isEmpty() ? "The script ran but placed nothing. Use the ai.place* / ai.addPart functions." : null;
            if (failure == null) {
                List<String> planProblems = plan.problems();
                if (!planProblems.isEmpty()) {
                    failure = PLAN_PROBLEMS + "\n- " + String.join("\n- ", planProblems);
                }
            }
        }
        return new Evaluation(plan, failure, review, result);
    }

    /** The prompt the game sends for a request, made where the player stands now. */
    public static AiPrompt promptFor(ServerPlayerEntity player, String userPrompt, boolean objectMode) {
        MinecraftAI.init(player.getServerWorld(), player.getBlockPos(), player.getYaw(), player.getPitch());
        return objectMode
            ? objectPrompt(userPrompt)
            : buildingPrompt(userPrompt, MinecraftAI.getWorldInfo(30, player.getYaw(), player.getPitch()), false);
    }

    /** The same request again, with the error and the failed script, asking for a fixed script. */
    public static AiPrompt fixPrompt(AiPrompt base, String badCode, String error) {
        return new AiPrompt(base.system(), base.user()
            + "\n\nYOUR PREVIOUS SCRIPT FAILED. Fix it.\n"
            + "Error:\n" + truncate(error, 1500) + "\n\n"
            + "Previous script:\n" + truncate(badCode, 6000) + "\n\n"
            + "Return the complete corrected Groovy script only. No markdown, no explanation.\n");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n...(truncated)";
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        for (String line : text.replace("\r", "").split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("startup failed")) {
                return trimmed.length() > 160 ? trimmed.substring(0, 160) + "..." : trimmed;
            }
        }
        return text.trim();
    }

    private static void handleAiFailure(ServerPlayerEntity player, Throwable throwable, long startTime) {
        debug("[TIMER] HTTP Error: " + (System.currentTimeMillis() - startTime) + "ms");
        Throwable cause = getRootCause(throwable);
        debug("[ERROR] HTTP Request failed: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        cause.printStackTrace();

        String message = cause.getMessage();
        if (cause instanceof java.net.ConnectException) {
            message = "Cannot connect to " + AiBuilderConfig.get().apiUrl + ". Check Settings > API URL.";
        }
        AiNetworking.sendLog(player, "[AI] Error: " + message);
    }

    private static void runOnServer(ServerPlayerEntity player, Runnable action) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(action);
        }
    }

    private static void debug(String message) {
        if (AiBuilderConfig.get().debugLogEnabled) {
            System.out.println(message);
        }
    }

    /** Example objects shown to the AI (also dry-run by the game tests, so they always work). */
    public static final String EXAMPLE_DUCK = """
        // rubber duck with a top hat, about 1.5 blocks tall; it looks at the player (-Z)
        def z = 2.6
        ai.addEllipsoid('yellow_concrete', 0, 0.42, z + 0.15, 0.42, 0.34, 0.55)      // body
        ai.addEllipsoid('yellow_terracotta', 0, 0.16, z + 0.15, 0.34, 0.1, 0.45)     // darker underside
        ai.addCone('yellow_concrete', 0, 0.45, z + 0.62, 0.16, 0.0, 0.32)          // tail
        ai.addSphere('yellow_concrete', 0, 0.98, z - 0.12, 0.3)                      // big head
        ai.addRoundBox('orange_concrete', -0.13, 0.84, z - 0.56, 0.26, 0.1, 0.2, 0.04) // beak
        ai.addCylinder('black_concrete', 0, 1.24, z - 0.12, 0.22, 0.03)              // hat brim
        ai.addCylinder('black_concrete', 0, 1.26, z - 0.12, 0.14, 0.26)              // hat
        ai.addCylinder('red_concrete', 0, 1.29, z - 0.12, 0.145, 0.05)               // hat band
        ai.mirrorOn()
        ai.addSphere('black_concrete', 0.13, 1.04, z - 0.38, 0.06)                   // eyes
        ai.addSphere('white_concrete', 0.11, 1.07, z - 0.43, 0.02)                   // eye shine
        ai.addEllipsoid('yellow_terracotta', 0.4, 0.48, z + 0.2, 0.07, 0.18, 0.3)    // wings
        ai.addRoundBox('orange_concrete', 0.1, 0, z - 0.15, 0.16, 0.05, 0.2, 0.02)   // feet
        ai.mirrorOff()
        """;

    public static final String EXAMPLE_LAMP = """
        // desk lamp, about 1 block tall
        def z = 2.5
        ai.addCylinder('spruce_planks', 0, 0, z, 0.22, 0.06)                         // base
        ai.addCylinder('dark_oak_planks', 0, 0.06, z, 0.18, 0.02)                     // base trim (darker)
        ai.addTube('iron_block', [[0, 0.06, z], [0.06, 0.35, z + 0.04], [0, 0.66, z]], 0.035)  // curved stem
        ai.addSphere('glowstone', 0, 0.68, z, 0.08)                                   // bulb
        ai.addCone('white_wool', 0, 0.6, z, 0.3, 0.15, 0.36)                          // shade
        ai.addCone('light_gray_wool', 0, 0.58, z, 0.31, 0.29, 0.03)                   // shade rim
        """;

    public static final String EXAMPLE_CHAIR = """
        // wooden chair at real size; the seat front faces the player
        def z = 2.2
        def w = 0.8
        def d = 0.8
        ai.addRoundBox('oak_planks', -w / 2, 0.45, z, w, 0.08, d, 0.02)              // seat
        ai.addRoundBox('red_wool', -w / 2 + 0.06, 0.53, z + 0.06, w - 0.12, 0.05, d - 0.12, 0.03)  // cushion
        ai.mirrorOn()
        ai.addRoundBox('oak_planks', w / 2 - 0.1, 0, z + 0.02, 0.08, 0.45, 0.08, 0.01)          // front legs
        ai.addRoundBox('stripped_oak_log', w / 2 - 0.1, 0, z + d - 0.1, 0.08, 1.2, 0.08, 0.01)  // back legs and posts
        ai.mirrorOff()
        for (int i = 0; i < 3; i++) {
            ai.addRoundBox('oak_planks', -w / 2 + 0.06, 0.72 + i * 0.16, z + d - 0.09, w - 0.12, 0.08, 0.05, 0.01)  // back slats
        }
        """;

    /** Instructions for objects: the same for every request (cached by Claude). */
    static final String OBJECT_SYSTEM = """
            You are an expert modeller of cute, detailed Minecraft props and characters. Write ONLY executable Groovy using the `ai` API.

            Build ONE object out of display-entity pieces (not whole blocks). Smooth shapes are drawn on a fine
            1/16-block grid (one texture pixel), and touching pieces of the same block are merged automatically.

            FRAME (always this, whichever way the player really looks):
            - Units are blocks; decimals are fine. +X = the player's right, +Y = up, +Z = away from the player.
            - The player stands at (0, 0, 0). Centre the object on x = 0, standing on y = 0, starting about 1.5-2 blocks away (+Z).
            - Its FRONT (face, eyes, display side, opening, seat front) looks toward -Z, at the player.
            - Left and right are mirror images across x = 0.

            SIZE (the player is 1.8 blocks tall):
            - If the request gives a size, use it. Otherwise: characters and animals 1.2-2.5 blocks tall; furniture at real
              size (chair seat 0.45 high, table 0.9 high); small items 0.4-1 block.
            - Never more than 4 blocks in any direction unless the request asks for it.

            SMOOTH SHAPES (on the grid; a later shape paints over earlier ones where they overlap):
              ai.addSphere(block, cx, cy, cz, r)
              ai.addEllipsoid(block, cx, cy, cz, rx, ry, rz)
              ai.addCylinder(block, cx, bottomY, cz, r, height)                 // upright
              ai.addCone(block, cx, bottomY, cz, r, height)                     // upright, point on top
              ai.addCone(block, cx, bottomY, cz, bottomR, topR, height)         // cut cone: lampshade, pot, trunk
              ai.addCapsule(block, x1, y1, z1, x2, y2, z2, r)                   // rounded rod in any direction: limbs, handles
              ai.addCapsule(block, x1, y1, z1, x2, y2, z2, r1, r2)              // tapered rod (r1 at the first end)
              ai.addTube(block, [[x, y, z], [x, y, z], ...], r)                 // smooth curve through 2-16 points: neck, tail, horn, stem
              ai.addTube(block, [[x, y, z], ...], r1, r2)                       // tapering along the curve
              ai.addRoundBox(block, x, y, z, sizeX, sizeY, sizeZ, radius)       // min corner + size, rounded edges; radius 0 = sharp
            EXACT PIECES (not on the grid; for very thin or tilted details):
              ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ)                   // box: min corner + size
              ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ, yaw)              // turned around its centre, degrees
              ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ, yaw, pitch, roll)
            SYMMETRY:
              ai.mirrorOn()    // until mirrorOff, every piece is also made on the other side of x = 0 (eyes, ears, arms, legs, wings)
              ai.mirrorOff()
            BLOCKS: ai.searchBlocks(keyword)

            STYLE (this decides the quality):
            - Silhouette first: 2-4 big shapes with the right proportions, then medium parts, then small details.
            - Characters and animals: cute, stylised proportions. A big head (40-60%% of the body), big readable eyes
              (dark pupil + small white shine), a simple nose/mouth/beak, short sturdy limbs, a recognisable feature
              (horn, hat, mane, ears, tail).
            - 3-5 main colours. Use a darker shade for undersides and creases and a lighter one for highlights
              (e.g. yellow_concrete body + yellow_terracotta underside; white_concrete + light_gray_concrete).
            - Overlap shapes slightly so nothing floats or leaves gaps; set details just outside the surface they sit on.
            - Colours: *_concrete (bright, flat), *_terracotta (muted, skin and earth tones), *_wool (soft), white and black
              concrete for eyes. Materials: oak/spruce/dark_oak_planks, stripped_*_log, stone, mossy_cobblestone, iron_block,
              gold_block, glass, glowstone, quartz_block.
            - Put the main sizes and positions in variables and build from them.
            - Prefer a few smooth shapes over many tiny boxes. At most 400 pieces after merging.

            CODE RULES:
            - Block names are quoted strings: 'white_concrete'.
            - Loops: for (int i = 0; i < n; i++) or for (i in 0..<n). Never closures: no .each { }, .times { }, { -> }.
            - No methods, classes or imports. Output Groovy only; no markdown, no explanation (short // comments are fine).

            EXAMPLES (style reference; make your own design, do not copy):
            %s
            %s
            %s
            """.formatted(EXAMPLE_DUCK.strip(), EXAMPLE_LAMP.strip(), EXAMPLE_CHAIR.strip());

    public static AiPrompt objectPrompt(String userPrompt) {
        return new AiPrompt(OBJECT_SYSTEM, "Object request: " + userPrompt + "\nWrite the Groovy script for it now.");
    }

    /** Instructions for buildings (fixed part) + the request and the terrain around the player. */
    public static AiPrompt buildingPrompt(String userPrompt, String worldInfo, boolean webSearchEnabled) {
        String user = "Build request: " + userPrompt + "\n\nWorld Information:\n" + worldInfo
            + "\n\nWrite the Groovy script for it now.";
        return new AiPrompt(buildingSystem(webSearchEnabled), user);
    }

    static String buildingSystem(boolean webSearchEnabled) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a Minecraft architect. Write ONLY executable Groovy using the `ai` API.\n\n");

        prompt.append("Absolute instructions:\n");
        prompt.append("1. Prioritize building quality above all else. (Never build flat. Focus on depth, 3D layering, overhangs, and mixing blocks for realistic texture).\n");
        prompt.append("2. Base your designs on real-world structures and architecture rather than abstract concepts. Build with physical logic (foundations, pillars, beams).\n");
        if (webSearchEnabled) {
            prompt.append("3. Web search is ON: use it whenever you need design inspiration or block information.\n");
        }
        prompt.append("\n");

        prompt.append("Scale and space (1 block = 1 metre; the player is 1.8 blocks tall). Make buildings and rooms feel roomy:\n");
        prompt.append("- Unless the request says otherwise, a house has a footprint of at least 11 x 11 (bigger for mansions, shops,\n");
        prompt.append("  halls, castles) and is divided into several rooms with interior walls and doorways.\n");
        prompt.append("- Rooms are at least 5 x 5 inside; living rooms, kitchens and halls 7 x 9 or more.\n");
        prompt.append("- Every storey has 3-4 blocks of free air inside (floor to floor 4-5 blocks). Never 2-block-high rooms.\n");
        prompt.append("- Doors 2 high; hallways 2-3 wide; stairs 2 wide with 3 blocks of headroom; leave walking space around furniture.\n");
        prompt.append("- Windows on the outer walls of every room, light inside (lanterns, glowstone), and a few pieces of furniture.\n");
        prompt.append("- Clear the inside with air (clearArea) so terrain does not stay inside the rooms.\n\n");

        prompt.append("The request and the World Information (terrain around the player) come in the next message.\n\n");

        prompt.append("API signatures (exact order; all x/y/z relative to player feet):\n");
        prompt.append("Build:\n");
        prompt.append("  ai.placeBlock(x, y, z, blockName)\n");
        prompt.append("  ai.placeBlock(blockName, x, y, z)\n");
        prompt.append("  ai.placeBox(x, y, z, width, height, length, blockName)\n");
        prompt.append("  ai.placeBox(blockName, x, y, z, width, height, length)\n");
        prompt.append("  ai.placePillar(x, y, z, height, blockName) | ai.placePillar(blockName, x, y, z, height)\n");
        prompt.append("  ai.placeFloor(x, y, z, width, length, blockName) | ai.placeFloor(x, y, z, width, length)\n");
        prompt.append("  ai.placeRoof(x, y, z, width, length, blockName) | ai.placeRoof(x, y, z, width, length)\n");
        prompt.append("  ai.placeWall(x, y, z, height, length, blockName, direction)\n");
        prompt.append("  ai.placeWall(x, y, z, height, length, direction, blockName)\n");
        prompt.append("  ai.placeWall(blockName, x, y, z, height, length, direction)\n");
        prompt.append("  ai.clearArea(x, y, z, width, length)              // 5 args: clear 1 horizontal layer\n");
        prompt.append("  ai.clearArea(x, y, z, width, height, length)      // 6 args: clear box volume\n");
        prompt.append("Inspect:\n");
        prompt.append("  ai.getFacing() -> String\n");
        prompt.append("  ai.getForwardX(distance) -> int | ai.getForwardZ(distance) -> int\n");
        prompt.append("  ai.getGroundLevel(x, z) -> int | ai.getBlock(x, y, z) -> String\n");
        prompt.append("  ai.canPlace(x, y, z) -> boolean | ai.isSolid(x, y, z) -> boolean\n");
        prompt.append("  ai.inspectColumn(x, z) -> String | ai.scanForward(maxDistance) -> String\n");
        prompt.append("  ai.scanArea(x, z, width, length) -> String\n");
        prompt.append("Blocks:\n");
        prompt.append("  ai.searchBlocks(keyword) | ai.searchBlocks(keyword, limit)\n");
        prompt.append("  ai.listBlocks() | ai.listBlocks(offset, limit)\n");
        prompt.append("  ai.getBlockListCount() -> int | ai.isBlockAllowed(blockName) -> boolean\n\n");

        prompt.append("Rules:\n");
        prompt.append("- width = size on X axis, length = size on Z axis, height = size on Y axis.\n");
        prompt.append("- Always prefix calls with ai. Match argument count exactly (5 or 6 for clearArea).\n");
        prompt.append("- Coordinates are RELATIVE to player feet (0,0,0). getGroundLevel returns relative Y.\n");
        prompt.append("- Use searchBlocks/listBlocks for block names. Forbidden blocks cannot be placed.\n");
        prompt.append("- Block names are quoted strings: 'oak_planks'. Loops: for (int i = 0; i < n; i++); never closures (.each { }).\n");
        prompt.append("- Build in front of the player. The FRONT of the building (main door, facade, windows of the main\n");
        prompt.append("  face) must face the player, i.e. point back toward (0, 0, 0).\n");
        prompt.append("- Inspect terrain before building. Output Groovy only. No markdown, no explanation.\n");

        return prompt.toString();
    }

    private static final Pattern BOX_PATTERN = Pattern.compile(
        "placeBox\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)"
    );

    private static String generateDescription(String aiCode, BlockPos origin) {
        StringBuilder desc = new StringBuilder();

        desc.append("Build location: X=").append(origin.getX())
            .append(" Y=").append(origin.getY())
            .append(" Z=").append(origin.getZ()).append("\n");
        desc.append("Relative origin: (0, 0, 0) = player feet\n");

        String footprint = estimateFootprint(aiCode, origin);
        if (!footprint.isEmpty()) {
            desc.append(footprint).append("\n");
        }

        int boxCount = countOccurrences(aiCode, "placeBox");
        int pillarCount = countOccurrences(aiCode, "placePillar");
        int floorCount = countOccurrences(aiCode, "placeFloor");
        int roofCount = countOccurrences(aiCode, "placeRoof");
        int wallCount = countOccurrences(aiCode, "placeWall");
        int blockCount = countOccurrences(aiCode, "placeBlock");

        if (boxCount > 0) {
            desc.append("Place ").append(boxCount).append(" box(es)\n");
        }
        if (pillarCount > 0) {
            desc.append("Place ").append(pillarCount).append(" pillar(s)\n");
        }
        if (floorCount > 0) {
            desc.append("Place ").append(floorCount).append(" floor(s)\n");
        }
        if (roofCount > 0) {
            desc.append("Place ").append(roofCount).append(" roof\n");
        }
        if (wallCount > 0) {
            desc.append("Place ").append(wallCount).append(" wall(s)\n");
        }
        if (blockCount > 0) {
            desc.append("Place ").append(blockCount).append(" block(s)\n");
        }

        if (desc.length() == 0) {
            desc.append("Execute building operations");
        }

        return desc.toString().trim();
    }

    private static String estimateFootprint(String aiCode, BlockPos origin) {
        Integer minX = null;
        Integer minY = null;
        Integer minZ = null;
        Integer maxX = null;
        Integer maxY = null;
        Integer maxZ = null;

        Matcher matcher = BOX_PATTERN.matcher(aiCode);
        while (matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));
            int w = Integer.parseInt(matcher.group(4));
            int h = Integer.parseInt(matcher.group(5));
            int l = Integer.parseInt(matcher.group(6));

            minX = min(minX, x);
            minY = min(minY, y);
            minZ = min(minZ, z);
            maxX = max(maxX, x + w - 1);
            maxY = max(maxY, y + h - 1);
            maxZ = max(maxZ, z + l - 1);
        }

        if (minX == null) {
            return "";
        }

        return "Estimated area (world): X="
            + (origin.getX() + minX) + ".." + (origin.getX() + maxX)
            + ", Y=" + (origin.getY() + minY) + ".." + (origin.getY() + maxY)
            + ", Z=" + (origin.getZ() + minZ) + ".." + (origin.getZ() + maxZ);
    }

    private static Integer min(Integer current, int value) {
        return current == null ? value : Math.min(current, value);
    }

    private static Integer max(Integer current, int value) {
        return current == null ? value : Math.max(current, value);
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private static Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
