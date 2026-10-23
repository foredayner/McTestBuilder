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
    private record BuildRequest(String userPrompt, String basePrompt, boolean webSearch, long startTime) {
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
        boolean webSearch = config.provider == AiProvider.OPENAI
            && (useWebSearch || config.openAiWebSearchEnabled);

        if (webSearch && !OpenAiWebSearchSupport.isModelSupported(config.modelName)) {
            String message = OpenAiWebSearchSupport.unsupportedModelMessage(config.modelName);
            debug("[ERROR] " + message);
            AiNetworking.sendLog(player, "[AI] Error: " + message);
            return;
        }

        AiNetworking.sendDebugLog(player, "[AI] Planning...");
        debug("[TIMER] ===== AI BUILD START =====");
        debug("[INPUT] Prompt: " + prompt);
        if (webSearch) {
            debug("[WEB] OpenAI web search enabled for this request");
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
            String promptText = objectMode
                ? buildObjectPrompt(prompt, MinecraftAI.getFacing())
                : buildPrompt(prompt, worldInfo, webSearch);

            debug("[DEBUG] Sending to AI provider...");
            AiNetworking.sendDebugLog(player, "[AI] Requesting from AI model (" + config.provider.getDisplayName() + ")...");

            sendToAi(player, new BuildRequest(prompt, promptText, webSearch, startTime), promptText, 0);

        } catch (Exception exception) {
            debug("[ERROR] Fatal error: " + exception.getMessage());
            exception.printStackTrace();
            AiNetworking.sendLog(player, "[AI] Error: " + exception.getMessage());
        }
    }

    private static void sendToAi(ServerPlayerEntity player, BuildRequest req, String promptText, int attempt) {
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
                for (String query : AiHttpService.extractOpenAiWebSearchQueries(rawBody)) {
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
    public static String promptFor(ServerPlayerEntity player, String userPrompt, boolean objectMode) {
        MinecraftAI.init(player.getServerWorld(), player.getBlockPos(), player.getYaw(), player.getPitch());
        return objectMode
            ? buildObjectPrompt(userPrompt, MinecraftAI.getFacing())
            : buildPrompt(userPrompt, MinecraftAI.getWorldInfo(30, player.getYaw(), player.getPitch()), false);
    }

    public static String fixPrompt(String basePrompt, String badCode, String error) {
        return basePrompt
            + "\n\nYOUR PREVIOUS SCRIPT FAILED. Fix it.\n"
            + "Error:\n" + truncate(error, 1500) + "\n\n"
            + "Previous script:\n" + truncate(badCode, 6000) + "\n\n"
            + "Return the complete corrected Groovy script only. No markdown, no comments, no explanation.\n";
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

    private static String buildObjectPrompt(String userPrompt, String facing) {
        return "You are a Minecraft prop designer. Write ONLY executable Groovy using the `ai` API.\n"
            + "Object request: " + userPrompt + "\n\n"
            + "Make ONE small, detailed decorative object out of scaled block pieces (display entities), not whole blocks.\n"
            + "Player facing: " + facing + "\n\n"
            + "API (units are blocks; decimals allowed, 0.0625 = one texture pixel):\n"
            + "  ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ)                    // box: min corner + size\n"
            + "  ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ, yaw)               // turned around its centre, degrees\n"
            + "  ai.addPart(block, x, y, z, sizeX, sizeY, sizeZ, yaw, pitch, roll)\n"
            + "  ai.addCylinder(block, centerX, bottomY, centerZ, radius, height)  // upright, 4 parts\n"
            + "  ai.addSphere(block, centerX, centerY, centerZ, radius)              // 20 parts\n"
            + "  ai.getForwardX(distance) -> int | ai.getForwardZ(distance) -> int | ai.searchBlocks(keyword)\n\n"
            + "Rules:\n"
            + "- Use ONLY addPart / addCylinder / addSphere to build. Never placeBlock/placeBox.\n"
            + "- Coordinates are relative to the corner of the player's feet block: +X east, +Y up, +Z south.\n"
            + "- Its FRONT (face, display side, opening) must face the player, i.e. point back toward (0, 0, 0).\n"
            + "- Put it 2 blocks in front of the player, standing on the ground: centre x = ai.getForwardX(2) + 0.5,\n"
            + "  centre z = ai.getForwardZ(2) + 0.5, bottom y = 0. Store these in variables and build around them.\n"
            + "- Unless the request gives a size, make it about 1 block tall. Keep it within 3 blocks in every direction.\n"
            + "- Each part shows one block texture: use concrete, wool, terracotta, glass, iron_block, gold_block,\n"
            + "  quartz_block, planks, etc. for colours and materials.\n"
            + "- Build base -> body -> details with correct proportions. At most 400 parts; prefer fewer, well-placed parts.\n"
            + "- Output Groovy only. No markdown, no comments, no explanation.\n";
    }

    private static String buildPrompt(String userPrompt, String worldInfo, boolean webSearchEnabled) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a Minecraft architect. Write ONLY executable Groovy using the `ai` API.\n");
        prompt.append("Build request: ").append(userPrompt).append("\n\n");

        prompt.append("Absolute instructions:\n");
        prompt.append("1. Prioritize building quality above all else. (Never build flat. Focus on depth, 3D layering, overhangs, and mixing blocks for realistic texture).\n");
        prompt.append("2. Use Web Search actively whenever you need design inspiration or up-to-date block information.\n");
        prompt.append("3. Base your designs on real-world structures and architecture rather than abstract concepts. Build with physical logic (foundations, pillars, beams).\n");
        if (webSearchEnabled) {
            prompt.append("(Web search: ON)\n");
        }
        prompt.append("\n");

        prompt.append("World Information:\n");
        prompt.append(worldInfo).append("\n\n");

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
        prompt.append("- Build in front of the player. The FRONT of the building (main door, facade, windows of the main\n");
        prompt.append("  face) must face the player, i.e. point back toward (0, 0, 0).\n");
        prompt.append("- Inspect terrain before building. Output Groovy only. No markdown, no comments, no explanation.\n");

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
