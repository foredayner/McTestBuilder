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

        // Solid object hitboxes are invisible; object items draw their own picture.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            com.example.content.AibContent.HITBOX, net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        net.minecraft.client.render.item.model.special.SpecialModelTypes.ID_MAPPER.put(
            ObjectIconRenderer.TYPE_ID, ObjectIconRenderer.Unbaked.CODEC);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            (context, tickCounter) -> ObjectBreaking.renderHud(context));

        OPEN_AI_UI_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.minecraft-ai-build-assistant.open_ai_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.misc"
            )
        );

        REMOVE_OBJECT_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.minecraft-ai-build-assistant.remove_object",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_DELETE,
                "key.categories.misc"
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_AI_UI_KEY.wasPressed()) {
                AiClientState.clearConversation();
                client.setScreen(new AiPromptScreen());
            }
            while (REMOVE_OBJECT_KEY.wasPressed()) {
                removeLookedAtObject(client);
            }
            ObjectBreaking.tick(client);
        });
    }

    private static KeyBinding REMOVE_OBJECT_KEY;
    private static int pendingRemoveId = -1;
    private static long pendingRemoveAt = 0;

    /** Look at an AI object and press the key twice within 3 seconds to remove it. */
    private static void removeLookedAtObject(net.minecraft.client.MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit
            && hit.getEntity() instanceof net.minecraft.entity.decoration.InteractionEntity) {
            int id = hit.getEntity().getId();
            long now = System.currentTimeMillis();
            if (id == pendingRemoveId && now - pendingRemoveAt < 3000) {
                pendingRemoveId = -1;
                AiClientNetworking.sendRemoveObject(id, "");
            } else {
                pendingRemoveId = id;
                pendingRemoveAt = now;
                client.player.sendMessage(net.minecraft.text.Text.literal("한 번 더 누르면 이 소품을 지워요")
                    .formatted(net.minecraft.util.Formatting.YELLOW), true);
            }
            return;
        }
        pendingRemoveId = -1;
        client.player.sendMessage(net.minecraft.text.Text.literal("지울 소품을 조준하고 누르세요")
            .formatted(net.minecraft.util.Formatting.GRAY), true);
    }
}
