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
                server.getPlayerManager().sendCommandTree(player); // tells the client it is an operator
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
                AiClientState.addLog("[AI] 완성! Size: 7 x 6 x 7 (W x H x L), 9 command(s)");
                AiClientState.addLog("[History] 보관함에 추가했어요. 보관함에서 \"손에 받기\"로 꺼내 쓰세요.");
            });
            shot(context, () -> AiPromptScreen.open("CREATE", null), "02_create_pending");

            context.runOnClient(client -> {
                AiClientState.setMode(1);
            });
            shot(context, () -> AiPromptScreen.open("CREATE", null), "03_create_object_mode");
            context.runOnClient(client -> AiClientState.setMode(0));

            String houseId = BuildRecord.fromJson(records.get(0)).id;
            String appleId = BuildRecord.fromJson(records.get(1)).id;
            shot(context, () -> AiPromptScreen.open("HISTORY", null), "04_history_list");
            shot(context, () -> AiPromptScreen.open("HISTORY", houseId), "05_history_house");
            shot(context, () -> AiPromptScreen.open("HISTORY", appleId), "06_history_apple");
            // The shared 보관함: a friend's designs come from the server and sit with mine, as pictures.
            context.runOnClient(client -> {
                for (int i = 0; i < 14; i++) {
                    BuildRecord r = BuildRecord.fromJson(records.get(i % 2));
                    r.id = "90" + i;
                    r.createdAt -= 1000L * (i + 1);
                    r.owner = i % 3 == 0 ? "Mina" : "Jo";
                    BuildHistory.addShared(r);
                }
            });
            shot(context, () -> AiPromptScreen.open("HISTORY", null), "04b_history_grid");

            // The shared 작업 일지: one line per request, details on a click.
            String jobId = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                com.example.ai.WorkJournal.Job job = com.example.ai.WorkJournal.start(player,
                    "작은 돌벽돌 집: 7x7, 참나무 문, 유리창 두 개", "build", "", "", "");
                com.example.ai.WorkJournal.log(player, "[Review] 1 problem(s) found before running:\n- line 4: placeWall got 6 argument(s), needs 7.");
                com.example.ai.WorkJournal.log(player, "[AI] Asking the AI to fix it (retry 1/2)...");
                com.example.ai.WorkJournal.log(player, "[AI] 완성! Size: 7 x 6 x 7 (W x H x L), 9 command(s)");
                com.example.ai.WorkJournal.start(player, "빨간 사과", "object", "", "", "");
                com.example.ai.WorkJournal.log(player, "[AI] Error: the AI did not answer in time");
                com.example.ai.WorkJournal.start(player, "푸른 불꽃이 도는 검", "gear:sword", "푸른 불꽃 베기", "", "slash");
                return job.id;
            });
            context.waitTicks(3);
            shot(context, () -> AiPromptScreen.openJournal(null), "07_logs");
            shot(context, () -> AiPromptScreen.openJournal(jobId), "07b_log_detail");
            shot(context, () -> new AiSettingsScreen(null), "08_settings");

            // Object item: hand one to the player (its icon is the front picture), then set it on the ground.
            context.setScreen(() -> null);
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                BuildRecord apple = BuildRecord.fromJson(records.get(1));
                apple.icon = BuildPreview.of(apple).icon();
                com.example.ai.build.BlueprintStore.give(player, apple);
            });
            context.waitTicks(5);
            context.takeScreenshot("11_object_item_in_hand");
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var stack = player.getMainHandStack();
                com.example.ai.build.ObjectItems.use(player, stack, player.getBlockPos().add(-2, -1, 2));
            });
            context.waitTicks(10);
            context.takeScreenshot("12_object_item_used");
            context.setScreen(AiPromptScreen::openNearby);
            context.waitTicks(10);
            context.setScreen(() -> AiPromptScreen.open("HISTORY", null)); // redraw with the server's answer
            context.waitTicks(3);
            context.takeScreenshot("13_nearby_objects");
            context.setScreen(() -> null);

            // Equipment: the AI 공방 in 장비 mode, the 장비 tab, and a sword held in the hand.
            List<String> gear = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var world = player.getServerWorld();
                var origin = player.getBlockPos();
                BuildRecord sword = BuildRecord.create("에메랄드가 박힌 기사의 장검", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_SWORD, true));
                sword.kind = "gear";
                sword.gear = "sword";
                BuildRecord axe = BuildRecord.create("바이킹 전투 도끼", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_AXE, true));
                axe.kind = "gear";
                axe.gear = "axe";
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(sword));
                return List.of(sword.toJson(), axe.toJson());
            });
            context.runOnClient(client -> {
                for (String json : gear) {
                    BuildHistory.save(BuildRecord.fromJson(json));
                }
                AiClientState.setMode(2);
            });
            shot(context, () -> AiPromptScreen.open("CREATE", null), "16_create_gear_mode");
            context.runOnClient(client -> AiClientState.setMode(0));
            shot(context, () -> AiPromptScreen.openGear(null), "17_history_gear");
            String swordId = BuildRecord.fromJson(gear.get(0)).id;
            shot(context, () -> AiPromptScreen.openGear(swordId), "18_history_sword");
            context.setScreen(() -> null);
            // A newly held weapon is raised only after its attack cooldown.
            context.waitTicks(40);
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("19_sword_in_hand");
            // Reference: a vanilla iron sword in the same hand, same view.
            game.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD)));
            context.waitTicks(40);
            context.takeScreenshot("19b_vanilla_sword_in_hand");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("19c_vanilla_sword_third_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
                    com.example.ai.build.GearItems.stackFor(BuildRecord.fromJson(gear.get(0))));
            });
            context.waitTicks(40);
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("20_sword_third_person");

            // Painted pieces: a smooth gold sword with a see-through blade, and a painted ghost with coloured boxes.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                var world = p.getServerWorld();
                BuildRecord painted = BuildRecord.create("얼음 칼날 검", "TEST", "script",
                    Demo.plan(world, p.getBlockPos(), p, com.example.gametest.Demo.PAINTED_SWORD, true));
                painted.kind = "gear";
                painted.gear = "sword";
                p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(painted));
                Demo.place(p, p.getBlockPos(), Demo.plan(world, p.getBlockPos(), p, com.example.gametest.Demo.PAINTED), 0, "painted ghost");
            });
            context.waitTicks(40);
            context.takeScreenshot("20p_painted_third_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            context.waitTicks(5);
            context.takeScreenshot("20q_painted_first_person");

            // Swords ordered with attack motions, frozen at the wind-up and at the strike, in third and first person.
            for (String motion : List.of("thrust", "slash", "smash")) {
                game.getServer().runOnServer(server -> {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                    BuildRecord r = BuildRecord.fromJson(gear.get(0));
                    r.plan.motion = motion;
                    p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(r));
                });
                context.waitTicks(motion.equals("thrust") ? 40 : 5);
                for (float[] at : new float[][] { { 0.25F }, { 0.5F } }) {
                    String phase = at[0] < 0.3F ? "wind" : "strike";
                    context.runOnClient(client -> {
                        com.example.client.GearMotionClient.forceProgress = at[0];
                        client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                    });
                    context.waitTicks(2);
                    context.takeScreenshot("25_" + motion + "_" + phase + "_third_person");
                    context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
                    context.waitTicks(2);
                    context.takeScreenshot("26_" + motion + "_" + phase + "_first_person");
                }
            }
            context.runOnClient(client -> {
                com.example.client.GearMotionClient.forceProgress = -1;
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            });

            // A greatsword taller than the player: shortened in first person, real size and two-handed in third.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                BuildRecord great = BuildRecord.create("대검", "TEST", "script",
                    Demo.plan(p.getServerWorld(), p.getBlockPos(), p, Demo.GREATSWORD, true));
                great.kind = "gear";
                great.gear = "sword";
                p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(great));
            });
            context.waitTicks(40);
            context.takeScreenshot("29_greatsword_first_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("30_greatsword_third_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(3);
            context.takeScreenshot("31_greatsword_back");
            // A battle scythe in first person: its blade must be in view.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                BuildRecord scythe = BuildRecord.create("전투낫", "TEST", "script",
                    Demo.plan(p.getServerWorld(), p.getBlockPos(), p, Demo.SCYTHE, true));
                scythe.kind = "gear";
                scythe.gear = "sword";
                p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.example.ai.build.GearItems.stackFor(scythe));
            });
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            context.waitTicks(40);
            context.takeScreenshot("32_scythe_first_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("33_scythe_third_person");

            // Colour-dot effects: a pink heart flying forward and a magic circle on the ground, drawn by this game.
            context.runOnClient(client -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                var p = client.player;
                var look = p.getRotationVec(1.0F);
                var flat = new net.minecraft.util.math.Vec3d(look.x, 0, look.z).normalize();
                com.example.client.SparkRenderer.play("[" + com.example.ai.build.Spark.fromMap(java.util.Map.of(
                    "shape", "heart", "fill", true, "color", "ff66cc", "color2", "ffffff", "size", 0.5, "count", 70,
                    "moves", List.of("forward", "pulse"), "speed", 1.5, "ticks", 60)).toJson() + "]",
                    p.getEyePos().add(flat.multiply(1.5)).add(0, -0.3, 0), flat, 1);
                com.example.client.SparkRenderer.play("[" + com.example.ai.build.Spark.fromMap(java.util.Map.of(
                    "shape", "magic_circle", "color", "b44cff", "size", 1.4, "count", 90, "plane", "flat",
                    "moves", List.of("spin"), "speed", 0.3, "ticks", 60)).toJson() + "]",
                    p.getPos().add(flat.multiply(3.5)).add(0, 0.05, 0), flat, 1);
            });
            context.waitTicks(10);
            context.takeScreenshot("34_spark_heart_and_circle");
            // Richer effects: a glowing fire arc with a colour ramp, falling spark streaks, real flames, twinkling
            // stars and a ring of runes.
            context.runOnClient(client -> {
                var p = client.player;
                var look = p.getRotationVec(1.0F);
                var flat = new net.minecraft.util.math.Vec3d(look.x, 0, look.z).normalize();
                var at = p.getEyePos().add(flat.multiply(2.2)).add(0, -0.4, 0);
                java.util.function.Function<java.util.Map<String, Object>, String> one = m ->
                    "[" + com.example.ai.build.Spark.fromMap(m).toJson() + "]";
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.ofEntries(java.util.Map.entry("shape", "crescent"),
                    java.util.Map.entry("look", "glow"), java.util.Map.entry("colors", List.of("#ffffff", "#ffc040", "#ff3000")),
                    java.util.Map.entry("size", 1.1), java.util.Map.entry("count", 48), java.util.Map.entry("ticks", 60))), at, flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.ofEntries(java.util.Map.entry("shape", "burst"),
                    java.util.Map.entry("look", "streak"), java.util.Map.entry("colors", List.of("#ffffa0", "#ff6000")),
                    java.util.Map.entry("size", 0.5), java.util.Map.entry("count", 40), java.util.Map.entry("moves", List.of("scatter")),
                    java.util.Map.entry("speed", 3), java.util.Map.entry("gravity", 4), java.util.Map.entry("drag", 0.3),
                    java.util.Map.entry("vary", 0.6), java.util.Map.entry("ticks", 60))), at, flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.ofEntries(java.util.Map.entry("shape", "circle"),
                    java.util.Map.entry("particle", "flame"), java.util.Map.entry("size", 0.7), java.util.Map.entry("count", 30),
                    java.util.Map.entry("plane", "flat"), java.util.Map.entry("ticks", 20))), p.getPos().add(flat.multiply(2.2)), flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.ofEntries(java.util.Map.entry("shape", "ring"),
                    java.util.Map.entry("look", "star"), java.util.Map.entry("color", "#a0e8ff"), java.util.Map.entry("size", 0.9),
                    java.util.Map.entry("count", 14), java.util.Map.entry("dot", 0.2), java.util.Map.entry("ticks", 60))),
                    at.add(-1.6, 0.6, 0), flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.ofEntries(java.util.Map.entry("shape", "circle"),
                    java.util.Map.entry("look", "rune"), java.util.Map.entry("colors", List.of("#d080ff", "#6020c0")),
                    java.util.Map.entry("size", 1.0), java.util.Map.entry("count", 10), java.util.Map.entry("dot", 0.3),
                    java.util.Map.entry("plane", "flat"), java.util.Map.entry("moves", List.of("spin")), java.util.Map.entry("speed", 0.2),
                    java.util.Map.entry("ticks", 60))), p.getPos().add(flat.multiply(3.5)).add(1.5, 0.05, 0), flat, 1);
            });
            context.waitTicks(8);
            context.takeScreenshot("34b_spark_looks");
            // Smooth pieces: a blade's swipe, a shockwave, a column of light, a magic circle and an orbiting ribbon.
            context.waitTicks(60);
            context.runOnClient(client -> {
                var p = client.player;
                var look = p.getRotationVec(1.0F);
                var flat = new net.minecraft.util.math.Vec3d(look.x, 0, look.z).normalize();
                var side = new net.minecraft.util.math.Vec3d(-flat.z, 0, flat.x);
                java.util.function.Function<java.util.Map<String, Object>, String> one = m ->
                    "[" + com.example.ai.build.Spark.fromMap(m).toJson() + "]";
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.of("form", "swipe", "colors",
                    List.of("#ffffff", "#ffc040", "#ff3000"), "size", 1.4, "arc", 170, "width", 0.45, "plane", "side", "ticks", 30)),
                    p.getEyePos().add(flat.multiply(2.5)).add(0, -0.5, 0), side, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.of("form", "shockwave", "color", "#a0e0ff",
                    "size", 2.4, "width", 0.3, "ticks", 40)), p.getPos().add(flat.multiply(3)), flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.of("form", "pillar", "colors",
                    List.of("#ffffff", "#ffe080"), "size", 0.8, "width", 0.3, "ticks", 60)),
                    p.getPos().add(flat.multiply(1.8)).add(side.multiply(1.6)), flat, 1);
                com.example.client.SparkRenderer.play(one.apply(java.util.Map.of("form", "disc", "colors",
                    List.of("#e0b0ff", "#7020ff"), "size", 1.3, "plane", "flat", "moves", List.of("spin"), "speed", 0.2, "ticks", 60)),
                    p.getPos().add(flat.multiply(3.5)).add(side.multiply(-2)).add(0, 0.05, 0), flat, 1);
                com.example.client.SparkRenderer.attachForTests(p, one.apply(java.util.Map.of("form", "ribbon", "colors",
                    List.of("#ffffff", "#40c0ff"), "size", 0.8, "width", 0.6, "moves", List.of("orbit"), "speed", 0.6, "ticks", 14)),
                    "head");
            });
            context.waitTicks(10);
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(4);
            context.takeScreenshot("34c_spark_forms");
            context.runOnClient(client -> {
                com.example.client.SparkRenderer.clearForTests();
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            });
            // A lightning bolt striking ahead (the 'lightning' preset's bolt) and a bow's beam shot reaching past it.
            context.runOnClient(client -> {
                var p = client.player;
                var look = p.getRotationVec(1.0F);
                var flat = new net.minecraft.util.math.Vec3d(look.x, 0, look.z).normalize();
                var side = new net.minecraft.util.math.Vec3d(-flat.z, 0, flat.x);
                var bolt = com.example.ai.build.Spark.fromMap(java.util.Map.of("form", "bolt", "colors", List.of("#ffffff", "#a0d0ff"),
                    "size", 1.0, "width", 0.3, "ticks", 60));
                com.example.client.SparkRenderer.play("[" + bolt.toJson() + "]", p.getPos().add(flat.multiply(7)).add(side.multiply(-1.5)), flat, 1);
                var shot = com.example.ai.build.Spark.Shot.fromMap(java.util.Map.of("kind", "beam", "color", "#ff4040", "color2", "#ffc0c0",
                    "width", 0.25, "ticks", 20));
                var from = p.getEyePos().add(0, -0.18, 0).add(flat.multiply(0.6));
                var to = p.getPos().add(flat.multiply(14)).add(side.multiply(2)).add(0, 1, 0);
                var beam = com.example.ai.build.GearEffects.beamSpark(shot, from.distanceTo(to));
                beam.ticks = 60;
                com.example.client.SparkRenderer.play("[" + beam.toJson() + "]", from, to.subtract(from), 1);
            });
            context.waitTicks(6);
            context.takeScreenshot("34d_lightning_bolt_and_beam");
            context.runOnClient(client -> com.example.client.SparkRenderer.clearForTests());
            // A ready-made layered effect played as a preview (the 보관함's "이펙트 보기"): holy light and a flame aura.
            context.runOnClient(client -> {
                var fx = new com.example.ai.build.Spark.Effects();
                fx.attack.addAll(com.example.ai.build.EffectPresets.build("holy", null));
                fx.idle.addAll(com.example.ai.build.EffectPresets.build("flame_aura", null));
                com.example.client.SparkRenderer.preview(fx, false);
            });
            context.waitTicks(18);
            context.takeScreenshot("34d_preset_preview");
            context.runOnClient(client -> com.example.client.SparkRenderer.clearForTests());
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

            // A bow with its own arrow shape: in the hand, and one of its arrows hanging in the air.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
                BuildRecord bow = BuildRecord.create("불꽃 활", "TEST", "script",
                    Demo.plan(p.getServerWorld(), p.getBlockPos(), p, Demo.BOW, true));
                bow.kind = "gear";
                bow.gear = "bow";
                net.minecraft.item.ItemStack stack = com.example.ai.build.GearItems.stackFor(bow);
                p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
                var arrow = new com.example.content.AibArrowEntity(p.getServerWorld(), p,
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW), stack);
                var look = p.getRotationVec(1.0F);
                var flat = new net.minecraft.util.math.Vec3d(look.x, 0, look.z).normalize();
                var at = p.getEyePos().add(flat.multiply(2.2)).add(0, -0.3, 0);
                arrow.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                arrow.setNoGravity(true);
                // Flying across the view (to the right), very slowly.
                arrow.setVelocity(-flat.z, 0.0, flat.x, 0.01F, 0.0F);
                p.getServerWorld().spawnEntity(arrow);
            });
            context.waitTicks(40);
            context.takeScreenshot("27_bow_first_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("28_bow_third_person");

            // Worn equipment: the example helmet and wings on the player, from the front and the back.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var world = player.getServerWorld();
                var origin = player.getBlockPos();
                BuildRecord helmet = BuildRecord.create("곰 탈", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_HELMET, true));
                helmet.kind = "gear";
                helmet.gear = "helmet";
                BuildRecord wings = BuildRecord.create("천사 날개", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_WINGS, true));
                wings.kind = "gear";
                wings.gear = "back";
                player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, com.example.ai.build.GearItems.stackFor(helmet));
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, com.example.ai.build.GearItems.stackFor(wings));
                // Jeans and sneakers: modelled for one leg, drawn on both.
                BuildRecord pants = BuildRecord.create("청바지", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_PANTS, true));
                pants.kind = "gear";
                pants.gear = "pants";
                BuildRecord shoes = BuildRecord.create("운동화", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_SHOES, true));
                shoes.kind = "gear";
                shoes.gear = "shoes";
                player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, com.example.ai.build.GearItems.stackFor(pants));
                player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, com.example.ai.build.GearItems.stackFor(shoes));
            });
            context.waitTicks(10);
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("21_worn_front");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("22_worn_back");
            // The same in pixel art: a knight helmet, jeans and angel wings, and a pixel sword in the hand.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var world = player.getServerWorld();
                var origin = player.getBlockPos();
                String[][] worn = {
                    { com.example.ai.AiController.EXAMPLE_PIXEL_HELMET, "helmet" },
                    { com.example.ai.AiController.EXAMPLE_PIXEL_WINGS, "back" },
                    { com.example.ai.AiController.EXAMPLE_PIXEL_PANTS, "pants" },
                    { com.example.ai.AiController.EXAMPLE_PIXEL_SWORD, "sword" } };
                for (String[] w : worn) {
                    BuildRecord r = BuildRecord.create("pixel " + w[1], "TEST", "script", Demo.plan(world, origin, player, w[0], true));
                    r.kind = "gear";
                    r.gear = w[1];
                    var stack = com.example.ai.build.GearItems.stackFor(r);
                    switch (w[1]) {
                        case "helmet" -> player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, stack);
                        case "back" -> player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, stack);
                        case "pants" -> player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, stack);
                        default -> player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
                    }
                }
            });
            context.waitTicks(10);
            context.takeScreenshot("22q_pixel_worn_back");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("22p_pixel_worn_front");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            context.waitTicks(40);
            context.takeScreenshot("22r_pixel_sword_first_person");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            // A beanie and a big balloon (asked to be big: it goes past the usual size) above the head.
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var world = player.getServerWorld();
                var origin = player.getBlockPos();
                BuildRecord hat = BuildRecord.create("털모자", "TEST", "script",
                    Demo.plan(world, origin, player, com.example.ai.AiController.EXAMPLE_HAT, true));
                hat.kind = "gear";
                hat.gear = "hat";
                BuildRecord balloon = BuildRecord.create("4블럭 풍선", "TEST", "script", Demo.plan(world, origin, player, Demo.BALLOON, true));
                balloon.kind = "gear";
                balloon.gear = "back";
                player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, com.example.ai.build.GearItems.stackFor(hat));
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, com.example.ai.build.GearItems.stackFor(balloon));
            });
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -10");
            context.waitTicks(10);
            context.takeScreenshot("23_hat_and_big_balloon");
            // Looking up from behind: the wearer is off screen but the balloon must still be drawn.
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -75");
            context.waitTicks(10);
            context.takeScreenshot("24_balloon_looking_up");
            game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 18");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            game.getServer().runOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.item.ItemStack.EMPTY);
                player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.item.ItemStack.EMPTY);
                player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.item.ItemStack.EMPTY);
            });
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

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
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("14_scaled_apples");

            // Objects are solid: drop the player onto the x5 apple and they stay on top of it.
            double[] top = game.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
                var boxes = player.getServerWorld().getEntitiesByClass(com.example.content.SolidHitboxEntity.class,
                    player.getBoundingBox().expand(12), e -> e.getCommandTags().stream().anyMatch(t -> t.startsWith("aib_") && t.length() > 8));
                net.minecraft.util.math.Box biggest = null;
                for (var b : boxes) {
                    var bb = b.getBoundingBox();
                    if (biggest == null || bb.getLengthY() * bb.getLengthX() > biggest.getLengthY() * biggest.getLengthX()) {
                        biggest = bb;
                    }
                }
                return biggest == null ? new double[] { 0, 0, 0 } : new double[] { biggest.getCenter().x, biggest.maxY, biggest.getCenter().z };
            });
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 70", top[0], top[1] + 1.5, top[2]));
            context.waitTicks(40);
            double standingY = context.computeOnClient(client -> client.player.getY());
            if (standingY < top[1] - 0.1) {
                throw new AssertionError("the player fell through the object: y " + standingY + ", top " + top[1]);
            }
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(3);
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("15_standing_on_big_apple");
            context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

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
