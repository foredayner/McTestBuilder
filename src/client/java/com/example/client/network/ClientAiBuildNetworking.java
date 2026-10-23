package com.example.client.network;

import net.minecraft.client.MinecraftClient;

public final class ClientAiBuildNetworking {

    private ClientAiBuildNetworking() {
    }

    public static void sendAiBuildRequest(String prompt) {
        sendAiBuildRequest(prompt, false);
    }

    public static void sendAiBuildRequest(String prompt, boolean useWebSearch) {
        sendAiBuildRequest(prompt, useWebSearch, false);
    }

    public static void sendAiBuildRequest(String prompt, boolean useWebSearch, boolean objectMode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String command = "aibuild " + (useWebSearch ? "--web " : "") + (objectMode ? "--object " : "") + prompt;
            client.player.networkHandler.sendChatCommand(command);
        }
    }
}
