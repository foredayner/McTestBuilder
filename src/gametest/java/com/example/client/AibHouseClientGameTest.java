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

    /** The 1920s props in a row against a wall, with tables for those that stand on one. */
    static final String GALLERY_1920S = """
        ai.clearArea(-15, 0, 42, 31, 5, 6)
        ai.placeFloor(-15, -1, 42, 31, 5, 'dark_oak_planks')
        ai.placeWall(-15, 0, 46, 4, 31, 'x', 'stripped_spruce_log[axis=x]')
        ai.prop('grandfather_clock', -11, 0, 45, 'north')
        ai.prop('rocking_chair', -9, 0, 45, 'north')
        ai.prop('globe', -7, 0, 45, 'north')
        ai.prop('sewing_machine', -5, 0, 45, 'north')
        ai.placeFloor(-3, 0, 45, 9, 1, 'spruce_slab[type=top]')
        ai.prop('gramophone', -3, 1, 45, 'north')
        ai.prop('typewriter', -1, 1, 45, 'north')
        ai.prop('candlestick_phone', 1, 1, 45, 'north')
        ai.prop('oil_lamp', 2, 1, 45, 'north')
        ai.prop('cathedral_radio', 3, 1, 45, 'north')
        ai.prop('candelabra', 5, 1, 45, 'north')
        """;

    /** Smooth round pieces: solid colours instead of blocks. */
    static final String SMOOTH_DUCK = com.example.ai.AiController.EXAMPLE_DUCK
        .replace("'yellow_concrete'", "'#f4d03f'").replace("'yellow_terracotta'", "'#d9a520'")
        .replace("'orange_concrete'", "'#e67e22'").replace("'black_concrete'", "'#1e1e1e'")
        .replace("'red_concrete'", "'#c0392b'").replace("'white_concrete'", "'#ffffff'");

    static final String SMOOTH_APPLE = """
        def z = 2.6
        ai.addSphere('#c0392b', 0, 0.42, z, 0.4)                           // apple
        ai.addEllipsoid('#a93226', 0, 0.8, z, 0.14, 0.05, 0.14)           // the hollow at the top
        ai.addCylinder('#6e4b2a', 0, 0.78, z, 0.03, 0.2)                   // stem
        ai.addCapsule('#3c9a3c', 0.02, 0.92, z, 0.2, 1.0, z - 0.05, 0.05, 0.02) // leaf
        """;

    static final String SMOOTH_SNOWMAN = """
        def z = 2.6
        ai.addSphere('#f4f6f7', 0, 0.42, z, 0.42)                          // bottom
        ai.addSphere('#f4f6f7', 0, 1.05, z, 0.3)                           // middle
        ai.addSphere('#f4f6f7', 0, 1.52, z, 0.22)                          // head
        ai.addCapsule('#e67e22', 0, 1.52, z - 0.2, 0, 1.5, z - 0.5, 0.05, 0.0) // carrot nose
        ai.addCylinder('#1e1e1e', 0, 1.7, z, 0.24, 0.03)                   // hat brim
        ai.addCylinder('#1e1e1e', 0, 1.72, z, 0.15, 0.25)                  // hat
        ai.addSphere('#1e1e1e', 0, 1.13, z - 0.29, 0.035)                  // buttons
        ai.addSphere('#1e1e1e', 0, 0.98, z - 0.3, 0.035)
        ai.mirrorOn()
        ai.addSphere('#1e1e1e', 0.08, 1.6, z - 0.19, 0.03)                 // eyes
        ai.addCapsule('#6e4b2a', 0.27, 1.1, z, 0.6, 1.35, z, 0.025, 0.02)  // stick arms
        ai.mirrorOff()
        """;

    static final String SMOOTH_MUSHROOM = """
        def z = 2.6
        ai.addCylinder('#f2ead3', 0, 0, z, 0.16, 0.55)                     // stem
        ai.addEllipsoid('#c0392b', 0, 0.6, z, 0.45, 0.28, 0.45)            // cap
        ai.addEllipsoid('#f2ead3', 0, 0.5, z, 0.4, 0.1, 0.4)               // underside
        ai.addSphere('#ffffff', 0, 0.87, z, 0.07)                          // spots
        ai.addSphere('#ffffff', 0.25, 0.73, z - 0.22, 0.06)
        ai.addSphere('#ffffff', -0.28, 0.7, z - 0.15, 0.05)
        ai.addSphere('#ffffff', 0.05, 0.75, z - 0.33, 0.055)
        ai.addCone('#3c9a3c', 0.4, 0, z - 0.2, 0.08, 0.25)                 // grass
        ai.addCone('#3c9a3c', 0.5, 0, z - 0.1, 0.06, 0.18)
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
                Demo.place(player, origin, Demo.plan(world, origin, player, GALLERY_1920S), 0, "1920년대 소품");
                // Round things: the duck of blocks, then smooth ones in colours.
                String[] round = { com.example.ai.AiController.EXAMPLE_DUCK, SMOOTH_DUCK, SMOOTH_APPLE, SMOOTH_SNOWMAN, SMOOTH_MUSHROOM };
                for (int i = 0; i < round.length; i++) {
                    BlockPos at = origin.add(20 + (i < 2 ? i * 2 : 1 + i * 2), 0, 0);
                    Demo.place(player, at, Demo.plan(world, at, player, round[i]), 0, "둥근 소품 " + i);
                }
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
            view(context, game, o, -7.5, 1.2, 39.5, 0, 12, "39_props_1920s_left");
            view(context, game, o, 1, 1.4, 41.2, 0, 22, "40_props_1920s_right");
            view(context, game, o, 21, 1.6, 0.4, 0, 12, "37_duck_blocks_and_smooth");
            view(context, game, o, 28, 1.8, -0.6, 0, 12, "38_smooth_apple_snowman_mushroom");
        }
    }

    private static void view(ClientGameTestContext context, TestSingleplayerContext game, BlockPos o, double x, double y, double z,
                             float yaw, float pitch, String name) {
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f",
            o.getX() + 0.5 + x, o.getY() + y, o.getZ() + 0.5 + z, yaw, pitch));
        context.waitTicks(20);
        try {
            game.getClientWorld().waitForChunksRender();
        } catch (AssertionError slow) {
            context.waitTicks(100); // the test machine draws slowly with many pieces about: take the picture anyway
        }
        context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
        context.takeScreenshot(name);
    }
}
