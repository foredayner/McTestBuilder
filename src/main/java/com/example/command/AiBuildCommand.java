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
        // Flags may come in any order before the prompt: --web, --object.
        String prompt = rawPrompt.trim();
        boolean useWebSearch = webFlag;
        boolean objectMode = false;
        while (true) {
            if (prompt.startsWith("--web ")) {
                useWebSearch = true;
                prompt = prompt.substring(6).trim();
            } else if (prompt.startsWith("--object ")) {
                objectMode = true;
                prompt = prompt.substring(9).trim();
            } else {
                break;
            }
        }
        return executeBuild(source, prompt, useWebSearch, objectMode);
    }

    private static int executeBuild(ServerCommandSource source, String prompt, boolean useWebSearch, boolean objectMode) {
        source.sendFeedback(
            () -> Text.literal("AI " + (objectMode ? "object" : "build") + " request" + (useWebSearch ? " (web search)" : "") + ": " + prompt),
            false
        );

        AiController.requestBuild(source.getPlayer(), prompt, useWebSearch, objectMode);
        return 1;
    }
}
