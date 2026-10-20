package com.example.client;

import com.example.client.network.AiClientNetworking;
import com.example.config.AiBuilderConfig;
import com.example.network.AiNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AiBuildAssistantClient implements ClientModInitializer {

    private static KeyBinding OPEN_AI_UI_KEY;

    @Override
    public void onInitializeClient() {
        AiBuilderConfig.load();
        AiNetworking.registerPayloads();
        AiClientNetworking.registerReceivers();

        OPEN_AI_UI_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.minecraft-ai-build-assistant.open_ai_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.misc"
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_AI_UI_KEY.wasPressed()) {
                client.setScreen(new AiPromptScreen());
            }
        });
    }
}
