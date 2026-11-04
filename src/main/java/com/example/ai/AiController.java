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
import com.example.ai.build.Spark;
import com.example.ai.build.BuildRecord;
import com.example.content.GearKind;
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
    private record BuildRequest(String userPrompt, AiPrompt basePrompt, boolean webSearch, long startTime, String mode,
                                String fxHit, String fxIdle, String motion) {
    }

    /** 고쳐서 주문: what the AI is told after the usual order, with the earlier script. */
    static final String REVISE_NOTE = """

            THIS ORDER CHANGES AN EARLIER DESIGN. The order above is how it was first asked for; this is the script that made it:
            ```groovy
            %SCRIPT%
            ```
            THE CHANGE ASKED FOR: %CHANGE%

            Write the whole script again with ONLY that change: keep every other piece, size, position, colour, group,
            animation and effect exactly as it is (copy those lines unchanged). If the change is about an effect or the
            projectile, change only those calls. Answer with the complete new script.""";

    private static String cutPrompt(String text) {
        return text.length() > AiOrder.MAX_PROMPT * 3 ? text.substring(0, AiOrder.MAX_PROMPT * 3) : text;
    }

    /** The prompt for a change of an earlier design: the usual one with its script and the change. */
    public static AiPrompt revisePrompt(AiPrompt base, String script, String change) {
        String note = REVISE_NOTE.replace("%CHANGE%", change.trim()).replace("%SCRIPT%", script.trim());
        int at = base.user().lastIndexOf("\nWrite the Groovy script");
        String user = at < 0 ? base.user() + note : base.user().substring(0, at) + note + base.user().substring(at);
        return new AiPrompt(base.system(), user);
    }

    /** Request modes: "build", "object", or "gear:<kind>" (sword, axe, ...). */
    public static String modeOf(boolean objectMode) {
        return objectMode ? "object" : "build";
    }

    private static GearKind gearOf(String mode) {
        if (mode == null || !mode.startsWith("gear:")) {
            return null;
        }
        String kind = mode.substring(5);
        return GearKind.of(kind.contains(":") ? kind.substring(0, kind.indexOf(':')) : kind);
    }

    /** The order's "모양: 픽셀": draw the equipment as pixel art (the mode ends with ":pixel"). */
    public static boolean pixelOf(String mode) {
        return mode != null && mode.startsWith("gear:") && mode.endsWith(":pixel");
    }

    static final String PIXEL_API = """
            PIXEL ART (this order asks for it: draw the equipment as pixel art, like a good resource pack, not blocky shapes):
              ai.pixelArt(palette, rows)                                      // a flat picture, 1 pixel thick, like a vanilla item
              ai.pixelArt(palette, rows, x, y, z)                             // its bottom-centre at (x, y, z)
              ai.pixelArt(palette, rows, x, y, z, pixelSize)                  // blocks per pixel (default 0.0625 = 1/16)
              ai.pixelArt(palette, rows, x, y, z, pixelSize, yaw, pitch, roll) // turned around its bottom-centre, degrees
              ai.pixelBox(palette, x, y, z, sizeX, sizeY, sizeZ, faces)       // a box (min corner + size) with a picture on its faces
            - palette: one letter per colour: [O: '#1e2430', L: '#e8eef5', S: '#b8c2cc', G: '#d4af37']; a see-through
              colour like '#9fe3ff/50' is fine. '.' (and space) is an empty pixel.
            - rows: equal-length strings, top row first: ['..O..', '.OLO.', ...]. At most 64 x 64.
            - pixelArt lies in the X-Y plane and faces -Z (the viewer); its left column is at -X.
            - pixelBox faces: [front: rows, back: rows, left: rows, right: rows, top: rows, bottom: rows]; leave out faces
              nobody sees. Each picture is stretched over its face (8 x 8 on a 0.5 face = 1/16-block pixels) and is seen from
              outside: front = the -Z side, left column at -X; back = +Z, left column at +X; left = the -X side, left column
              at the back; right = the +X side, left column at the front; top seen from above with the front at the bottom
              row; bottom seen from below with the front at the top row.
            - ai.mirrorOn() also works on pictures (wings, horns, shoulder plates); groups, glow and animations too.
            """;

    static final String PIXEL_STYLE = """
            PIXEL STYLE (this decides the quality):
            - Draw like a resource-pack artist: 2-3 shades per material (light on top and the top-left, dark below and
              on the bottom-right), a few bright highlight pixels and small accents (gems, runes, rivets, stitches).
              Flat pictures (pixelArt) get a 1-pixel outline in a dark shade of the main colour (not pure black); 3D
              boxes need no outline, their shades and edges show the form. Clean, readable shapes; no noise.
            - 3-6 colours, at most 16. Each run of same-coloured pixels becomes one piece: at most 400 pieces in all.
            - Build it in 3D like a Blockbench model: every part is its own ai.pixelBox with pictures on the faces that
              show (blade, guard, grip, pommel, axe head, haft, gems and studs that stand out), sizes and places in whole
              pixels (def u = 0.0625, then 3 * u...). A face picture has one letter per pixel of that face (a 3 x 16-pixel
              blade face is 3 letters x 16 rows). Corners left empty ('.') round a box off. Use ai.pixelArt only for
              really flat bits (a blade tip, a feather, a flat ornament).
            - Hand-held: upright, the hand at (0, 0, 0) on the grip, the item going up (+Y). 16-32 pixels long for normal
              weapons and tools (1-2 blocks), up to 48 for big ones; blades 1-2 pixels thick, handles 2 pixels square.
              A bow is held at its middle.
            - Worn: cover the body part with ai.pixelBox a little outside it (0.03), 8 x 8 pixels per head face, 8 x 12
              for the torso front and back, 4 x 12 for its sides and for each leg face; add things that stick out (horns,
              crests, plumes, wings, feathers, ears) with ai.pixelArt, turned with yaw so they face the right way
              (yaw 90: the picture faces -X; yaw 180: +Z) and mirrored with mirrorOn().
            - Use smooth shapes only where a picture cannot work, in the same flat colours.
            """;

    public static final String EXAMPLE_PIXEL_SWORD = """
        // 3D pixel art knight sword, built like a Blockbench model: each part is a pixel box with its own pictures,
        // sizes and places in whole pixels (u = 1/16 block); the hand is at (0, 0, 0) on the grip, the blade goes up
        def u = 0.0625
        def p = [L: '#e8eef5', S: '#b8c2cc', D: '#7f8b97', E: '#5f6b78', G: '#d4af37', g: '#9c7a1c', B: '#6b4423', b: '#4a2e17', M: '#2ecc71']
        def blade = ['LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD', 'LSD']
        def edge = ['E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E']
        ai.pixelBox(p, -1.5 * u, 4 * u, -0.5 * u, 3 * u, 16 * u, 1 * u, [front: blade, back: blade, left: edge, right: edge])  // blade, 1 pixel thick
        ai.pixelArt(p, ['.L.', 'LSD'], 0, 20 * u, 0)                                                         // flat tip
        ai.pixelBox(p, -4 * u, 2 * u, -1 * u, 8 * u, 2 * u, 2 * u, [front: ['GGGGGGGG', 'gggggggg'], back: ['GGGGGGGG', 'gggggggg'],
          left: ['GG', 'gg'], right: ['GG', 'gg'], top: ['GGGGGGGG', 'GGGGGGGG'], bottom: ['gggggggg', 'gggggggg']])   // cross guard
        ai.pixelBox(p, -0.5 * u, 2.5 * u, -1.5 * u, u, u, 0.5 * u, [front: ['M'], left: ['M'], right: ['M'], top: ['M']])  // gem standing out
        def grip = ['Bb', 'bB', 'Bb', 'bB', 'Bb']
        ai.pixelBox(p, -1 * u, -3 * u, -1 * u, 2 * u, 5 * u, 2 * u, [front: grip, back: grip, left: grip, right: grip])  // grip
        ai.pixelBox(p, -1.5 * u, -5 * u, -1.5 * u, 3 * u, 2 * u, 3 * u, [front: ['GGG', 'ggg'], back: ['GGG', 'ggg'],
          left: ['GGG', 'ggg'], right: ['GGG', 'ggg'], bottom: ['ggg', 'ggg', 'ggg']])                               // pommel
        """;

    public static final String EXAMPLE_PIXEL_STAFF = """
        // 3D pixel art magic staff: a wooden shaft, a gold ring and a purple gem head with cut corners (u = 1/16 block)
        def u = 0.0625
        def pal = [P: '#9b59d0', p: '#6c3483', W: '#f5e6ff', G: '#d4af37', g: '#9c7a1c', B: '#7a5230', b: '#5a3a20']
        def shaft = ['BB', 'Bb', 'BB', 'bB', 'BB', 'Bb', 'BB', 'bB', 'BB', 'Bb', 'BB', 'bB', 'BB', 'Bb', 'BB', 'bB']
        ai.pixelBox(pal, -1 * u, -4 * u, -1 * u, 2 * u, 16 * u, 2 * u, [front: shaft, back: shaft, left: shaft, right: shaft, bottom: ['bb', 'bb']])
        def ring = ['GGG', 'ggg']
        ai.pixelBox(pal, -1.5 * u, 12 * u, -1.5 * u, 3 * u, 2 * u, 3 * u, [front: ring, back: ring, left: ring, right: ring])
        def gem = ['.PP.', 'PWpP', 'PppP', '.PP.']
        ai.pixelBox(pal, -2 * u, 14 * u, -2 * u, 4 * u, 4 * u, 4 * u, [front: gem, back: gem, left: gem, right: gem,
          top: ['.PP.', 'PPPP', 'PPPP', '.PP.'], bottom: ['.pp.', 'pppp', 'pppp', '.pp.']])
        """;

    public static final String EXAMPLE_PIXEL_HELMET = """
        // pixel art knight helmet: an 8 x 8 picture on each face of a box just outside the head, and a red plume
        def p = [O: '#2b3038', I: '#c9d1d9', i: '#8d98a5', K: '#121417', R: '#c0392b', r: '#7b241c']
        def side = ['OOOOOOOO', 'OIIIIIIO', 'OIIIIIIO', 'OIIiIIIO', 'OIIiIIIO', 'OIIIIIIO', 'OIiiiiIO', 'OOOOOOOO']
        ai.pixelBox(p, -0.28, -0.02, -0.28, 0.56, 0.56, 0.56, [
          front: ['OOOOOOOO', 'OIIIIIIO', 'OIiiiiIO', 'OKKKKKKO', 'OIIIIIIO', 'OIIiiIIO', 'OIIiiIIO', 'OOOOOOOO'],
          back: side, left: side, right: side,
          top: ['OOOOOOOO', 'OIIIIIIO', 'OIiIIiIO', 'OIiIIiIO', 'OIiIIiIO', 'OIiIIiIO', 'OIIIIIIO', 'OOOOOOOO']])
        ai.pixelArt(p, ['..RRR', '.RRrR', 'RRrr.', 'Rrr..', '.Or..'], 0, 0.54, 0.05, 0.0625, 90, 0, 0)  // plume, side on
        """;

    public static final String EXAMPLE_PIXEL_WINGS = """
        // pixel art angel wings: one 12 x 8 wing on the upper back, mirrored to the other side
        def p = [O: '#9aa7b8', W: '#ffffff', w: '#dfe7f2']
        ai.mirrorOn()
        ai.pixelArt(p, ['..........OO', '.......OOOWO', '....OOOWWWWO', '..OOWWWWWwWO',
                        '.OWWWwWWWwO.', 'OWWwWWWwOO..', 'OWwWWOOO....', '.OOOO.......'], 0.5, -0.4, 0.2, 0.0625, -20, 0, 0)
        ai.mirrorOff()
        """;

    public static final String EXAMPLE_PIXEL_PANTS = """
        // pixel art jeans for ONE leg (copied to the other): a 4 x 12 picture on each side of a box around the leg
        def p = [O: '#1f2f4a', B: '#3b5b8c', b: '#2c4870', L: '#4f74ad', K: '#6b4423', s: '#c8c8c8']
        def leg = ['KKKK', 'BsBB', 'BBLB', 'BBLB', 'BbBB', 'BBBB', 'BBLB', 'BbBB', 'BBBB', 'BBLB', 'bbbb', 'OOOO']
        ai.pixelBox(p, -0.155, -0.75, -0.155, 0.31, 0.77, 0.31, [front: leg, back: leg, left: leg, right: leg])
        """;


    public static void requestBuild(ServerPlayerEntity player, String prompt) {
        requestBuild(player, prompt, false);
    }

    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch) {
        requestBuild(player, prompt, useWebSearch, false);
    }

    /** objectMode: a small decorative object from scaled block pieces instead of a block build. */
    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch, boolean objectMode) {
        requestBuild(player, prompt, useWebSearch, modeOf(objectMode));
    }

    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch, String mode) {
        requestBuild(player, prompt, useWebSearch, mode, "", "");
    }

    /**
     * fxHit / fxIdle: what the order's effect fields say (equipment only). Empty = no such effect;
     * the AI is told so, and anything it adds anyway is removed.
     */
    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch, String mode,
                                    String fxHit, String fxIdle) {
        requestBuild(player, prompt, useWebSearch, mode, fxHit, fxIdle, "");
    }

    /** motion: the weapon's attack motion picked in the order (see GearMotions), or empty for the normal swing. */
    public static void requestBuild(ServerPlayerEntity player, String prompt, boolean useWebSearch, String mode,
                                    String fxHit, String fxIdle, String motion) {
        AiOrder order = new AiOrder();
        order.prompt = prompt;
        order.web = useWebSearch;
        order.mode = mode;
        order.fxHit = fxHit;
        order.fxIdle = fxIdle;
        order.motion = motion;
        requestOrder(player, order);
    }

    /**
     * An order (from the order screen's packet or /aibuild). For 고쳐서 주문 (baseScript or a kept
     * record's id), the AI gets the earlier script and the change, and the new design keeps the rest.
     */
    public static void requestOrder(ServerPlayerEntity player, AiOrder order) {
        order.cleaned();
        if (!order.baseId.isEmpty() && player.getServer() != null) {
            // The world's own copy of the record is the one trusted, when it has it.
            com.example.ai.build.BuildRecord kept = com.example.ai.build.SharedHistory.get(player.getServer(), order.baseId);
            if (kept != null && kept.revisable()) {
                order.baseScript = kept.script;
                order.basePrompt = kept.prompt;
            }
        }
        if (!order.baseId.isEmpty() && !order.isRevision()) {
            AiNetworking.sendLog(player, "[AI] Error: 이 기록에는 AI 스크립트가 없어서 고쳐서 주문할 수 없어요. 다시 주문을 써 주세요.");
            return;
        }
        boolean revision = order.isRevision();
        String prompt = revision ? cutPrompt(order.basePrompt + " (수정: " + order.prompt + ")") : order.prompt;
        // The AI is asked with the first description; the change comes with the script.
        String described = revision ? order.basePrompt : order.prompt;
        boolean useWebSearch = order.web;
        String mode = order.mode;
        String motion = order.motion;
        String hitText = order.fxHit;
        String idleText = order.fxIdle;
        boolean objectMode = !"build".equals(mode);
        // The shared 작업 일지: one entry per request, collecting what this request reports.
        WorkJournal.start(player, prompt, mode, hitText, idleText, com.example.content.GearMotions.normalize(motion));
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
            AiPrompt promptText = withWebNote(gearOf(mode) != null
                ? gearPrompt(described, gearOf(mode), hitText, idleText, pixelOf(mode))
                : objectMode ? objectPrompt(described) : buildingPrompt(described, worldInfo, webSearch), webSearch);
            if (revision) {
                promptText = revisePrompt(promptText, order.baseScript, order.prompt);
                AiNetworking.sendLog(player, "[AI] 이전 설계를 고치는 중이에요: " + order.prompt);
            }

            debug("[DEBUG] Sending to AI provider...");
            AiNetworking.sendDebugLog(player, "[AI] Requesting from AI model (" + config.provider.getDisplayName() + ")...");

            sendToAi(player, new BuildRequest(prompt, promptText, webSearch, startTime, mode, hitText, idleText,
                com.example.content.GearMotions.normalize(motion)), promptText, 0);

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
        GearKind gear = gearOf(req.mode());
        Evaluation eval = evaluate(player, aiCode, gear != null || "object".equals(req.mode()));
        BuildPlan plan = eval.plan();
        ScriptExecutor.ExecutionResult result = eval.result();
        String failure = eval.failure();
        if (failure == null && gear != null) {
            failure = gearProblem(plan, gear);
            if (failure == null) {
                failure = effectProblem(plan, gear, req.fxHit(), req.fxIdle());
            }
            if (failure != null) {
                AiNetworking.sendLog(player, "[Review] " + failure);
            }
        }
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
            // Kept with the design: the order (다시 주문) and the script (고쳐서 주문).
            record.script = aiCode;
            record.fxHit = req.fxHit();
            record.fxIdle = req.fxIdle();
            if (gear != null) {
                record.kind = "gear";
                record.gear = gear.id();
                record.pixel = pixelOf(req.mode());
                applyEffectRequest(plan, gear, req.fxHit(), req.fxIdle());
                plan.motion = com.example.content.GearMotions.applies(gear) ? req.motion() : "";
                record.motion = plan.motion;
            }
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
        return evaluate(player, aiCode, false);
    }

    /** gear: equipment, drawn on a finer grid. */
    public static Evaluation evaluate(ServerPlayerEntity player, String aiCode, boolean gear) {
        return evaluate(player.getServerWorld(), player.getBlockPos(), player.getYaw(), player, aiCode, gear);
    }

    /** As above, without a player (e.g. for other mods): the script runs at {@code origin} looking along {@code yaw}. */
    public static Evaluation evaluate(ServerWorld world, BlockPos origin, float yaw, ServerPlayerEntity player, String aiCode, boolean gear) {
        MinecraftAI.init(world, origin, yaw, 0f, player);
        BuildPlan plan = new BuildPlan();
        plan.facing = BuildPlan.normalizeFacing(MinecraftAI.getFacing());

        ScriptExecutor.ExecutionResult result = null;
        String failure;
        List<String> review = ScriptLinter.lint(aiCode);
        if (!review.isEmpty()) {
            failure = "Code review found problems:\n- " + String.join("\n- ", review);
        } else {
            MinecraftAI.startRecording(plan, gear);
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
        return promptFor(player, userPrompt, modeOf(objectMode));
    }

    public static AiPrompt promptFor(ServerPlayerEntity player, String userPrompt, String mode) {
        return promptFor(player, userPrompt, mode, "", "");
    }

    public static AiPrompt promptFor(ServerPlayerEntity player, String userPrompt, String mode, String fxHit, String fxIdle) {
        if (gearOf(mode) != null) {
            return gearPrompt(userPrompt, gearOf(mode), fxHit, fxIdle, pixelOf(mode));
        }
        boolean objectMode = "object".equals(mode);
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

    public static String gearProblem(BuildPlan plan) {
        return gearProblem(plan, GearKind.SWORD);
    }

    /** Hand-held equipment without ai.oversize(): up to a greatsword or scythe taller than the wielder. */
    public static final double HAND_MAX_LONG = 3.5;
    public static final double HAND_MAX_WIDE = 2.4;

    /** Largest size (blocks) of equipment the order asked to be big (ai.oversize()). */
    public static final double OVERSIZE_MAX = 5.0;

    /**
     * Hand-held equipment must fit in the hand (about the size of vanilla tools, at most 2.6
     * long); worn equipment must stay close to the head or torso it is worn on. Equipment the
     * order asked to be big (ai.oversize()) may be up to {@link #OVERSIZE_MAX} blocks.
     */
    public static String gearProblem(BuildPlan plan, GearKind kind) {
        if (plan.oversize && plan.ops.isEmpty() && plan.isObject()) {
            double[] b = plan.partBounds();
            List<String> problems = new java.util.ArrayList<>();
            String[] axis = { "x", "y", "z" };
            for (int i = 0; i < 3; i++) {
                // The big thing itself is up to OVERSIZE_MAX; a string or pole holding it may add a little more.
                if (b[i + 3] - b[i] > OVERSIZE_MAX + 1.5) {
                    problems.add(String.format(java.util.Locale.ROOT, "it is %.2f blocks along %s; even a big item is at most %.1f "
                        + "(plus a short string or pole holding it).", b[i + 3] - b[i], axis[i], OVERSIZE_MAX));
                }
            }
            double reach = 0;
            for (int i = 0; i < 6; i++) {
                reach = Math.max(reach, Math.abs(b[i]));
            }
            if (reach > OVERSIZE_MAX + 1.1) {
                problems.add(String.format(java.util.Locale.ROOT, "it reaches %.2f blocks from (0, 0, 0); keep it within %.1f.",
                    reach, OVERSIZE_MAX + 1));
            }
            if (!kind.worn() && (b[1] > 0.05 || b[0] > 0.05 || b[3] < -0.05)) {
                problems.add("the hand holds it at (0, 0, 0): the handle must pass through that point.");
            }
            return problems.isEmpty() ? null : PLAN_PROBLEMS + "\n- " + String.join("\n- ", problems);
        }
        if (kind.worn() && plan.ops.isEmpty() && plan.isObject()) {
            double[] b = plan.partBounds();
            double[] lim = switch (kind.slot) {
                case HEAD -> new double[] { -0.7, -0.25, -0.7, 0.7, 1.3, 0.7 };
                case LEGS -> new double[] { -0.3, -0.95, -0.45, 0.6, 0.35, 0.45 };
                case FEET -> new double[] { -0.3, -0.95, -0.5, 0.5, -0.1, 0.4 };
                default -> new double[] { -1.3, -1.1, -0.5, 1.3, 0.7, 1.0 };
            };
            String part = switch (kind.slot) {
                case HEAD -> "head";
                case LEGS -> "leg";
                case FEET -> "foot";
                default -> "torso";
            };
            String[] axis = { "x", "y", "z" };
            List<String> problems = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                // A little slack: smooth shapes may grow by part of a grid pixel.
                if (b[i] < lim[i] - 0.08 || b[i + 3] > lim[i + 3] + 0.08) {
                    problems.add(String.format(java.util.Locale.ROOT, "it reaches %s = %.2f..%.2f; keep it within %.2f..%.2f "
                        + "(it must stay on the %s; only if the request asks for it to be big, call ai.oversize()).",
                        axis[i], b[i], b[i + 3], lim[i], lim[i + 3], part));
                }
            }
            return problems.isEmpty() ? null : PLAN_PROBLEMS + "\n- " + String.join("\n- ", problems);
        }
        if (!plan.ops.isEmpty()) {
            return PLAN_PROBLEMS + "\n- equipment must be made only of pieces (addPart / smooth shapes), no blocks.";
        }
        double[] b = plan.partBounds();
        List<String> problems = new java.util.ArrayList<>();
        if (b[4] - b[1] > HAND_MAX_LONG) {
            problems.add(String.format(java.util.Locale.ROOT, "it is %.2f blocks long; keep it at most %.1f "
                + "(only if the request asks for it to be giant, call ai.oversize()).", b[4] - b[1], HAND_MAX_LONG));
        }
        if (b[3] - b[0] > HAND_MAX_WIDE || b[5] - b[2] > HAND_MAX_WIDE) {
            problems.add(String.format(java.util.Locale.ROOT, "it is %.2f wide (X) and %.2f deep (Z); keep both at most %.1f.",
                b[3] - b[0], b[5] - b[2], HAND_MAX_WIDE));
        }
        if (b[1] > 0.05 || b[4] < 0.2 || b[0] > 0.05 || b[3] < -0.05) {
            problems.add("the hand holds it at (0, 0, 0): the handle must pass through that point, with the item going up (+Y).");
        }
        return problems.isEmpty() ? null : PLAN_PROBLEMS + "\n- " + String.join("\n- ", problems);
    }

    public static final String EXAMPLE_SWORD = """
        // plain steel sword, about 1.45 long; the hand holds it at (0, 0, 0), blade up (+Y), flat side facing -Z
        ai.addCapsule('dark_oak_planks', 0, -0.12, 0, 0, 0.22, 0, 0.045)            // grip
        ai.addSphere('gold_block', 0, -0.15, 0, 0.06)                               // pommel
        ai.addRoundBox('gold_block', -0.2, 0.22, -0.05, 0.4, 0.07, 0.1, 0.02)      // cross guard
        ai.addRoundBox('iron_block', -0.075, 0.29, -0.025, 0.15, 0.95, 0.05, 0.01) // blade
        ai.addRoundBox('light_gray_concrete', -0.02, 0.32, -0.03, 0.04, 0.85, 0.06, 0)  // groove down the blade
        ai.addCone('iron_block', 0, 1.24, 0, 0.075, 0.16)                           // tip
        """;

    public static final String EXAMPLE_AXE = """
        // red fire axe, about 1.2 long; hand at (0, 0, 0), handle up (+Y), blade out to +X
        ai.addCapsule('red_concrete', 0, -0.1, 0, 0, 1.15, 0, 0.04)                 // painted handle
        ai.addRoundBox('black_concrete', -0.05, -0.05, -0.05, 0.1, 0.3, 0.1, 0.03)  // rubber grip
        ai.addRoundBox('red_concrete', -0.08, 0.88, -0.06, 0.18, 0.2, 0.12, 0.02)   // head socket
        ai.addEllipsoid('red_concrete', 0.24, 0.98, 0, 0.16, 0.16, 0.03)            // blade
        ai.addEllipsoid('iron_block', 0.36, 0.98, 0, 0.06, 0.15, 0.032)             // sharpened steel edge
        ai.addCone('red_concrete', -0.1, 0.98, 0, 0.05, 0.14)                       // back spike (pointing up is fine)
        """;

    /** What every equipment prompt says about style: make what was asked, in its own style. */
    static final String GEAR_STYLE = """
            STYLE (this decides the quality):
            - PLAN FIRST: before the code, write short // comments listing its real parts and sizes, then build them.
            - Make exactly what the request describes, in the style it implies (everyday, realistic, cute, toy, sci-fi,
              fantasy, a joke...). Do not add armour, gems or fantasy decoration it did not ask for.
            - Get the subject right first: its recognisable silhouette, proportions and colours. If it names a known
              thing, reproduce that thing's distinctive features.
            - Big shapes first, then medium parts, then small details. Details (eyes, mouths, buttons, patterns, emblems,
              seams) are 3D shapes standing a little out of the surface, big enough to read from a few blocks away.
            - Pick blocks for colour and texture that match the real material: *_concrete (flat, bright colours),
              *_terracotta (muted colours, skin, earth), *_wool (cloth, fur, knit), planks and logs (wood), iron_block /
              light_gray_concrete (metal), gold_block, glass, glowstone / sea_lantern (lights), white and black concrete
              (eyes). ai.searchBlocks(keyword) finds others.
            - One darker shade for undersides and creases, one lighter shade for highlights.
            - Overlap pieces slightly so nothing floats (a floating thing hangs on a thin string or rod).
            - Keep parts at least 0.03 thick. Prefer a few smooth shapes over many tiny boxes. At most 400 pieces after merging.
            """;

    /** Added to the request when web search is on: look the thing up and build what was found. */
    static final String WEB_NOTE = """
            Web search is ON for this request. Use it to find out what the requested thing really looks like:
            - Search for its appearance: shape, colours, parts, proportions, markings, how it is worn or placed. For an
              item that belongs to a character, series or brand, search for that exact version.
            - If the request could mean several designs (e.g. a series where each character has their own), use the one
              the request names, otherwise the most iconic one, and say which in a comment.
            - Search results are text, so turn every described feature into shapes and colours. Before the code, write
              // comments listing the features you found (3-8 lines), then build every one of them. What you found decides
              the shape and colours: never fall back to a generic version of the thing.
            """;

    /** The request's prompt with the web search note (when web search is on for it). */
    public static AiPrompt withWebNote(AiPrompt prompt, boolean webSearch) {
        if (!webSearch) {
            return prompt;
        }
        String user = prompt.user();
        int at = user.lastIndexOf("\nWrite the Groovy script");
        user = at < 0 ? user + "\n" + WEB_NOTE : user.substring(0, at) + "\n" + WEB_NOTE + user.substring(at);
        return new AiPrompt(prompt.system(), user);
    }

    /** Instructions for equipment (fixed part); the kind and the request come in the user message. */
    static final String GEAR_SYSTEM = """
            You are an expert 3D modeller of hand-held items for Minecraft. Write ONLY executable Groovy using the `ai` API.

            Model ONE hand-held item out of pieces. It is drawn as the item itself, held in the hand, in first and third
            person. Smooth shapes are drawn on a fine 1/32-block grid (a coarser one if the item is very big or busy).

            FRAME:
            - Units are blocks (a vanilla sword is about 1.2 long). +X = right, +Y = up, +Z = away from the viewer.
            - The hand holds it at (0, 0, 0). The handle (or the part you hold) passes through that point and the item
              goes UP (+Y); the end below the hand may go to about y = -0.2. (A bow is held at its middle: see its kind.)
            - The front (the side with the most detail) faces -Z, the viewer.

            SIZE (decide from what it is; the player is 1.8 tall):
            - Normal weapons and tools (sword, axe, pickaxe, wand...): like vanilla, 1.0-1.6 long.
            - Large ones by nature (greatsword, zweihander, scythe, war hammer, halberd, giant club, guandao, big
              anime-style blades): as big as the wielder or bigger, 1.8-3.5 long, with heads or blades up to 2.4 wide.
              Make them heavy and bold: thick blades, big heads, a long two-handed grip (0.4-0.7) at the bottom.
            - Limits without asking: 3.5 long (Y), 2.4 wide (X and Z).
            - Only if the request asks for it to be even bigger (huge, giant, 거대한, or a size in blocks over 3.5): call
              ai.oversize() first; then it may be up to 5 blocks in each direction.

            %s
            %s
            CODE RULES:
            - Block names and colours are quoted strings: 'iron_block', '#c0392b'.
            - Loops: for (int i = 0; i < n; i++) or for (i in 0..<n). Never closures: no .each { }, .times { }, { -> }.
            - One flat script: never define methods or functions (no def name(...) { }, no classes); repeat code in loops.
            - No methods, classes or imports. Output Groovy only; no markdown, no explanation (short // comments are fine).

            EXAMPLES (only to show the API and the frame; make what the request asks, do not copy):
            %s
            %s
            """;

    public static final String EXAMPLE_HELMET = """
        // bear mascot head, bigger than the head, face toward -Z; the head is the box x -0.25..0.25, y 0..0.5, z -0.25..0.25
        ai.addEllipsoid('brown_terracotta', 0, 0.3, 0, 0.4, 0.38, 0.38)             // round head around the wearer's head
        ai.addCylinder('brown_concrete', 0, -0.1, 0, 0.27, 0.08)                     // darker neck rim
        ai.addEllipsoid('white_terracotta', 0, 0.2, -0.33, 0.17, 0.12, 0.1)         // muzzle standing out
        ai.addEllipsoid('black_concrete', 0, 0.26, -0.43, 0.06, 0.04, 0.03)         // nose
        ai.addCapsule('black_concrete', -0.05, 0.15, -0.42, 0.05, 0.15, -0.42, 0.015)  // mouth line
        ai.mirrorOn()
        ai.addSphere('white_concrete', 0.14, 0.4, -0.31, 0.065)                     // eye
        ai.addSphere('black_concrete', 0.14, 0.4, -0.355, 0.04)                     // pupil
        ai.addSphere('white_concrete', 0.155, 0.415, -0.39, 0.014)                  // shine
        ai.addEllipsoid('pink_terracotta', 0.24, 0.24, -0.3, 0.05, 0.03, 0.025)     // cheek
        ai.addSphere('brown_terracotta', 0.3, 0.62, 0, 0.12)                        // ear
        ai.addEllipsoid('pink_terracotta', 0.3, 0.62, -0.1, 0.07, 0.07, 0.03)       // inside of the ear
        ai.mirrorOff()
        """;

    public static final String EXAMPLE_HAT = """
        // knitted beanie with a pompom; sits on the top of the head and leaves the face free
        ai.addEllipsoid('red_wool', 0, 0.5, 0, 0.29, 0.21, 0.29)                    // knitted crown
        ai.addCylinder('white_wool', 0, 0.38, 0, 0.3, 0.1)                           // folded cuff
        ai.addSphere('white_wool', 0, 0.75, 0, 0.09)                                 // pompom
        for (int i = 0; i < 3; i++) {
            ai.addCylinder('red_terracotta', 0, 0.52 + i * 0.07, 0, 0.27 - i * 0.04, 0.015)  // knit rows (darker)
        }
        """;

    public static final String EXAMPLE_WINGS = """
        // white bird wings on a harness; the torso is the box x -0.25..0.25, y -0.75..0, z -0.125..0.125, back toward +Z
        ai.addRoundBox('brown_terracotta', -0.27, -0.45, -0.15, 0.54, 0.06, 0.32, 0.02)  // harness around the chest
        ai.mirrorOn()
        ai.addEllipsoid('white_wool', 0.45, -0.2, 0.22, 0.4, 0.18, 0.05)                 // upper wing
        ai.addEllipsoid('white_wool', 0.55, -0.42, 0.24, 0.35, 0.14, 0.04)               // lower feathers
        ai.addEllipsoid('light_gray_wool', 0.75, -0.5, 0.24, 0.2, 0.08, 0.045)           // feather tips (shadow tone)
        ai.addCapsule('white_concrete', 0.12, -0.15, 0.17, 0.85, 0.0, 0.22, 0.04)        // wing bone
        ai.mirrorOff()
        """;

    public static final String EXAMPLE_PANTS = """
        // jeans for ONE leg (drawn mirrored on the other); the leg is x -0.125..0.125, y -0.75..0, +X is the outer side
        ai.addRoundBox('blue_terracotta', -0.155, -0.72, -0.155, 0.31, 0.78, 0.31, 0.03)      // leg from the hip down
        ai.addRoundBox('brown_terracotta', -0.16, -0.02, -0.16, 0.32, 0.1, 0.32, 0.02)        // belt at the waist
        ai.addRoundBox('light_blue_terracotta', -0.16, -0.74, -0.16, 0.32, 0.06, 0.32, 0.02)  // rolled cuff
        ai.addRoundBox('blue_concrete', 0.15, -0.5, -0.06, 0.02, 0.12, 0.12, 0.01)            // pocket on the outer side
        """;

    public static final String EXAMPLE_SHOES = """
        // a sneaker for ONE foot (drawn mirrored on the other); the sole is at the bottom of the leg, the toe toward -Z
        ai.addRoundBox('white_concrete', -0.15, -0.8, -0.24, 0.3, 0.07, 0.4, 0.03)       // sole
        ai.addRoundBox('red_wool', -0.145, -0.73, -0.22, 0.29, 0.16, 0.37, 0.05)         // upper
        ai.addRoundBox('white_wool', -0.135, -0.6, -0.14, 0.27, 0.12, 0.27, 0.04)        // ankle collar
        ai.addRoundBox('white_concrete', -0.04, -0.6, -0.235, 0.08, 0.1, 0.02, 0.01)     // laces
        """;

    /** Instructions for worn equipment (fixed part). */
    static final String WEAR_SYSTEM = """
            You are an expert 3D modeller of things people wear in Minecraft: hats, masks, costume and mascot heads,
            helmets, glasses, hair, clothes, armour, backpacks, wings, capes, balloons and anything else asked for.
            Write ONLY executable Groovy using the `ai` API.

            Model ONE worn item out of pieces. It is drawn on the wearer and moves with their head or torso.
            Smooth shapes are drawn on a fine 1/32-block grid (a coarser one if the item is very big or busy).

            FRAME (units are blocks; +Y = up, -Z = the wearer's front, +X = the right side as seen by someone facing them):
            - HEAD items: the head is the box x -0.25..0.25, y 0..0.5, z -0.25..0.25; (0, 0, 0) is the bottom centre of
              the head (the neck). The face is on the -Z side.
            - CHEST items: the torso is the box x -0.25..0.25, y -0.75..0, z -0.125..0.125; (0, 0, 0) is the top centre of
              the torso (between the shoulders). The front is -Z, the back is +Z. The head is above, y 0..0.5.
              The arms hang beside the torso (x beyond +-0.25), so keep clothes off them.
            - LEGS and FEET items: model ONE leg; it is copied, mirrored, onto the other leg. The leg is the box
              x -0.125..0.125, y -0.75..0, z -0.125..0.125; (0, 0, 0) is the top centre of the leg (the hip), the foot is
              at the bottom (y -0.75), the front is -Z and +X is the OUTER side of the leg (keep the inner side, x below
              -0.15, free: the other leg is there). Pants cover the leg from the hip down; shoes cover the bottom, from
              y -0.8 up, with the toe toward -Z.
            - Pieces inside the body part are hidden, which is fine.

            SIZE (decide from what it is):
            - Fitted things (helmets, hoods, glasses, clothes, armour) sit 0.02-0.06 outside the body part.
            - Things that are naturally bigger (mascot or animal heads, big masks, big hair, tall hats, wings) may be
              bigger: a mask or costume head can be about 1.5 times the head, with its face features standing out.
            - Normal limits: HEAD x and z -0.7..0.7, y -0.25..1.3; CHEST x -1.3..1.3, y -1.1..0.7, z -0.5..1.0;
              LEGS x -0.3..0.6, y -0.95..0.35, z -0.45..0.45; FEET x -0.3..0.5, y -0.95..-0.1, z -0.5..0.4.
            - Only if the request asks for it to be big (big, huge, giant, 거대한, 큰, or a size in blocks, e.g. a
              4-block balloon floating above the head): call ai.oversize() first; then it may be up to 5 blocks in each
              direction, within 6 blocks of (0, 0, 0). Something floating above is held by a thin string (ai.addTube).

            %s
            %s
            CODE RULES:
            - Block names and colours are quoted strings: 'red_wool', '#c0392b'.
            - Loops: for (int i = 0; i < n; i++) or for (i in 0..<n). Never closures: no .each { }, .times { }, { -> }.
            - One flat script: never define methods or functions (no def name(...) { }, no classes); repeat code in loops.
            - No methods, classes or imports. Output Groovy only; no markdown, no explanation (short // comments are fine).

            EXAMPLES (only to show the API and the frame; make what the request asks, do not copy):
            %s
            %s
            %s
            """;

    /**
     * Keep only the effects the order asked for: no attack/arrow text = no attack effects (and no
     * trail); no idle text = no effects while held or worn, no movement and no glow (worn
     * equipment has no attack effect).
     */
    public static void applyEffectRequest(BuildPlan plan, GearKind kind, String fxHit, String fxIdle) {
        if (plan.fx == null) {
            plan.fx = new Spark.Effects();
        }
        if (fxHit == null || fxHit.isBlank() || kind.worn()) {
            plan.fx.attack.clear();
            plan.fx.trail.clear();
            plan.fx.shot = null;
        }
        if (kind.worn()) {
            plan.fx.shot = null;
        }
        if (kind != GearKind.BOW && !kind.worn()) {
            // A melee weapon's shot is only its extras (projectiles sent with a swing); they may have a trail.
            if (plan.fx.shot != null) {
                Spark.Shot extras = new Spark.Shot();
                extras.extras = plan.fx.shot.extras;
                extras.text = plan.fx.shot.text;
                extras.order = plan.fx.shot.order;
                extras.facing = plan.fx.shot.facing;
                extras.hit = plan.fx.shot.hit;
                extras.gravity = plan.fx.shot.gravity;
                extras.size = plan.fx.shot.size;
                extras.color = plan.fx.shot.color;
                extras.color2 = plan.fx.shot.color2;
                extras.model = plan.fx.shot.model;
                extras.tilt = plan.fx.shot.tilt;
                extras.thickness = plan.fx.shot.thickness;
                plan.fx.shot = extras.hasExtras() ? extras.clamped() : null;
            }
            if (plan.fx.shot == null) {
                plan.fx.trail.clear();
            }
        }
        if (fxIdle == null || fxIdle.isBlank()) {
            plan.fx.idle.clear();
            plan.anims.clear();
            plan.aura = null;
        }
    }

    /**
     * The order asked for an effect but the script made none for it: send it back. (The other
     * way round, effects that were not asked for, is handled by removing them.)
     */
    public static String effectProblem(BuildPlan plan, GearKind kind, String fxHit, String fxIdle) {
        List<String> problems = new java.util.ArrayList<>();
        Spark.Effects fx = plan.fx == null ? new Spark.Effects() : plan.fx;
        if (kind == GearKind.BOW && fxHit != null && !fxHit.isBlank() && fx.attack.isEmpty() && fx.trail.isEmpty()
            && (fx.shot == null || !fx.shot.beam() && fx.shot.letters().length == 0 && fx.shot.model.isEmpty()) && plan.arrowShapes() == 0) {
            problems.add("the order asks for an arrow effect (\"" + fxHit.trim() + "\") but the script makes none: "
                + "call ai.effect('trail', [...]) for the flight and/or ai.effect('attack', [...]) for where it lands.");
        } else if (kind != GearKind.BOW && !kind.worn() && fxHit != null && !fxHit.isBlank() && fx.attack.isEmpty()
            && (fx.shot == null || !fx.shot.hasExtras())) {
            problems.add("the order asks for an effect on attack (\"" + fxHit.trim() + "\") but the script makes none: "
                + "call ai.effect('attack', [...]) with a shape, colours and moves that match it.");
        }
        if (fxIdle != null && !fxIdle.isBlank() && fx.idle.isEmpty() && plan.anims.isEmpty() && plan.aura == null) {
            problems.add("the order asks for an effect while " + (kind.worn() ? "worn" : "held") + " (\"" + fxIdle.trim()
                + "\") but the script makes none: use ai.effect('idle', [...]), ai.groupStart/animate, ai.glow and/or ai.aura.");
        }
        return problems.isEmpty() ? null : PLAN_PROBLEMS + "\n- " + String.join("\n- ", problems);
    }

    /** Bows: what is shot (Spark.Shot), from the order's arrow line. */
    static final String SHOT_API = """
            WHAT THE BOW SHOOTS (bows only; decide it from the order's arrow line, leave it out for a plain arrow):
              ai.shot([settings])
                kind: 'arrow' (something flies: the shape between ai.arrowStart()/arrowEnd(), which may be ANYTHING the
                      order says - a bubble, a fireball, a shuriken, a fish - not only an arrow) or
                      'beam' (nothing flies: a ray of light reaches the target at once, up to 64 blocks, and stops at the
                      first mob or block; it hits like a fully drawn arrow; use it for lasers, rays, beams, light shots)
                model: a SMOOTH SHAPE DRAWN BY THE MOD instead of a modelled one - USE IT for these (it looks far better
                       than pieces): 'crescent' (a sword wave / 검기, 참격), 'shuriken' (수리검), 'disc', 'ring', 'star',
                       'orb' (a glowing ball: magic bolt, fireball, bubble), 'arrowhead', 'feather'; with color (its
                       edge), color2 (its glow), thickness (0.02-0.3, thin 0.04-0.08), size (blocks across)
                facing: 'flight' (points where it flies, like an arrow; the default), 'spin' (turns FLAT like a thrown
                        shuriken or disc), 'tumble' (end over end: thrown axes, hammers), 'roll' (turns round its
                        flight like a drill or a spiralling bolt), 'fixed' (level, never turning: balls, bubbles, orbs)
                tilt: degrees turned round its flight: 0 = lying flat (a horizontal wave, 가로), 90 = standing (세로)
                hit: 'stick' (stays where it hits, like an arrow; the default) or 'vanish' (pops with a small burst:
                     bubbles, fireballs, magic bolts, bullets)
                gravity: true (falls in an arc, the default) or false (flies straight: magic, lasers, bullets)
                size: drawn length in blocks, 0.25-2 (a head-sized bubble 0.6-0.8, a huge fireball 1.5); leave it
                      out to draw the shape as designed. Bigger shots also hit a little easier.
                color, color2, width (0.05-1), ticks (4-20, how long the beam shows): the beam, or the vanish burst
                opacity: beam only, 0-1 (1 the default; 0.2-0.4 a faint beam; 0 an INVISIBLE beam: nothing is seen
                         until the landing effects play where it hits; use 0 for "투명한/보이지 않는" beams)
                text: LETTERS instead of a modelled shape: each shot is one character of it, drawn in the game's font
                      with some thickness (color = its colour, size = its height). For letters, numbers, notes, symbols:
                      'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '0123456789', '♪♫♬', '★☆', '가나다라마바사'. Then make NO arrow shape.
                order: with several shapes or letters, 'random' (the default) or 'cycle' (in turn: 'HELLO' spells it out)
              SEVERAL SHAPES: when the order wants different things to fly (a heart, a star and a note), make up to 8
                shapes, each between ai.arrowStart(n) and ai.arrowEnd() with n = 1, 2, 3...; each shot is one of them.
              The arrow line's look (trail, landing effects) still comes from ai.effect('trail'/'attack').
              e.g. a laser gun: ai.shot([kind: 'beam', color: '#ff4040', color2: '#ffc0c0', width: 0.15, ticks: 6])
                   an invisible ray that strikes lightning: ai.shot([kind: 'beam', opacity: 0]) + ai.preset('attack', 'lightning')
                   a bubble wand: ai.shot([facing: 'fixed', hit: 'vanish', gravity: false, size: 0.7, color: '#c0f0ff'])
                   a shuriken: ai.shot([model: 'shuriken', facing: 'spin', size: 0.5, thickness: 0.05, color: '#c8ccd4'])
                   a horizontal sword wave: ai.shot([model: 'crescent', facing: 'flight', size: 1.6, color: '#80d0ff',
                                                     color2: '#ffffff', gravity: false, hit: 'vanish'])
                   a vertical one: the same with tilt: 90
                   a keyboard shooting random letters: ai.shot([text: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', facing: 'fixed',
                                                                gravity: false, hit: 'vanish', size: 0.7, color: '#80ffea'])
            """;

    /** Extra projectiles with each shot or swing (Spark.Extra), for bows and melee weapons. */
    static final String EXTRA_API = """
            EXTRA PROJECTILES (only when the attack/arrow line asks for more than one thing flying: "두 개가 나선형으로",
            "3발", "부채꼴", "따라오는", "휘어서", "유도", "화살비", "쏟아지는", "갈라지는", "분열"...). Inside ai.shot:
              ai.shot([extra: [[count: 2, path: 'spiral'], [count: 3, path: 'split']], ...other shot settings])
              each group: count 1-8 (at most 12 in all), path, and as fits:
                'spread'  a fan side to side (angle: degrees each side, 15)          - 3발 산탄, 부채꼴
                'ring'    a cone all round the aim (angle 10)                        - 사방으로 퍼짐
                'spiral'  turning round the main shot as it flies (radius 0.5, turns per second 2) - 나선형으로 감싸며
                'trail'   one after another on the same line                         - 연사, 따라오는
                'arc'     curving out and back into where it is aimed (angle 40)     - 휘어 들어오는
                'homing'  after what it is aimed at                                  - 유도, 추적
                'rain'    falling from the sky onto where it is aimed (radius 2.5)   - 화살비, 별똥별
                'split'   bursting out where the main shot lands (angle 50)          - 착탄 시 분열, 파편
                delay: ticks before they go; shape: 1-8 = which ai.arrowStart(n) shape (0 = the main shot's);
                size: drawn size
              Each extra hurts HALF as much as the main shot, costs no arrow and cannot be picked up; extras play no
              landing effects of their own. With a beam, arc/homing/rain/split aim at where the beam stopped.
              e.g. two arrows spiralling round the main one: ai.shot([extra: [[count: 2, path: 'spiral']]])
                   a beam with two arrows curving into the same spot: ai.shot([kind: 'beam', extra: [[count: 2, path: 'arc']]])
            """;

    /** Melee weapons: what they may send with a swing. */
    static final String MELEE_SHOT = """
            PROJECTILES FROM A SWING (melee weapons; only when the attack line asks for something flying off the weapon:
            검기, 날아가는 참격, 마법탄, 불꽃 구슬...): ai.shot([extra: [[count: 1, path: 'trail']], facing: 'spin',
              hit: 'vanish', gravity: false, size: 0.8]) - the extras leave with the swing and pass the one it hits
              directly. Their shape: pieces between ai.arrowStart(n)/arrowEnd() (pointing up, +Y) or text: '...'.
              가로 (horizontal) means lying flat: tilt 0 for a shot, plane 'flat' for an effect; 세로 (vertical) means standing:
              tilt 90, plane 'front'. Do not draw a flying wave with effect dots: use the shot below.
              A sword wave (검기): ai.shot([extra: [[count: 1, path: 'trail']], model: 'crescent', gravity: false,
                hit: 'vanish', size: 1.6, color: '#80d0ff', color2: '#ffffff'])  (tilt: 90 for an upright one)
              Thrown shurikens in a fan: ai.shot([extra: [[count: 3, path: 'spread', angle: 20]], model: 'shuriken',
                facing: 'spin', size: 0.5, thickness: 0.05, color: '#c8ccd4'])
            """;

    /** API and rules for effects, added to the equipment prompts. */
    static final String EFFECT_API = effectApi();

    private static String effectApi() {
        return EFFECT_API_TEXT.replace("%PRESETS%", com.example.ai.build.EffectPresets.catalogue());
    }

    private static final String EFFECT_API_TEXT = """
            EFFECTS (use ONLY what the request's effect lines ask for; if a line says "none", make nothing for it).
            Effects are dots arranged into a shape and moved over time. Make the shape, colours, look and movement
            match the words of the request (a pink heart flying forward = a pink heart shape moving forward). Make them
            rich, not flat: give the dots a fitting look, a colour ramp, some variation and physics, and layer 2-3
            effects (a bright core + sparks flying off + a fading ring) instead of one plain shape.
              ai.effect(when, [settings])
                when: 'attack' (weapons: when it is swung; bows: where the arrow lands; up to 5 layers)
                      'idle'   (while held or worn; up to 3)
                      'trail'  (bows only: behind the arrow in flight; copies are left along its path)
                settings (all optional):
                  shape: 'burst' (ball of dots), 'sphere' (hollow ball), 'circle', 'ring', 'heart', 'star', 'crescent',
                         'slash' (wide arc of a sword swing), 'cross', 'spiral', 'bolt' (lightning zigzag),
                         'magic_circle' (ring + inner ring + pentagram), 'square', 'triangle', 'diamond', 'line',
                         'points' (your own: points: [[u, v], ...] in -1..1 units; lines: true joins them in order)
                  fill: true            (dots fill the shape instead of drawing its outline)
                  color: '#rrggbb'      color2: '#rrggbb' (colour it turns into over its life)
                  colors: ['#ffffff', '#ffd040', '#ff4010', '#401010']  (2-4 stops over its life, e.g. fire: white -> yellow
                         -> red -> dark; ice: white -> light blue -> blue; overrides color/color2)
                  form: what it is drawn as: 'dots' (the shape's dots, the default) or one smooth glowing piece:
                         'swipe'     the band a blade leaves along its swing (attack; plane 'side' for a sideways cut,
                                     'front' for an overhead one; arc: degrees round, 120-200; width: 0.2-0.6)
                         'shockwave' a ring spreading over the ground (attack with start 'ground' or 'target'; width)
                         'pillar'    a column of light shooting up (start 'target' or 'ground'; width: 0.1-0.4)
                         'beam'      a ray shooting ahead from the start (width)
                         'disc'      a glowing magic circle (plane 'flat' on the ground; moves ['spin'] to turn it)
                         'ribbon'    a band left behind along the path (bow 'trail', or 'idle' with moves ['orbit'];
                                     width; ticks = how long the band is)
                         'bolt'      a jagged, branching LIGHTNING BOLT striking down from the sky onto the start, with
                                     a flash where it hits; it flickers (attack with start 'target' or 'ground'; colors
                                     e.g. ['#ffffff', '#a0d0ff']; size 0.4-1.5 = how thick and wide it branches; width;
                                     ticks 6-12). For lightning, thunder and smiting use THIS, not a pillar or dots.
                  lightning: true  (attack: also a real vanilla lightning strike there, harmless: no fire, no damage;
                         only when the request wants real thunderbolts)
                  damage: 0.1-0.5 (attack only: what the effect touches is HURT by that part of the weapon's damage;
                         only when the attack line says the effect itself hurts: "충격파로 주변 적에게 피해", "번개가 떨어져
                         피해", "검기가 적을 벤다", "폭발 피해". The one hit directly is not hurt again; a swing or landing
                         hurts each target by at most 0.5 in all. Where it reaches: swipe = the fan in front, shockwave =
                         the ring as it spreads, beam = the line, pillar/bolt = the column, disc = the circle, dots = the
                         ball (flying with 'forward'). Leave it out for effects that only look.)
                         Smooth pieces take color/colors, size, plane, start/anchor, moves (expand, spin, fade, rise...)
                         and ticks; count, dot, look and particle are for dots. Combine a smooth piece with dots
                         (a swipe + sparks flying off it, a shockwave + dust, a pillar + rising embers).
                  look: what each dot looks like: 'dot' (round), 'glow' (big soft light), 'star' (twinkling 4-point
                         star), 'streak' (stretched along its motion: sparks, rain, speed lines), 'shard' (spinning
                         crystal or glass chip), 'rune' (spinning magic glyph), 'ember' (bright core, faint halo), 'ring'
                  particle: a vanilla particle instead of our dots: 'flame', 'soul_fire_flame', 'small_flame',
                         'end_rod', 'cherry_leaves', 'snowflake', 'electric_spark', 'glow', 'enchant', 'smoke',
                         'large_smoke', 'cloud', 'bubble', 'heart', 'note', 'crit', 'enchanted_hit', 'totem_of_undying',
                         'witch', 'portal', 'sculk_soul', 'wax_on', 'scrape', 'lava', 'firework', 'soul', 'ash',
                         'white_ash', 'spore_blossom_air', 'poof', 'dust' (takes the colours). Real flames, leaves and
                         smoke look best this way; the particle keeps moving on its own after it leaves the shape.
                  vary: 0-1 (dots differ in size, life and fall; 0.3-0.6 looks natural)
                  gravity: blocks/s^2 the dots fall (sparks 3-6, embers 1; negative floats up like smoke, -0.5)
                  drag: 0-1 (moving dots slow down and stop: bursts that puff out and hang, 0.3-0.7)
                  turbulence: 0-1 (dots drift and swirl: smoke, magic dust, embers in the air)
                  delay: ticks after the swing this layer starts (0-40): stage the layers, e.g. a flash at 0, the
                         ring at 2, sparks at 3, so a hit builds up instead of everything popping at once
                  shake: 0-1 (attack only: a big hit shakes the view of whoever is right there; hammers, thunder,
                         explosions 0.4-0.8; leave it out for ordinary swings)
                  sound: a vanilla sound when it starts, e.g. 'entity.blaze.shoot', 'entity.lightning_bolt.thunder',
                         'block.amethyst_block.chime', 'entity.player.attack.sweep', 'item.trident.riptide_1',
                         'block.fire.extinguish', 'entity.evoker.cast_spell'   volume: 0-2   pitch: 0.5-2
                  size: blocks (radius, 0.1-4)   count: dots (4-96; outlines 24-48, filled 40-80)   dot: dot size (0.02-0.3)
                  plane: 'front' (stands UP like a wall facing the way it goes: a VERTICAL wave), 'flat' (lies flat: a
                         HORIZONTAL / 가로 wave, ground circles, halos),
                         'side' (stands along the way it goes: a sword arc seen from the side)
                  start (attack): 'weapon' (just in front of the wielder), 'target' (on what is hit), 'ground' (on the
                         ground under it), 'above' (over it), 'self' (around the wielder)
                  anchor (idle): 'item' (the held item), 'head' (over the head), 'body', 'back', 'feet', 'around'
                  moves: list of 'forward', 'back', 'rise', 'fall', 'expand', 'shrink', 'spin', 'orbit', 'pulse',
                         'scatter' (dots fly apart), 'gather' (dots fly in), 'wave', 'reveal' (drawn along its length
                         like a slash), 'fade', 'flicker'
                  speed: blocks per second for forward/back/rise/fall/scatter/gather; turns per second for spin/orbit
                  ticks: how long it lives (20 = 1 second); for idle without 'every', one loop
                  every: idle and trail: ticks between new copies (0 = one copy that stays and loops, e.g. a ring
                         orbiting the item)
            READY-MADE EFFECTS: whole layered effects made by hand; START FROM ONE when the request fits one, then
            adjust it and/or add your own layer:
              ai.preset(when, name)   or   ai.preset(when, name, [colors: ['#rrggbb', ...], scale: 1.3, sound: '...', shake: 0.5])
                colors: 1-4 colours replacing the preset's (keep their light-to-dark order); scale: size x0.3-3;
                sound: another vanilla sound, or '' for none; shake: 0-1; lightning: true (attack: a real harmless strike too)
            %PRESETS%            e.g. ai.preset('attack', 'frost', [colors: ['#ffffff', '#ffb0f0', '#c040c0'], scale: 1.2])  // pink ice
                 ai.preset('idle', 'flame_aura')
                 ai.effect('attack', [shape: 'star', look: 'star', color: '#ffe080', size: 0.6, count: 12, start: 'target',
                                      moves: ['expand', 'fade'], delay: 5, ticks: 14])   // an extra layer of your own
            Examples:
              // a fire slash: the blade's glowing band, sparks thrown off it that fall, and real flames
              ai.effect('attack', [form: 'swipe', colors: ['#ffffff', '#ffc040', '#ff3000'], size: 1.4, arc: 170,
                                   width: 0.45, plane: 'side', start: 'weapon', ticks: 9, sound: 'entity.blaze.shoot',
                                   volume: 0.6])
              ai.effect('attack', [shape: 'slash', look: 'streak', colors: ['#ffffa0', '#ff6000'], size: 1.3, count: 40,
                                   plane: 'side', start: 'weapon', moves: ['scatter', 'fade'], speed: 4, gravity: 5,
                                   drag: 0.3, vary: 0.6, ticks: 16])
              ai.effect('attack', [shape: 'burst', particle: 'flame', size: 0.6, count: 24, start: 'target',
                                   moves: ['scatter'], speed: 2, ticks: 10])
              ai.effect('attack', [shape: 'heart', fill: true, color: '#ff66cc', size: 0.45, count: 60, plane: 'front',
                                   start: 'weapon', moves: ['forward', 'pulse', 'fade'], speed: 7, ticks: 16])
              ai.effect('attack', [shape: 'slash', color: '#bfe8ff', color2: '#3060ff', size: 1.3, count: 64,
                                   plane: 'side', start: 'weapon', moves: ['reveal', 'fade'], ticks: 8])
              ai.effect('attack', [form: 'disc', colors: ['#e0b0ff', '#7020ff'], size: 1.5, plane: 'flat',
                                   start: 'ground', moves: ['spin', 'fade'], speed: 0.4, ticks: 30])
              ai.effect('attack', [form: 'pillar', colors: ['#ffffff', '#ffe080'], size: 0.8, width: 0.3,
                                   start: 'target', ticks: 14, sound: 'block.beacon.activate'])
              ai.effect('attack', [form: 'shockwave', color: '#a0e0ff', size: 2.2, width: 0.25, start: 'ground',
                                   ticks: 14])
              ai.effect('idle', [shape: 'burst', look: 'ember', colors: ['#fff0a0', '#ff8020', '#602010'], size: 0.3,
                                 count: 8, anchor: 'item', moves: ['rise', 'fade'], speed: 0.6, turbulence: 0.5, vary: 0.5,
                                 ticks: 24, every: 6])
              ai.effect('idle', [shape: 'circle', particle: 'cherry_leaves', size: 0.5, count: 6, anchor: 'item',
                                 ticks: 40])
              ai.effect('idle', [shape: 'ring', color: '#9ff', size: 0.35, count: 24, plane: 'flat', anchor: 'head',
                                 moves: ['spin', 'pulse'], speed: 0.4, ticks: 40])
              ai.effect('trail', [form: 'ribbon', colors: ['#ffffff', '#40c0ff'], size: 0.4, width: 0.5, ticks: 12])
              ai.effect('trail', [shape: 'star', color: '#ffd84a', size: 0.15, count: 12, moves: ['shrink', 'fade'],
                                  ticks: 10, every: 1])

            MOVEMENT AND GLOW OF THE ITEM ITSELF (part of the while-held/worn line: only if that line is not "none"):
              ai.groupStart('name') ... ai.groupEnd()       // pieces made in between move together
              ai.animate('name', type, pivotX, pivotY, pivotZ, axis, speed, amount)
                type 'spin'  : turns around the axis through the pivot, speed = turns per second (amount unused)
                type 'swing' : rocks back and forth, amount = degrees (flapping wings, swaying plumes), speed = cycles/s
                type 'bob'   : floats up and down, amount = blocks (0.02-0.15), speed = cycles/s
                type 'pulse' : grows and shrinks, amount = fraction (0.05-0.2), speed = cycles/s
                axis: 'x', 'y' or 'z'. A group made with mirrorOn() gets a mirrored twin that moves as its mirror image.
              ai.glow('name')                               // the group glows in the dark (gems, flames, runes)
              ai.colorWave('name', ['#rrggbb', ...], speed, flow, bands)   // THE ITEM'S OWN COLOUR CHANGES, like LEDs:
                the pieces of the group ('all' = the whole item) run through 2-6 colours and shine; flow 'down' (colours
                pour from the top to the bottom), 'up', or 'all' (all together); speed = cycles per second (0.3-2);
                bands = colour stripes along it at once (1 = one gradient over its length). Use THIS for gradients,
                rainbow, LED, neon or colour-changing items - not dots: an idle dot effect floats beside the item
                and does not change its colour. e.g. ai.colorWave('blade', ['#ff3030', '#ffd030', '#30ff60', '#3080ff'], 0.8, 'down')
              ai.aura('#rrggbb', strength)                  // a soft, shining halo of light around the whole item while held
                                                            // or worn (strength 0.2-1): holy, cursed, magical, legendary items
            """;

    public static AiPrompt gearPrompt(String userPrompt, GearKind kind) {
        return gearPrompt(userPrompt, kind, "", "");
    }

    public static AiPrompt gearPrompt(String userPrompt, GearKind kind, String fxHit, String fxIdle) {
        return gearPrompt(userPrompt, kind, fxHit, fxIdle, false);
    }

    /** pixel: the order's "모양: 픽셀" (pixel art instead of block pieces). */
    public static AiPrompt gearPrompt(String userPrompt, GearKind kind, String fxHit, String fxIdle, boolean pixel) {
        AiPrompt base = gearPromptBase(userPrompt, kind, pixel);
        String hit = kind.worn() ? "none (worn equipment has no hit effect)" : blankToNone(fxHit);
        String hitLine = kind == GearKind.BOW ? "\nArrow effect (trail in flight and/or where it lands): " : "\nEffect on attack (swing): ";
        String effects = hitLine + hit + "\nEffect while " + (kind.worn() ? "worn" : "held")
            + " (particles, movement, glow): " + blankToNone(fxIdle);
        String system = base.system() + "\n" + EFFECT_API + (kind == GearKind.BOW ? "\n" + SHOT_API + "\n" + EXTRA_API
            : !kind.worn() ? "\n" + EXTRA_API + "\n" + MELEE_SHOT : "");
        return new AiPrompt(system, base.user().replace("\nWrite the Groovy script", effects + "\nWrite the Groovy script"));
    }

    private static String blankToNone(String text) {
        return text == null || text.isBlank() ? "none" : text.trim();
    }

    private static AiPrompt gearPromptBase(String userPrompt, GearKind kind, boolean pixel) {
        String shapes = OBJECT_SYSTEM.substring(OBJECT_SYSTEM.indexOf("SMOOTH SHAPES"), OBJECT_SYSTEM.indexOf("STYLE (this"));
        String style = GEAR_STYLE;
        if (pixel) {
            shapes = PIXEL_API + "\n" + shapes;
            style = PIXEL_STYLE;
        }
        if (kind.worn()) {
            boolean legs = kind.slot == net.minecraft.entity.EquipmentSlot.LEGS || kind.slot == net.minecraft.entity.EquipmentSlot.FEET;
            String system = pixel
                ? WEAR_SYSTEM.formatted(shapes, style, (legs ? EXAMPLE_PIXEL_PANTS : EXAMPLE_PIXEL_HELMET).strip(),
                    EXAMPLE_PIXEL_WINGS.strip(), (legs ? EXAMPLE_PIXEL_HELMET : EXAMPLE_PIXEL_PANTS).strip())
                : legs
                ? WEAR_SYSTEM.formatted(shapes, style, EXAMPLE_PANTS.strip(), EXAMPLE_SHOES.strip(), EXAMPLE_HAT.strip())
                : WEAR_SYSTEM.formatted(shapes, style, EXAMPLE_HELMET.strip(), EXAMPLE_HAT.strip(), EXAMPLE_WINGS.strip());
            String user = "Equipment kind: " + kind.id() + " (" + kind.korean + "), worn on the "
                + switch (kind.slot) {
                    case HEAD -> "HEAD";
                    case LEGS -> "LEGS (model one leg)";
                    case FEET -> "FEET (model one foot)";
                    default -> "CHEST (torso)";
                }
                + ". What this kind covers (the request decides what it actually is): " + kind.guide
                + "\nRequest: " + userPrompt + "\nWrite the Groovy script for it now.";
            return new AiPrompt(system, user);
        }
        String system = pixel
            ? GEAR_SYSTEM.formatted(shapes, style, EXAMPLE_PIXEL_SWORD.strip(), EXAMPLE_PIXEL_STAFF.strip())
            : GEAR_SYSTEM.formatted(shapes, style, EXAMPLE_SWORD.strip(), EXAMPLE_AXE.strip());
        String user = "Equipment kind: " + kind.id() + " (" + kind.korean + "). Typical shape for this kind "
            + "(follow the request where it differs): " + kind.guide
            + "\nRequest: " + userPrompt + "\nWrite the Groovy script for it now.";
        return new AiPrompt(system, user);
    }

    /** A real device with depth on every side (front frame, recessed and raised parts, a closed back). */
    public static final String EXAMPLE_RADIO = """
        // old table radio at real size. Parts: wooden cabinet 0.7 x 0.42 x 0.28; raised front panel; speaker cloth with
        // grille bars; dial scale with a needle; two knobs; carrying handle; antenna; feet; back cover with vents
        def z = 2.0
        def w = 0.7
        def h = 0.42
        def d = 0.28
        ai.addRoundBox('dark_oak_planks', -w / 2, 0.04, z, w, h, d, 0.05)                         // cabinet (real depth)
        ai.addRoundBox('spruce_planks', -w / 2 + 0.03, 0.07, z - 0.01, w - 0.06, h - 0.06, 0.03, 0.02)  // raised front panel
        ai.addRoundBox('black_wool', -0.31, 0.11, z - 0.015, 0.34, 0.3, 0.03, 0.01)               // speaker cloth
        for (int i = 0; i < 5; i++) {
            ai.addPart('dark_oak_planks', -0.31, 0.13 + i * 0.06, z - 0.03, 0.34, 0.015, 0.02)    // grille bars in front of it
        }
        ai.addRoundBox('yellow_terracotta', 0.07, 0.3, z - 0.02, 0.22, 0.08, 0.02, 0.01)          // dial scale
        ai.addPart('red_concrete', 0.16, 0.305, z - 0.03, 0.008, 0.07, 0.01)                      // needle
        ai.addCapsule('black_concrete', 0.11, 0.17, z - 0.06, 0.11, 0.17, z, 0.035)               // tuning knob
        ai.addCapsule('black_concrete', 0.25, 0.17, z - 0.06, 0.25, 0.17, z, 0.035)               // volume knob
        ai.addTube('black_concrete', [[-0.2, h + 0.03, z + 0.14], [-0.17, h + 0.13, z + 0.14], [0.17, h + 0.13, z + 0.14], [0.2, h + 0.03, z + 0.14]], 0.015)
        ai.addCapsule('iron_block', 0.27, h + 0.04, z + 0.22, 0.4, h + 0.42, z + 0.25, 0.01)       // telescopic antenna
        ai.addRoundBox('brown_terracotta', -w / 2 + 0.05, 0.09, z + d - 0.005, w - 0.1, h - 0.1, 0.015, 0)  // back cover
        for (int i = 0; i < 4; i++) {
            ai.addPart('black_concrete', -0.2, 0.15 + i * 0.06, z + d + 0.008, 0.4, 0.015, 0.008)  // vents
        }
        ai.mirrorOn()
        ai.addRoundBox('black_concrete', 0.25, 0, z + 0.03, 0.07, 0.05, 0.22, 0.01)              // feet
        ai.mirrorOff()
        """;

    /** Instructions for objects: the same for every request (cached by Claude). */
    static final String OBJECT_SYSTEM = """
            You are an expert modeller of cute, detailed Minecraft props and characters. Write ONLY executable Groovy using the `ai` API.

            Build ONE object out of display-entity pieces (not whole blocks). Smooth shapes are drawn on a fine
            1/32-block grid (1/16 or coarser for big or busy objects), and touching pieces of the same block are merged
            automatically.

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
            MATERIAL (the first argument of every piece): a block name, or a smooth colour:
              'oak_planks', 'white_concrete'      // the block's texture (wood grain, stone, bricks, ore)
              '#c0392b'                           // one smooth colour, no texture: a clean, modelled look
              '#88ccff/50'                        // a see-through colour; opacity 25, 50 or 75 (percent)
            - Colours look like a polished model instead of blocks: use them for smooth, stylised or toy-like things and
              for exact shades (skin, fur, painted metal, plastic, cloth). Use blocks where the material's texture is the
              point (wood, stone, bricks, ore). Mixing both in one thing is fine.
            - See-through things (ghosts, spirits, slime, jelly, ice, crystal, glass, water, magic or energy blades): a pale
              colour with /50 or /25, or glass, *_stained_glass, ice, slime_block or honey_block. Keep solid parts solid.

            STYLE (this decides the quality):
            - PLAN FIRST: before the code, write short // comments listing the thing's real parts with their real sizes
              (1 block = 1 metre), from the main body to the small details; then build them in that order.
            - Silhouette first: 2-4 big shapes with the right proportions, then medium parts, then small details.
            - Real things (devices, furniture, vehicles, tools, food, appliances): build their real 3D structure, never a
              flat picture of them. Every side has thickness; fronts have frames, bezels or rims, with recessed or raised
              parts in them (a screen sits inside its bezel, buttons and knobs stand out, drawers have handles); backs and
              undersides are closed and have their own simpler detail (back housing, vents, cables, legs, a stand and base).
            - Characters and animals: cute, stylised proportions. A big head (40-60%% of the body), big readable eyes
              (dark pupil + small white shine), a simple nose/mouth/beak, short sturdy limbs, a recognisable feature
              (horn, hat, mane, ears, tail).
            - Faces (eyes, mouth) only on characters and animals, or when the request asks for one. Food, plants,
              furniture, tools and decorations stay realistic: get their shape and colours right instead.
            - Round things (fruit, pots, heads, bodies) are spheres, ellipsoids, cylinders and cones, not boxes.
            - Keep parts at least 0.03 thick (one grid pixel); thinner ones are drawn one pixel thick.
            - 3-5 main colours. Use a darker shade for undersides and creases and a lighter one for highlights
              (e.g. yellow_concrete body + yellow_terracotta underside; white_concrete + light_gray_concrete).
            - Overlap shapes slightly so nothing floats or leaves gaps; set details just outside the surface they sit on.
            - Colours: *_concrete (bright, flat), *_terracotta (muted, skin and earth tones), *_wool (soft), white and black
              concrete for eyes. Materials: oak/spruce/dark_oak_planks, stripped_*_log, stone, mossy_cobblestone, iron_block,
              gold_block, glass, glowstone, quartz_block.
            - Put the main sizes and positions in variables and build from them.
            - Prefer a few smooth shapes over many tiny boxes. At most 400 pieces after merging.

            CODE RULES:
            - Block names and colours are quoted strings: 'white_concrete', '#c0392b'.
            - Loops: for (int i = 0; i < n; i++) or for (i in 0..<n). Never closures: no .each { }, .times { }, { -> }.
            - One flat script: never define methods or functions (no def name(...) { }, no classes); repeat code in loops.
            - No methods, classes or imports. Output Groovy only; no markdown, no explanation (short // comments are fine).

            EXAMPLES (style reference; make your own design, do not copy):
            %s
            %s
            %s
            %s
            """.formatted(EXAMPLE_DUCK.strip(), EXAMPLE_LAMP.strip(), EXAMPLE_CHAIR.strip(), EXAMPLE_RADIO.strip());


    public static AiPrompt objectPrompt(String userPrompt) {
        return new AiPrompt(OBJECT_SYSTEM, "Object request: " + userPrompt + "\nWrite the Groovy script for it now.");
    }

    /** Instructions for buildings (fixed part) + the request and the terrain around the player. */
    public static AiPrompt buildingPrompt(String userPrompt, String worldInfo, boolean webSearchEnabled) {
        String user = "Build request: " + userPrompt + "\n\nWorld Information:\n" + worldInfo
            + "\n\nWrite the Groovy script for it now.";
        return new AiPrompt(buildingSystem(webSearchEnabled), user);
    }

    /** The fixed building instructions with another request (for other mods; cached by Claude like ours). */
    public static AiPrompt buildingPromptWith(String userText) {
        return new AiPrompt(buildingSystem(false), userText);
    }

    /** How to make a building look built, not boxed (the part of the building instructions about quality). */
    static final String BUILDING_CRAFT = """
            CRAFT (what makes a building look real; do all of it unless the request says otherwise):
            - Palette of 3-4 materials: main wall, frame/trim (logs or a darker stone), roof, one accent. Mix the main wall
              with ai.wallMix (2-3 related blocks, e.g. 60% stone_bricks, 25% cobblestone, 15% mossy_stone_bricks) instead of one flat block.
            - Foundation: a plinth 1 block wider than the walls, of a darker or rougher stone, at the ground line.
            - Frame: corner posts of logs or pillars, and a beam line along the top of each storey's walls, so the
              structure shows on the outside. Where the style allows, posts stick out 1 block from the wall.
            - Depth on every face: something comes out or goes in (porch or step at the door, bay or balcony, chimney,
              awning, a deeper window). Never a flat box.
            - Windows with ai.window (panes set in the wall, a sill and shutters outside); 2 high; at least one on each
              outer wall of each room; line them up storey over storey.
            - Doors with ai.door; a step, a small roof or awning, and a light beside the main door.
            - A real roof: ai.roofGable or ai.roofHip with 1 block of overhang (a flat roof only for modern buildings,
              then with a parapet and a top edge). Fill the gable ends; add a chimney on houses with a hearth or stove.
            - Inside: floors, a ceiling under the roof, room walls with doorways, light in every room, furniture that fits each
              room. In modern and contemporary buildings use the library props (ai.prop) for what blocks cannot show well.

            """;

    /** A small modern house using the helpers and props (in the prompt as a style reference, and built in the tests). */
    public static final String EXAMPLE_HOUSE = """
            // EXAMPLE (style reference; make your own design): a modern cottage 11 x 9, x -5..5, z 3..11, the player at the
            // origin looking +Z; the front with the door faces the player (north, z = 3). Palette: mixed stone, spruce frame,
            // dark oak roof, white accent.
            def mix = ['stone_bricks': 60, 'cobblestone': 25, 'andesite': 15]
            ai.clearArea(-6, 0, 2, 13, 10, 11)
            ai.placeFloor(-6, -1, 2, 13, 11, 'polished_andesite')            // plinth, 1 wider than the walls
            ai.placeFloor(-5, 0, 3, 11, 9, 'spruce_planks')                  // floor
            ai.wallMix(-5, 1, 3, 4, 11, 'x', mix)                            // front
            ai.wallMix(-5, 1, 11, 4, 11, 'x', mix)                           // back
            ai.wallMix(-5, 1, 3, 4, 9, 'z', mix)                             // west
            ai.wallMix(5, 1, 3, 4, 9, 'z', mix)                              // east
            def corners = [[-5, 3], [5, 3], [-5, 11], [5, 11]]
            for (int i = 0; i < 4; i++) {
                ai.placePillar(corners[i][0], 0, corners[i][1], 5, 'stripped_spruce_log')   // corner posts
            }
            ai.placeWall(-5, 5, 3, 1, 11, 'x', 'spruce_log[axis=x]')         // beams on top of the walls
            ai.placeWall(-5, 5, 11, 1, 11, 'x', 'spruce_log[axis=x]')
            ai.placeWall(-5, 5, 4, 1, 7, 'z', 'spruce_log[axis=z]')
            ai.placeWall(5, 5, 4, 1, 7, 'z', 'spruce_log[axis=z]')
            ai.placeFloor(-4, 5, 4, 9, 7, 'spruce_planks')                   // ceiling
            ai.placeWall(0, 1, 4, 4, 7, 'z', 'white_concrete')               // room wall: living room west, kitchen east
            ai.placeBlock(0, 1, 9, 'air'); ai.placeBlock(0, 2, 9, 'air')     // doorway between them
            ai.door(-2, 1, 3, 'north', 'spruce')                             // front door
            ai.placeBlock(-2, 0, 2, 'spruce_stairs[facing=south]')           // step
            ai.placePillar(-3, 0, 2, 3, 'spruce_fence'); ai.placeBlock(-3, 3, 2, 'lantern')
            ai.window(-4, 2, 3, 1, 2, 'north', 'spruce')
            ai.window(2, 2, 3, 2, 2, 'north', 'spruce')
            ai.window(-5, 2, 6, 2, 2, 'west', 'spruce')
            ai.window(5, 2, 6, 2, 2, 'east', 'spruce')
            ai.window(-3, 2, 11, 2, 2, 'south', 'spruce')
            ai.roofGable(-5, 6, 3, 11, 9, 'dark_oak', 'x', 'spruce_planks')  // roof, ridge along x, gable ends filled
            ai.placePillar(3, 6, 9, 6, 'bricks')                             // chimney
            // living room (west): sofa against the west wall facing the TV on the room wall, a lamp, curtains at the back window
            ai.prop('sofa', -4, 1, 5, 'east')
            ai.prop('wall_tv', -1, 1, 5, 'west')
            ai.prop('floor_lamp', -4, 1, 10, 'north')
            ai.prop('curtain', -3, 1, 10, 'north')
            ai.prop('ceiling_light', -3, 4, 6, 'south')
            // kitchen (east): fridge, a counter with a phone on it (the counter's top is y = 2), a clock on the room wall
            ai.prop('fridge', 4, 1, 10, 'west')
            ai.placeFloor(1, 1, 10, 2, 1, 'smooth_stone')
            ai.prop('desk_phone', 2, 2, 10, 'north')
            ai.prop('wall_clock', 1, 1, 6, 'east')
            ai.prop('ceiling_light', 2, 4, 7, 'south')
            """;

    /** The building helpers, for the API list. */
    static final String BUILDING_HELPERS = """
            Building parts (they get stairs, doors and shutters the right way round):
              ai.roofGable(x, y, z, width, length, material, ridgeAxis)          // stairs from both long sides up to a ridge along 'x' or 'z'
              ai.roofGable(x, y, z, width, length, material, ridgeAxis, gableBlock) // ... and the two triangle ends filled with gableBlock
              ai.roofHip(x, y, z, width, length, material)                       // stairs from all four sides
                // (x, z, width, length) = the walls' outline; y = one above the walls' top; 1 block of overhang is added all round.
                // material = a block that has stairs and slabs: 'spruce', 'dark_oak', 'stone_brick', 'deepslate_tile', 'brick', 'mud_brick'
              ai.door(x, y, z, facing) | ai.door(x, y, z, facing, wood)            // a door in the wall block (x, y, z); facing = the way the outside looks
              ai.window(x, y, z, width, height, facing) | ai.window(..., frameWood) | ai.window(..., frameWood, glassBlock)
                // panes in the wall from (x, y, z) along the wall toward +x or +z; sill and open shutters on the outside (facing)
              ai.wallMix(x, y, z, height, length, direction, ['stone_bricks': 60, 'cobblestone': 25, 'mossy_stone_bricks': 15])
                // like placeWall, of several blocks mixed by weight
            """;

    /** The furniture library, for the API list. */
    static String propsDoc() {
        return "Ready-made furniture (modern and contemporary buildings only; never in old-period buildings):\n"
            + "  ai.prop(id, x, y, z, facing)\n"
            + "    // (x, y, z) = the air block it stands in, just above the floor; facing = the way its front looks (into the room).\n"
            + "    // Its back lies against the far side of that block: put it in the block right in front of a wall, facing away from it.\n"
            + "    // A prop N blocks wide takes N blocks from x toward +x (facing north/south) or from z toward +z (facing east/west).\n"
            + "    // 'top' props stand on a table: y = the table's top (e.g. y of the table block + 1, or + 0.5 on a bottom slab).\n"
            + "    // 'ceiling' props: (x, y, z) = the air block just under the ceiling. Lamps light the room themselves.\n"
            + "    // Place no blocks where a prop is. At most " + com.example.ai.api.MinecraftAI.MAX_PROPS + " per building. Ids:\n"
            + com.example.ai.build.PropLibrary.catalog()
            + "    // Beds, chests, doors, tables, chairs, shelves, stoves and the like stay real blocks.\n";
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

        prompt.append(BUILDING_CRAFT);
        prompt.append(EXAMPLE_HOUSE).append("\n");

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
        prompt.append(BUILDING_HELPERS);
        prompt.append(propsDoc());
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
        prompt.append("- One flat script: never define methods or functions (no def name(...) { }, no classes); use loops and arrays.\n");
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
