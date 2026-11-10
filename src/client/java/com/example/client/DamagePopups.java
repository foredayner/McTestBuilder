package com.example.client;

import com.example.AiBuildAssistantMod;
import com.example.config.AiBuilderConfig;
import com.example.network.AiNetworkPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Damage numbers over what an AI weapon hits (like MapleStory): each pops in, floats up and
 * fades in about a second; hits in a row stack upward. Drawn with the mod's own number glyphs
 * (tools/damage_font.py) in the player's chosen style (classic, flame, ice), in three strengths:
 * with random damage the roll picks the strength (0-3 low, 4-6 mid, 7-9 high, and a sparkle on a
 * 9); without, the middle one. A roll of 0 is a grey MISS. Only the damage itself is shown.
 */
public final class DamagePopups {

    private DamagePopups() {
    }

    static final int LIFE = 26;
    public static final List<String> STYLES = List.of("classic", "flame", "ice");
    static final Identifier SHEET = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/damage_numbers.png");
    static final Identifier METRICS = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/damage_numbers.json");

    /** The glyph sheet's layout: cell size, characters, and each glyph's inked span per style. */
    static int cellW = 56, cellH = 64;
    static String chars = "0123456789.MIS*";
    static Map<String, Map<Character, int[]>> spans;

    /** One number in the air. */
    static final class Popup {
        final int entity;
        final double x, y, z;
        final String text;
        final int tier;      // 0 low, 1 mid, 2 high
        final boolean miss;
        final boolean sparkle;
        final float scale;
        int age;

        Popup(int entity, double x, double y, double z, String text, int tier, boolean miss, boolean sparkle, float scale) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.tier = tier;
            this.miss = miss;
            this.sparkle = sparkle;
            this.scale = scale;
        }
    }

    static final List<Popup> POPUPS = new ArrayList<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AiNetworkPayloads.DamageNumberS2CPayload.ID,
            (payload, context) -> context.client().execute(() -> add(payload.entity(), payload.amount(), payload.roll())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.isPaused()) {
                return;
            }
            synchronized (POPUPS) {
                POPUPS.removeIf(p -> ++p.age > LIFE);
                if (client.world == null) {
                    POPUPS.clear();
                }
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(DamagePopups::render);
    }

    /** The player's style (classic, flame or ice). */
    public static String style() {
        String s = AiBuilderConfig.get().damageStyle;
        return STYLES.contains(s) ? s : "classic";
    }

    public static String korean(String style) {
        return switch (style) {
            case "flame" -> "불꽃";
            case "ice" -> "얼음";
            default -> "클래식";
        };
    }

    /** The next style, saved. */
    public static void cycleStyle() {
        AiBuilderConfig.get().damageStyle = STYLES.get((STYLES.indexOf(style()) + 1) % STYLES.size());
        AiBuilderConfig.save();
    }

    /** The damage as shown: whole when whole, else one decimal. */
    static String text(float amount) {
        return Math.abs(amount - Math.round(amount)) < 0.05 ? Integer.toString(Math.round(amount))
            : String.format(Locale.ROOT, "%.1f", amount);
    }

    /** Strength from the roll (-1 = not rolled: the middle one). */
    static int tier(int roll) {
        return roll < 0 ? 1 : roll >= 7 ? 2 : roll >= 4 ? 1 : 0;
    }

    static void add(int entityId, float amount, int roll) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        Entity e = client.world.getEntityById(entityId);
        if (e == null) {
            return;
        }
        boolean miss = roll == 0;
        float scale = miss ? 0.8f : roll < 0 ? 1.0f : 0.85f + roll * 0.05f;
        synchronized (POPUPS) {
            // Hits in a row on the same one stack upward.
            int stacked = 0;
            for (Popup p : POPUPS) {
                if (p.entity == entityId && p.age < 12) {
                    stacked++;
                }
            }
            double jitter = ((entityId * 31 + POPUPS.size() * 17) % 7 - 3) * 0.06;
            POPUPS.add(new Popup(entityId, e.getX() + jitter, e.getY() + e.getHeight() + 0.45 + stacked * 0.42, e.getZ(),
                miss ? "MISS" : text(amount), miss ? 0 : tier(roll), miss, roll == 9, scale));
            if (POPUPS.size() > 64) {
                POPUPS.remove(0);
            }
        }
    }

    /** Read the glyph spans once (they come with the sheet). */
    static synchronized Map<String, Map<Character, int[]>> spans() {
        if (spans != null) {
            return spans;
        }
        Map<String, Map<Character, int[]>> out = new HashMap<>();
        try {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(METRICS);
            if (res.isPresent()) {
                try (Reader r = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    JsonArray cell = o.getAsJsonArray("cell");
                    cellW = cell.get(0).getAsInt();
                    cellH = cell.get(1).getAsInt();
                    chars = o.get("chars").getAsString();
                    JsonObject glyphs = o.getAsJsonObject("glyphs");
                    for (String style : glyphs.keySet()) {
                        Map<Character, int[]> m = new HashMap<>();
                        JsonObject g = glyphs.getAsJsonObject(style);
                        for (String ch : g.keySet()) {
                            JsonArray a = g.getAsJsonArray(ch);
                            m.put(ch.charAt(0), new int[] { a.get(0).getAsInt(), a.get(1).getAsInt() });
                        }
                        out.put(style, m);
                    }
                }
            }
        } catch (Exception broken) {
            AiBuildAssistantMod.LOGGER.warn("Damage number glyphs: {}", broken.toString());
        }
        spans = out;
        return out;
    }

    static void render(WorldRenderContext ctx) {
        List<Popup> list;
        synchronized (POPUPS) {
            if (POPUPS.isEmpty()) {
                return;
            }
            list = new ArrayList<>(POPUPS);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buf = consumers.getBuffer(RenderLayer.getText(SHEET));
        MatrixStack m = new MatrixStack(); // camera-relative, as the entities are drawn
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        float delta = client.getRenderTickCounter().getTickDelta(true);
        String style = style();
        int row0 = STYLES.indexOf(style) * 3;
        Map<Character, int[]> span = spans().getOrDefault(style, Map.of());
        int sheetW = cellW * chars.length(), sheetH = cellH * STYLES.size() * 3;
        for (Popup p : list) {
            float age = p.age + delta;
            double rise = age * 0.035;
            float fade = age > LIFE - 8 ? Math.max(0, (LIFE - age) / 8f) : 1f;
            int alpha = Math.max(8, (int) (fade * 255));
            int tint = p.miss ? 0x9A9A9A : 0xFFFFFF;
            int color = (alpha << 24) | tint;
            float pop = age < 3 ? 1f + (3 - age) * 0.18f : 1f;
            // one glyph cell = this many blocks tall
            float unit = 0.5f * p.scale * pop / cellH;
            m.push();
            m.translate(p.x - cam.x, p.y + rise - cam.y, p.z - cam.z);
            m.multiply(camera.getRotation());
            m.scale(unit, -unit, unit);
            Matrix4f mat = m.peek().getPositionMatrix();
            // The inked width of the whole number, glyphs set close (their outlines touch).
            int overlap = 6;
            float total = 0;
            for (int i = 0; i < p.text.length(); i++) {
                int[] s = span.getOrDefault(p.text.charAt(i), new int[] { 8, cellW - 8 });
                total += (s[1] - s[0]) - (i > 0 ? overlap : 0);
            }
            float x = -total / 2f;
            int row = row0 + p.tier;
            for (int i = 0; i < p.text.length(); i++) {
                char ch = p.text.charAt(i);
                int col = chars.indexOf(ch);
                int[] s = span.getOrDefault(ch, new int[] { 8, cellW - 8 });
                if (col >= 0) {
                    quad(buf, mat, x - s[0], -cellH / 2f, col, row, sheetW, sheetH, color);
                }
                x += (s[1] - s[0]) - overlap;
            }
            if (p.sparkle) {
                int col = chars.indexOf('*');
                if (col >= 0) {
                    float twinkle = 0.75f + 0.25f * (float) Math.sin(age * 0.9);
                    m.push();
                    m.translate(x + 2, -cellH * 0.55f, -0.01f);
                    m.scale(0.6f * twinkle, 0.6f * twinkle, 1);
                    quad(buf, m.peek().getPositionMatrix(), -cellW / 2f, -cellH / 2f, col, row, sheetW, sheetH, color);
                    m.pop();
                }
            }
            m.pop();
        }
        consumers.draw();
    }

    private static void quad(VertexConsumer buf, Matrix4f mat, float x, float y, int col, int row, int sheetW, int sheetH, int color) {
        float u0 = col * cellW / (float) sheetW, u1 = (col + 1) * cellW / (float) sheetW;
        float v0 = row * cellH / (float) sheetH, v1 = (row + 1) * cellH / (float) sheetH;
        int light = 0xF000F0;
        buf.vertex(mat, x, y, 0).color(color).texture(u0, v0).light(light);
        buf.vertex(mat, x, y + cellH, 0).color(color).texture(u0, v1).light(light);
        buf.vertex(mat, x + cellW, y + cellH, 0).color(color).texture(u1, v1).light(light);
        buf.vertex(mat, x + cellW, y, 0).color(color).texture(u1, v0).light(light);
    }

    /** Test hook: how many numbers are in the air. */
    public static int countForTests() {
        synchronized (POPUPS) {
            return POPUPS.size();
        }
    }

    /** Test hook: show a number over an entity as if the server had sent it. */
    public static void addForTests(int entityId, float amount, int roll) {
        add(entityId, amount, roll);
    }
}
