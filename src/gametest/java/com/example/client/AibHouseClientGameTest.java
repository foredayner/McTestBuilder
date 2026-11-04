package com.example.client;

import com.example.ai.AiController;
import com.example.ai.build.BuildPlan;
import com.example.gametest.Demo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Pictures of the example house of the building instructions (helpers and library props) and of
 * every library prop in a row, built through the same pipeline as an accepted AI build.
 */
public class AibHouseClientGameTest implements FabricClientGameTest {

    /** Every prop in a row against a wall (the camera looks +Z at it). */
    static final String GALLERY = """
        ai.clearArea(-15, 0, 32, 31, 5, 6)
        ai.placeFloor(-15, -1, 32, 31, 5, 'smooth_stone')
        ai.placeWall(-15, 0, 36, 4, 31, 'x', 'white_concrete')
        ai.placeWall(8, 1, 36, 2, 2, 'x', 'glass')
        ai.prop('sofa', -14, 0, 35, 'north')
        ai.prop('armchair', -10, 0, 35, 'north')
        ai.prop('wall_tv', -8, 0, 35, 'north')
        ai.prop('floor_lamp', -5, 0, 35, 'north')
        ai.prop('fridge', -3, 0, 35, 'north')
        ai.prop('wall_clock', -1, 0, 35, 'north')
        ai.placeBlock(1, 0, 35, 'spruce_planks')
        ai.prop('desk_phone', 1, 1, 35, 'north')
        ai.placeBlock(3, 0, 35, 'spruce_planks')
        ai.prop('computer', 3, 1, 35, 'north')
        ai.placeFloor(5, 3, 32, 2, 4, 'white_concrete')
        ai.prop('ceiling_light', 5, 2, 35, 'north')
        ai.prop('curtain', 8, 1, 35, 'north')
        ai.prop('partition', 11, 0, 34, 'north')
        """;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("AIB_BENCH_ONLY") != null) {
            return;
        }
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE))
                .create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runCommand("time set noon");
            game.getServer().runCommand("weather clear");
            game.getServer().runCommand("gamerule doDaylightCycle false");
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            context.waitTicks(5);
            BlockPos o = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                server.getPlayerManager().sendCommandTree(player);
                var world = player.getServerWorld();
                BlockPos origin = player.getBlockPos();
                BuildPlan house = Demo.plan(world, origin, player, AiController.EXAMPLE_HOUSE);
                Demo.place(player, origin, house, 0, "예시 집");
                BuildPlan gallery = Demo.plan(world, origin, player, GALLERY);
                Demo.place(player, origin, gallery, 0, "소품 모음");
                player.getAbilities().flying = true; // the camera stays where it is put
                player.sendAbilitiesUpdate();
                return origin;
            });
            context.waitTicks(900);
            view(context, game, o, 0, 6, -11, 0, 15, "30_house_front");
            view(context, game, o, 11, 7, 18, 145, 20, "31_house_back_corner");
            view(context, game, o, -2.5, 1, 10.4, 180, 12, "32_house_living_room");
            view(context, game, o, -2.5, 1, 4.6, 0, 12, "33_house_living_back");
            view(context, game, o, 2.5, 1, 4.6, -25, 15, "34_house_kitchen");
            view(context, game, o, -8, 1, 28.5, 0, 10, "35_props_left");
            view(context, game, o, 5, 1, 28.5, 0, 10, "36_props_right");
        }
    }

    private static void view(ClientGameTestContext context, TestSingleplayerContext game, BlockPos o, double x, double y, double z,
                             float yaw, float pitch, String name) {
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f",
            o.getX() + 0.5 + x, o.getY() + y, o.getZ() + 0.5 + z, yaw, pitch));
        context.waitTicks(20);
        game.getClientWorld().waitForChunksRender();
        context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
        context.takeScreenshot(name);
    }
}
