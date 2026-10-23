package com.example.ai.build;

import com.example.ai.api.MinecraftAI;
import com.example.network.AiNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Blueprints: a glowing paper item that carries a saved build. The build itself is kept in the
 * world folder (ai_builder/blueprints/<id>.json) and the paper only holds its id, so items stay
 * small and the blueprint keeps working after a restart.
 */
public final class BlueprintStore {

    public static final String KEY = "aib_blueprint";
    private static final Pattern ID = Pattern.compile("^[0-9a-z_]{1,40}$");

    private BlueprintStore() {
    }

    private static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("ai_builder").resolve("blueprints");
    }

    /** The blueprint id on a paper, or null for any other item. */
    public static String idOf(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.PAPER)) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        NbtCompound nbt = data.copyNbt();
        if (!nbt.contains(KEY)) {
            return null;
        }
        String id = nbt.getString(KEY);
        return ID.matcher(id).matches() ? id : null;
    }

    public static BuildRecord load(MinecraftServer server, String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return null;
        }
        try {
            return BuildRecord.fromJson(Files.readString(dir(server).resolve(id + ".json"), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * "손에 받기": save the design with the world and hand it over: an object becomes an object
     * item, a building a blueprint paper.
     */
    public static void give(ServerPlayerEntity player, BuildRecord record) {
        String id = record.id.replaceAll("[^0-9a-z_]", "");
        if (!ID.matcher(id).matches()) {
            AiNetworking.sendLog(player, "[History] 설계도를 만들 수 없어요 (잘못된 id).");
            return;
        }
        record.id = id;
        try {
            Path dir = dir(player.getServer());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + ".json"), record.toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AiNetworking.sendLog(player, "[History] 설계도를 저장하지 못했어요: " + exception.getMessage());
            return;
        }
        if (record.plan.isObject() && record.plan.ops.isEmpty()) {
            player.getInventory().offerOrDrop(ObjectItems.stackFor(record));
            AiNetworking.sendLog(player, "[History] 소품 아이템을 받았어요. 블록 윗면을 우클릭하면 그 위에 놓이고, 돌처럼 캐면 다시 아이템이 돼요.");
            return;
        }
        player.getInventory().offerOrDrop(paperFor(record));
        AiNetworking.sendLog(player, "[History] 설계도를 받았어요. 블록 윗면을 우클릭하면 그 위에 건물 앞면 가운데가 오도록 지어집니다.");
    }

    public static ItemStack paperFor(BuildRecord record) {
        ItemStack paper = new ItemStack(Items.PAPER);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, record.id);
        paper.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        boolean object = "object".equals(record.kind);
        String title = RebuildService.title(record);
        paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal((object ? "소품 설계도: " : "건물 설계도: ") + title)
            .setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.GOLD)));
        int[] b = record.plan.bounds();
        Style lore = Style.EMPTY.withItalic(false).withColor(Formatting.GRAY);
        paper.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("크기 " + (b[3] - b[0] + 1) + " × " + (b[4] - b[1] + 1) + " × " + (b[5] - b[2] + 1)).setStyle(lore),
            Text.literal("블록 윗면을 우클릭하면 그 위에").setStyle(lore),
            Text.literal("앞면 가운데를 맞춰 지어집니다").setStyle(lore))));
        paper.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return paper;
    }

    /**
     * Right-click with a blueprint on the top of {@code clicked}: the building's front edge,
     * centred, lands on the block above it. True if handled.
     */
    public static boolean use(ServerPlayerEntity player, ItemStack stack, net.minecraft.util.math.BlockPos clicked,
                              net.minecraft.util.math.Direction side) {
        String id = idOf(stack);
        if (id == null) {
            return false;
        }
        if (side != net.minecraft.util.math.Direction.UP) {
            player.sendMessage(Text.literal("블록 윗면을 클릭해 주세요").formatted(Formatting.GRAY), true);
            return true;
        }
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.notify(player, "§c설계도를 쓰려면 치트(OP 권한 2)가 필요해요.§r");
            return true;
        }
        BuildRecord record = load(player.getServer(), id);
        if (record == null) {
            AiNetworking.notify(player, "§c이 설계도의 내용을 찾을 수 없어요 (다른 월드에서 받은 설계도일 수 있어요).§r");
            return true;
        }
        if (record.plan.isObject() && record.plan.ops.isEmpty()) {
            RebuildService.placeObjectOn(player, record, clicked); // blueprints made before object items
        } else {
            RebuildService.placeAt(player, record, clicked.up());
        }
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        return true;
    }
}
