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
        sendAiBuildRequest(prompt, useWebSearch, objectMode ? "--object " : "");
    }

    /** modeFlag: "", "--object " or "--gear=<kind> ". */
    public static void sendAiBuildRequest(String prompt, boolean useWebSearch, String modeFlag) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String command = "aibuild " + (useWebSearch ? "--web " : "") + modeFlag + prompt;
            client.player.networkHandler.sendChatCommand(command);
        }
    }
}
