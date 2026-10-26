package com.example.api;

import com.example.ai.AiCodeSanitizer;
import com.example.ai.AiController;
import com.example.ai.AiHttpService;
import com.example.ai.AiPrompt;
import com.example.ai.api.MinecraftAI;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildTaskQueue;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What other mods (such as the scenario map builder) can use from the AI Build Assistant:
 * ask the configured AI anything, have it design a building for a given site or an object of a
 * given size (checked and retried like the mod's own requests), and place the result as the
 * server. Nothing here asks a player for approval; the calling mod decides.
 *
 * <p>All results complete on the server thread.
 */
public final class AiBuildApi {

    /** Version of this API, raised when something here changes in a way callers must know. */
    public static final int VERSION = 1;

    private AiBuildApi() {
    }

    // ------------------------------------------------------------------ plain AI questions

    /** The AI's text answer, and the tokens it cost. */
    public record Answer(boolean ok, String text, String error, int inputTokens, int outputTokens) {
    }

    /**
     * Ask the configured AI provider (optionally another model of it) and get its text answer.
     * Use for planning steps that are not build scripts (e.g. JSON).
     */
    public static CompletableFuture<Answer> ask(MinecraftServer server, AiPrompt prompt, String model) {
        CompletableFuture<Answer> out = new CompletableFuture<>();
        AiProvider provider = AiBuilderConfig.get().provider;
        try {
            var request = AiHttpService.buildRequest(prompt, false, model);
            AiHttpService.client().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> server.execute(() -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        out.complete(new Answer(false, "", AiHttpService.extractHttpErrorMessage(response.body(),
                            response.statusCode(), provider), 0, 0));
                        return;
                    }
                    int[] usage = AiHttpService.usage(response.body());
                    int in = usage.length > 0 ? usage[0] : 0, outTokens = usage.length > 1 ? usage[1] : 0;
                    try {
                        String text = AiHttpService.extractResponseBody(response.body(), provider);
                        out.complete(new Answer(true, text, null, in, outTokens));
                    } catch (RuntimeException exception) {
                        // Still report what it cost (an answer that ran out of room is paid for).
                        out.complete(new Answer(false, "", exception.getMessage(), in, outTokens));
                    }
                }))
                .exceptionally(ex -> {
                    server.execute(() -> out.complete(new Answer(false, "", String.valueOf(ex.getMessage()), 0, 0)));
                    return null;
                });
        } catch (RuntimeException exception) {
            out.complete(new Answer(false, "", exception.getMessage(), 0, 0));
        }
        return out;
    }

    // ------------------------------------------------------------------ designs

    /** A checked design: the plan to place, or why the AI could not make one. */
    public record Design(boolean ok, String error, BuildPlan plan, String script, int calls, int inputTokens, int outputTokens) {
    }

    /**
     * A building site. The building is designed with its front (main door) on the front edge;
     * {@code width} runs along the front edge and {@code depth} away from it.
     */
    public record Site(int width, int depth, int maxHeight) {
    }

    /**
     * What to build: what it is, style rules shared by a whole town, the rooms it needs, the
     * named spots the script must mark with ai.mark (e.g. "clue:diary in the study"), and any
     * extra instructions. {@code model} may name another model of the provider (null = configured).
     */
    public record BuildingRequest(String description, String style, List<String> rooms, List<String> marks, String extra,
                                  String model) {
    }

    /** Design frame of a building: the designer stands at (0, 0, 0) looking north (-Z). */
    private static final float DESIGN_YAW = 180f;

    /** Ask the AI for a building that fits the site, retrying with the problems until it does. */
    public static CompletableFuture<Design> designBuilding(ServerWorld world, BlockPos near, Site site, BuildingRequest req,
                                                           Consumer<String> log) {
        StringBuilder user = new StringBuilder();
        user.append("Build request: ").append(req.description()).append("\n");
        if (req.style() != null && !req.style().isBlank()) {
            user.append("\nStyle rules (shared by the whole town; follow them):\n").append(req.style().trim()).append("\n");
        }
        if (req.rooms() != null && !req.rooms().isEmpty()) {
            user.append("\nRooms it must have (each reachable through doors, with furniture that fits its use):\n");
            for (String room : req.rooms()) {
                user.append("- ").append(room).append("\n");
            }
        }
        user.append("\nSITE (strict): flat ground; the ground surface is y = -1 and you build from y = 0 up.\n")
            .append("- Use only x from 0 to ").append(site.width() - 1).append(", z from -").append(site.depth())
            .append(" to -1 and y from 0 to ").append(site.maxHeight()).append(" (anything outside is cut off).\n")
            .append("- The FRONT edge is z = -1: the main door and facade face +Z (toward the street at z = 0).\n")
            .append("- Leave a 1-block margin inside the site for paths; do not inspect the terrain.\n");
        if (req.marks() != null && !req.marks().isEmpty()) {
            user.append("\nMARK these spots with ai.mark(name, x, y, z): the exact block where each thing belongs (on the floor,\n")
                .append("a table or a shelf, inside the right room, reachable). Use exactly these names:\n");
            for (String mark : req.marks()) {
                user.append("- ").append(mark).append("\n");
            }
        }
        if (req.extra() != null && !req.extra().isBlank()) {
            user.append("\n").append(req.extra().trim()).append("\n");
        }
        user.append("\nWrite the Groovy script for it now.");
        AiPrompt base = AiController.buildingPromptWith(user.toString());
        List<String> wanted = new ArrayList<>();
        if (req.marks() != null) {
            for (String m : req.marks()) {
                wanted.add(markName(m));
            }
        }
        return design(world.getServer(), base, req.model(), log, code -> {
            AiController.Evaluation eval = AiController.evaluate(world, near, DESIGN_YAW, null, code, false);
            if (!eval.ok()) {
                return eval.failure();
            }
            String problem = siteProblem(eval.plan(), site);
            if (problem == null) {
                problem = missingMarks(eval.plan(), wanted);
            }
            return problem == null ? eval.plan() : problem;
        });
    }

    /** "clue:diary in the study" -> "clue:diary". */
    static String markName(String mark) {
        String m = mark.trim();
        int space = m.indexOf(' ');
        return space < 0 ? m : m.substring(0, space);
    }

    static String siteProblem(BuildPlan plan, Site site) {
        if (plan.ops.isEmpty()) {
            return "The script placed no blocks. Build the building with ai.place* calls.";
        }
        List<String> problems = new ArrayList<>();
        for (BuildPlan.Op op : plan.ops) {
            int minX = Math.min(op.x1, op.x2), maxX = Math.max(op.x1, op.x2);
            int minY = Math.min(op.y1, op.y2), maxY = Math.max(op.y1, op.y2);
            int minZ = Math.min(op.z1, op.z2), maxZ = Math.max(op.z1, op.z2);
            if (minX < -1 || maxX > site.width() || minZ < -site.depth() - 1 || maxZ > 0 || minY < -2 || maxY > site.maxHeight() + 2) {
                problems.add(String.format(Locale.ROOT, "a %s reaches x %d..%d, y %d..%d, z %d..%d; keep x 0..%d, y 0..%d, z -%d..-1",
                    op.kind == null ? "command" : op.kind, minX, maxX, minY, maxY, minZ, maxZ,
                    site.width() - 1, site.maxHeight(), site.depth()));
                if (problems.size() >= 5) {
                    break;
                }
            }
        }
        return problems.isEmpty() ? null : "The building leaves its site:\n- " + String.join("\n- ", problems);
    }

    /** Tests: the site check. */
    public static String siteProblemForTests(BuildPlan plan, Site site) {
        return siteProblem(plan, site);
    }

    /** Tests: the mark check. */
    public static String missingMarksForTests(BuildPlan plan, List<String> wanted) {
        return missingMarks(plan, wanted);
    }

    static String missingMarks(BuildPlan plan, List<String> wanted) {
        List<String> missing = new ArrayList<>();
        for (String w : wanted) {
            boolean found = false;
            for (BuildPlan.Mark m : plan.marks) {
                found |= m.name.equalsIgnoreCase(w);
            }
            if (!found) {
                missing.add(w);
            }
        }
        return missing.isEmpty() ? null : "These spots were not marked; call ai.mark for each: " + String.join(", ", missing);
    }

    /** Ask the AI for an object (display pieces) at most {@code maxSize} blocks in any direction. */
    public static CompletableFuture<Design> designObject(ServerWorld world, BlockPos near, String description, double maxSize,
                                                         String model, Consumer<String> log) {
        AiPrompt base = AiController.objectPrompt(description + String.format(Locale.ROOT,
            " (at most %.1f blocks in any direction)", maxSize));
        return design(world.getServer(), base, model, log, code -> {
            AiController.Evaluation eval = AiController.evaluate(world, near, DESIGN_YAW, null, code, true);
            if (!eval.ok()) {
                return eval.failure();
            }
            if (!eval.plan().ops.isEmpty() || eval.plan().parts.isEmpty()) {
                return "An object is made only of pieces (addPart / smooth shapes), no blocks.";
            }
            double[] b = eval.plan().partBounds();
            double size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
            if (size > maxSize + 0.1) {
                return String.format(Locale.ROOT, "It is %.2f blocks big; keep it at most %.1f in any direction.", size, maxSize);
            }
            return eval.plan();
        });
    }

    /**
     * Ask, check, and on a problem ask again with the problem and the script, up to the
     * configured number of retries. {@code check} runs on the server thread and returns the
     * accepted plan or the problem text.
     */
    private static CompletableFuture<Design> design(MinecraftServer server, AiPrompt base, String model, Consumer<String> log,
                                                    Function<String, Object> check) {
        CompletableFuture<Design> out = new CompletableFuture<>();
        attempt(server, base, base, model, log, check, 0, new int[3], out);
        return out;
    }

    private static void attempt(MinecraftServer server, AiPrompt base, AiPrompt prompt, String model, Consumer<String> log,
                                Function<String, Object> check, int tries, int[] stats, CompletableFuture<Design> out) {
        ask(server, prompt, model).thenAccept(answer -> {
            stats[0]++;
            stats[1] += answer.inputTokens();
            stats[2] += answer.outputTokens();
            if (!answer.ok()) {
                String error = String.valueOf(answer.error());
                if (error.contains("max_tokens") && tries < AiBuilderConfig.get().autoRetries) {
                    // It ran out of room before the script: ask again for a shorter way to the script.
                    if (log != null) {
                        log.accept("retry " + (tries + 1) + ": the answer ran out of room");
                    }
                    AiPrompt shorter = new AiPrompt(base.system(), base.user() + "\n\nNOTE: the last answer ran out of output room before"
                        + " the script. Plan briefly, then write a compact script (loops, boxes and walls rather than many single blocks).");
                    attempt(server, base, shorter, model, log, check, tries + 1, stats, out);
                    return;
                }
                out.complete(new Design(false, answer.error(), null, null, stats[0], stats[1], stats[2]));
                return;
            }
            String code = AiCodeSanitizer.sanitize(answer.text());
            Object result;
            try {
                result = code.isBlank() ? "The AI returned no script." : check.apply(code);
            } catch (RuntimeException exception) {
                result = String.valueOf(exception.getMessage());
            }
            if (result instanceof BuildPlan plan) {
                out.complete(new Design(true, null, plan, code, stats[0], stats[1], stats[2]));
                return;
            }
            String problem = String.valueOf(result);
            int max = AiBuilderConfig.get().autoRetries;
            if (tries < max) {
                if (log != null) {
                    log.accept("retry " + (tries + 1) + "/" + max + ": " + firstLine(problem));
                }
                attempt(server, base, AiController.fixPrompt(base, code, problem), model, log, check, tries + 1, stats, out);
            } else {
                out.complete(new Design(false, problem, null, code, stats[0], stats[1], stats[2]));
            }
        });
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    // ------------------------------------------------------------------ placing

    /** Where a placed building ended up: its origin, turn, footprint and marked spots (world positions). */
    public record Placed(BlockPos origin, int turns, BlockPos min, BlockPos max, Map<String, List<BlockPos>> marks, String tag) {
    }

    /** "north", "east", "south", "west": the way the building's front looks. */
    public static Placed placeBuilding(ServerWorld world, BuildPlan plan, BlockPos siteMin, Site site, String front, String tag,
                                       UUID lane, Runnable onDone) {
        // Designed with the front facing south (+Z); turn it so the front faces the street.
        int turns = BuildPlan.turnsBetween("south", BuildPlan.normalizeFacing(front));
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : new int[][] { { 0, -site.depth() }, { site.width() - 1, -site.depth() }, { 0, -1 }, { site.width() - 1, -1 } }) {
            int[] r = BuildPlan.rotate(c[0], c[1], turns);
            minX = Math.min(minX, r[0]);
            minZ = Math.min(minZ, r[1]);
            maxX = Math.max(maxX, r[0]);
            maxZ = Math.max(maxZ, r[1]);
        }
        BlockPos origin = siteMin.add(-minX, 0, -minZ);
        BuildPlan clipped = clip(plan, site);
        MinecraftAI.executePlanAsServer(world, clipped, origin, turns, tag, plan.facing, List.of(), lane);
        if (onDone != null) {
            BuildTaskQueue.whenEmpty(lane, onDone);
        }
        Map<String, List<BlockPos>> marks = new HashMap<>();
        for (BuildPlan.Mark m : plan.marks) {
            int[] r = BuildPlan.rotate(m.x, m.z, turns);
            marks.computeIfAbsent(m.name, k -> new ArrayList<>()).add(origin.add(r[0], m.y, r[1]));
        }
        return new Placed(origin, turns, origin.add(minX, 0, minZ), origin.add(maxX, site.maxHeight(), maxZ), marks, tag);
    }

    /** Drop commands that leave the site (the AI was told the site is strict). */
    private static BuildPlan clip(BuildPlan plan, Site site) {
        BuildPlan out = new BuildPlan();
        out.facing = plan.facing;
        for (BuildPlan.Op op : plan.ops) {
            int minX = Math.min(op.x1, op.x2), maxX = Math.max(op.x1, op.x2);
            int minZ = Math.min(op.z1, op.z2), maxZ = Math.max(op.z1, op.z2);
            if (maxX >= 0 && minX <= site.width() - 1 && maxZ >= -site.depth() && minZ <= -1) {
                out.ops.add(op);
            }
        }
        out.parts = plan.parts;
        return out;
    }

    /** World footprint size of a site whose front faces {@code front}: {sizeX, sizeZ}. */
    public static int[] footprint(Site site, String front) {
        String f = BuildPlan.normalizeFacing(front);
        return "east".equals(f) || "west".equals(f) ? new int[] { site.depth(), site.width() } : new int[] { site.width(), site.depth() };
    }

    /**
     * Place an object centred on top of {@code onTop} (the block it stands on), its front looking
     * {@code front}. Returns its tag (display pieces and hitboxes carry it).
     */
    public static String placeObject(ServerWorld world, BuildPlan plan, BlockPos onTop, String front, String tag, UUID lane) {
        // Objects face the person who placed them; that person looks the opposite way.
        String looking = switch (BuildPlan.normalizeFacing(front)) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
        int turns = BuildPlan.turnsBetween(plan.facing, looking);
        double[] b = MinecraftAI.objectBounds(plan, turns);
        double[] back = BuildPlan.rotate(0.5 - (b[0] + b[3]) / 2, 0.5 - (b[2] + b[5]) / 2, 4 - turns);
        BuildPlan moved = plan.translated(back[0], -b[1], back[1]);
        MinecraftAI.executePlanAsServer(world, moved, onTop.up(), turns, tag, "object", List.of(), lane);
        return tag;
    }

    /** Remove an object (or anything placed with display pieces) by its tag. */
    public static void removeTagged(ServerWorld world, String tag) {
        MinecraftServer server = world.getServer();
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(world).withLevel(4).withSilent(),
            "kill @e[tag=" + tag + "]");
    }

    // ------------------------------------------------------------------ undo for an area

    /** The blocks of a box, to put back later (block-entity contents are not kept). */
    public static final class Snapshot {
        final ServerWorld world;
        final BlockPos min;
        final int sx, sy, sz;
        final BlockState[] states;

        Snapshot(ServerWorld world, BlockPos min, int sx, int sy, int sz, BlockState[] states) {
            this.world = world;
            this.min = min;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.states = states;
        }

        /** Put the blocks back; returns how many changed. */
        public int restore() {
            int changed = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int i = 0;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlockState old = states[i++];
                        pos.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                        if (world.getBlockState(pos) != old) {
                            world.setBlockState(pos, old, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                            changed++;
                        }
                    }
                }
            }
            return changed;
        }
    }

    /** Copy the blocks between two corners (at most 2 million blocks). */
    public static Snapshot snapshot(ServerWorld world, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1, sz = Math.abs(a.getZ() - b.getZ()) + 1;
        if ((long) sx * sy * sz > 2_000_000L) {
            throw new IllegalArgumentException("area too large to snapshot: " + sx + "x" + sy + "x" + sz);
        }
        BlockState[] states = new BlockState[sx * sy * sz];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int i = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    states[i++] = world.getBlockState(pos.set(min.getX() + x, min.getY() + y, min.getZ() + z));
                }
            }
        }
        return new Snapshot(world, min, sx, sy, sz, states);
    }
}
