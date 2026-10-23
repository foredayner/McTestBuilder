package com.example.ai.script;

import com.example.config.ForbiddenBlockRegistry;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks an AI build script before it is run, without any AI call:
 * it must compile, call only real ai.* functions with a valid number of arguments, use block
 * names that exist in this Minecraft version (and are not forbidden), give walls a direction,
 * and keep literal coordinates/sizes near the player. Each problem names the line, so the AI
 * gets a precise list to fix.
 */
public final class ScriptLinter {

    private static final int MAX_LITERAL = 256;
    private static final int MAX_PROBLEMS = 15;
    private static final Set<String> PLACERS = Set.of(
        "placeBlock", "placeBox", "placePillar", "placeFloor", "placeRoof", "placeWall",
        "addPart", "addCylinder", "addSphere", "addEllipsoid", "addCone", "addCapsule", "addTube", "addRoundBox");
    private static final Set<String> WALL_DIRECTIONS = Set.of("x", "z", "north", "south", "east", "west");
    private static final Pattern CALL = Pattern.compile("(?<![\\w$])(ai\\s*\\.\\s*)?([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern STRING = Pattern.compile("^(['\"])(.*)\\1$", Pattern.DOTALL);
    private static final Pattern INTEGER = Pattern.compile("^-?\\d+$");
    private static final Map<String, Set<Integer>> ARITY = arities();

    private ScriptLinter() {
    }

    private static Map<String, Set<Integer>> arities() {
        Map<String, Set<Integer>> map = new HashMap<>();
        for (Method method : AiScriptFacade.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                map.computeIfAbsent(method.getName(), k -> new TreeSet<>()).add(method.getParameterCount());
            }
        }
        return map;
    }

    public static List<String> lint(String code) {
        List<String> problems = new ArrayList<>();
        String compile = ScriptExecutor.compileProblem(code);
        if (compile != null) {
            problems.add("Does not compile: " + compile.replace("\r", "").trim());
            return problems;
        }

        Matcher m = CALL.matcher(code);
        while (m.find() && problems.size() < MAX_PROBLEMS) {
            if (insideString(code, m.start())) {
                continue;
            }
            boolean viaAi = m.group(1) != null;
            String name = m.group(2);
            int line = lineOf(code, m.start());
            Set<Integer> allowed = ARITY.get(name);
            if (allowed == null) {
                if (viaAi) {
                    problems.add("line " + line + ": ai." + name + "(...) does not exist. Use only the listed ai functions.");
                }
                continue;
            }
            List<String> args = arguments(code, m.end() - 1);
            if (args == null) {
                problems.add("line " + line + ": " + name + "( has no closing parenthesis.");
                continue;
            }
            if (!allowed.contains(args.size())) {
                problems.add("line " + line + ": " + name + " got " + args.size() + " argument(s), needs " + join(allowed) + ".");
                continue;
            }
            checkArguments(name, args, line, problems);
        }
        return problems.size() > MAX_PROBLEMS ? problems.subList(0, MAX_PROBLEMS) : problems;
    }

    private static void checkArguments(String name, List<String> args, int line, List<String> problems) {
        if (!PLACERS.contains(name) && !"clearArea".equals(name)) {
            return;
        }
        boolean wall = "placeWall".equals(name);
        boolean hasDirection = false;
        int strings = 0;
        for (String raw : args) {
            Matcher s = STRING.matcher(raw.trim());
            if (s.matches()) {
                strings++;
                hasDirection |= wall && WALL_DIRECTIONS.contains(s.group(2).trim().toLowerCase(Locale.ROOT));
            }
        }
        boolean missingDirection = wall && strings >= 2 && !hasDirection;
        for (String raw : args) {
            String arg = raw.trim();
            Matcher s = STRING.matcher(arg);
            if (s.matches()) {
                String value = s.group(2).trim();
                if (wall && WALL_DIRECTIONS.contains(value.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String problem = blockProblem(value);
                // On a wall with no valid direction, the unknown word is the bad direction, not a block.
                if (problem != null && !missingDirection) {
                    problems.add("line " + line + ": " + problem);
                }
            } else if (INTEGER.matcher(arg).matches()) {
                long v = Long.parseLong(arg);
                if (Math.abs(v) > MAX_LITERAL) {
                    problems.add("line " + line + ": " + name + " uses " + v + "; keep coordinates and sizes within "
                        + MAX_LITERAL + " blocks of the player.");
                }
            }
        }
        // A wall with two literal strings but no direction among them places nothing.
        if (missingDirection) {
            problems.add("line " + line + ": placeWall needs a direction \"x\" or \"z\" (north/south = x, east/west = z).");
        }
    }

    /** Null if the block can be placed, otherwise why not. */
    static String blockProblem(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        String full = lower.contains(":") ? lower : "minecraft:" + lower;
        String id = full.contains("[") ? full.substring(0, full.indexOf('[')) : full;
        try {
            if (!Registries.BLOCK.containsId(Identifier.of(id))) {
                return "\"" + value + "\" is not a block in Minecraft 1.21.4. Use ai.searchBlocks to find the right name.";
            }
        } catch (RuntimeException exception) {
            return "\"" + value + "\" is not a valid block name.";
        }
        if (ForbiddenBlockRegistry.isForbidden(id)) {
            return "\"" + id + "\" is forbidden. Pick another block.";
        }
        if (full.contains("[")) {
            try {
                BlockArgumentParser.block(Registries.BLOCK, full, false);
            } catch (Exception exception) {
                return "\"" + value + "\" has invalid block states (" + exception.getMessage() + ").";
            }
        }
        return null;
    }

    /** Top-level arguments of the call whose '(' is at open, or null if it never closes. */
    static List<String> arguments(String code, int open) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        char quote = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (c) {
                case '\'', '"' -> quote = c;
                case '(', '[', '{' -> depth++;
                case ')', ']', '}' -> {
                    depth--;
                    if (depth == 0) {
                        String last = code.substring(start, i);
                        if (!last.isBlank() || !args.isEmpty()) {
                            args.add(last);
                        }
                        return args;
                    }
                }
                case ',' -> {
                    if (depth == 1) {
                        args.add(code.substring(start, i));
                        start = i + 1;
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static boolean insideString(String code, int index) {
        char quote = 0;
        for (int i = 0; i < index; i++) {
            char c = code.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '/') {
                int end = code.indexOf('\n', i);
                if (end < 0 || end > index) {
                    return true; // inside a // comment
                }
                i = end;
            }
        }
        return quote != 0;
    }

    private static int lineOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String join(Set<Integer> counts) {
        List<String> parts = new ArrayList<>();
        for (int c : counts) {
            parts.add(Integer.toString(c));
        }
        return String.join(" or ", parts);
    }
}
