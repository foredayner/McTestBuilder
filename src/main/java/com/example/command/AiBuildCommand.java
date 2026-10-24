package com.example.command;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import com.example.ai.AiController;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AiBuildCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                literal("aibuild")
                    .requires(source -> source.hasPermissionLevel(com.example.network.AiNetworking.OP_LEVEL))
                    .then(literal("--web")
                        .then(argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> executeBuild(context.getSource(), StringArgumentType.getString(context, "prompt"), true))
                        )
                    )
                    .then(argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> executeBuild(context.getSource(), StringArgumentType.getString(context, "prompt"), false))
                    )
            );
        });
    }

    private static int executeBuild(ServerCommandSource source, String rawPrompt, boolean webFlag) {
        // Flags may come in any order before the prompt: --web, --object, --gear=<kind>.
        String prompt = rawPrompt.trim();
        boolean useWebSearch = webFlag;
        String mode = "build";
        while (true) {
            if (prompt.startsWith("--web ")) {
                useWebSearch = true;
                prompt = prompt.substring(6).trim();
            } else if (prompt.startsWith("--object ")) {
                mode = "object";
                prompt = prompt.substring(9).trim();
            } else if (prompt.startsWith("--gear=") && prompt.indexOf(' ') > 0) {
                mode = "gear:" + com.example.content.GearKind.of(prompt.substring(7, prompt.indexOf(' '))).id();
                prompt = prompt.substring(prompt.indexOf(' ') + 1).trim();
            } else {
                break;
            }
        }
        // Equipment effect fields ride after the request: "<request>||hit:<text>||idle:<text>".
        String fxHit = "";
        String fxIdle = "";
        int idle = prompt.indexOf("||idle:");
        if (idle >= 0) {
            fxIdle = prompt.substring(idle + 7).trim();
            prompt = prompt.substring(0, idle).trim();
        }
        int hit = prompt.indexOf("||hit:");
        if (hit >= 0) {
            fxHit = prompt.substring(hit + 6).trim();
            prompt = prompt.substring(0, hit).trim();
        }
        final String finalHit = fxHit;
        final String finalIdle = fxIdle;
        final String finalPrompt = prompt;
        final String finalMode = mode;
        final boolean web = useWebSearch;
        source.sendFeedback(
            () -> Text.literal("AI " + finalMode + " request" + (web ? " (web search)" : "") + ": " + finalPrompt),
            false
        );
        AiController.requestBuild(source.getPlayer(), finalPrompt, web, finalMode, finalHit, finalIdle);
        return 1;
    }
}
