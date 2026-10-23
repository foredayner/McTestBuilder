package com.example.client;

import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.example.gametest.Demo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Screenshots of the mod in a real client (run headless in CI under xvfb):
 * a demo house and object built through the same pipeline, then every page of the window.
 */
public class AibClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("AIB_BENCH_ONLY") != null) {
            return; // the model benchmark run only wants its own pictures
        }
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE))
                .create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runCommand("time set noon");
            game.getServer().runCommand("weather clear");
            game.getServer().runCommand("gamerule doDaylightCycle false");
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 18");
            context.waitTicks(5);

            // Build the demo house and an apple in front of the player, like an accepted AI build.
            List<String> records = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                // The GameTest server gives new operators level 0; set level 4 directly.
                server.getPlayerManager().getOpList().add(
                    new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                var world = player.getServerWorld();
                var origin = player.getBlockPos();
                BuildPlan house = Demo.plan(world, origin, player, Demo.HOUSE);
                BuildRecord houseRecord = Demo.place(player, origin, house, 0, "작은 돌벽돌 집: 7x7, 참나무 문, 유리창 두 개, 짙은 참나무 지붕, 안쪽에 의자 계단");
                BuildPlan apple = Demo.plan(world, origin, player, Demo.APPLE);
                BuildRecord appleRecord = Demo.place(player, origin, apple, 0, "한 블록보다 작은 빨간 사과, 갈색 꼭지와 초록 잎");
                return List.of(houseRecord.toJson(), appleRecord.toJson());
            });
            context.waitTicks(20);
            game.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("01_world_house_and_apple");

            // Fill the window with a realistic session.
            context.runOnClient(client -> {
                for (String json : records) {
                    BuildRecord r = BuildRecord.fromJson(json);
                    BuildHistory.save(r);
                }
                AiClientState.addLog("[Request] 작은 돌벽돌 집: 7x7, 참나무 문, 유리창 두 개, 짙은 참나무 지붕");
                AiClientState.addLog("[Waiting...] 건물 설계 중입니다. 잠시 기다려 주세요.");
                AiClientState.addLog("[Review] 1 problem(s) found before running:\n- line 4: placeWall got 6 argument(s), needs 7.");
                AiClientState.addLog("[AI] Asking the AI to fix it (retry 1/2)...");
                AiClientState.addLog("[AI] Approval required.");
                AiClientState.addLog("  Build location: X=0 Y=-60 Z=4\n  Size: 7 x 6 x 7 (W x H x L), 9 command(s)");
                AiClientState.setPendingApproval("Build location: X=0 Y=-60 Z=4");
            });
            shot(context, () -> AiPromptScreen.open("CREATE", null), "02_create_pending");

            context.runOnClient(client -> {
                AiClientState.clearPendingApproval();
                AiClientState.addLog("[AI] Complete!");
                AiClientState.addLog("[History] Saved to the History tab.");
                AiClientState.toggleObjectMode();
            });
            shot(context, () -> AiPromptScreen.open("CREATE", null), "03_create_object_mode");
            context.runOnClient(client -> AiClientState.toggleObjectMode());

            String houseId = BuildRecord.fromJson(records.get(0)).id;
            String appleId = BuildRecord.fromJson(records.get(1)).id;
            shot(context, () -> AiPromptScreen.open("HISTORY", null), "04_history_list");
            shot(context, () -> AiPromptScreen.open("HISTORY", houseId), "05_history_house");
            shot(context, () -> AiPromptScreen.open("HISTORY", appleId), "06_history_apple");
            shot(context, () -> AiPromptScreen.open("LOGS", null), "07_logs");
            shot(context, () -> new AiSettingsScreen(null), "08_settings");

            // Blueprint paper: hand one to the player, then use it on the ground to the left of the house.
            context.setScreen(() -> null);
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                BuildRecord apple = BuildRecord.fromJson(records.get(1));
                com.example.ai.build.BlueprintStore.give(player, apple);
            });
            context.waitTicks(5);
            context.takeScreenshot("11_blueprint_in_hand");
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var stack = player.getMainHandStack();
                com.example.ai.build.BlueprintStore.use(player, stack, player.getBlockPos().add(-2, 0, 2));
            });
            context.waitTicks(10);
            context.takeScreenshot("12_blueprint_used");
            context.setScreen(AiPromptScreen::openNearby);
            context.waitTicks(10);
            context.setScreen(() -> AiPromptScreen.open("HISTORY", null)); // redraw with the server's answer
            context.waitTicks(3);
            context.takeScreenshot("13_nearby_objects");
            context.setScreen(() -> null);

            // The same apple placed at x1, x3 and x5, side by side somewhere empty.
            game.getServer().runCommand("execute as @a at @s run tp @s ~40 ~ ~ 0 18");
            context.waitTicks(5);
            game.getClientWorld().waitForChunksRender();
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var origin = player.getBlockPos();
                BuildRecord apple = BuildRecord.create("사과", "TEST", "script",
                    Demo.plan(player.getServerWorld(), origin, player, Demo.APPLE));
                int[][] spots = { { -4, 100 }, { -1, 300 }, { 4, 500 } };
                for (int[] spot : spots) {
                    BuildRecord r = com.example.ai.build.RebuildService.withScale(player, apple, spot[1]);
                    Demo.place(player, origin.add(spot[0], 0, 0), r.plan, 0, "사과 x" + spot[1] / 100);
                }
            });
            context.waitTicks(30);
            game.getServer().runCommand("execute as @a at @s run tp @s ~1 ~1 ~-5 0 15");
            context.waitTicks(10);
            game.getClientWorld().waitForChunksRender();
            context.takeScreenshot("14_scaled_apples");

            // Small window: the settings form must scroll, nothing may overlap.
            context.getInput().resizeWindow(640, 360);
            context.waitTicks(2);
            shot(context, () -> new AiSettingsScreen(null), "09_settings_small_window");
            shot(context, () -> AiPromptScreen.open("CREATE", null), "10_create_small_window");
            context.getInput().resizeWindow(854, 480);
            context.setScreen(() -> null);
        }
    }

    private static void shot(ClientGameTestContext context, java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> screen, String name) {
        context.setScreen(screen);
        context.waitTicks(3);
        context.takeScreenshot(name);
    }
}
