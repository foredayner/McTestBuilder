package com.example.client;

import com.example.network.AiNetworkPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Damage numbers over what an AI weapon hits (like MapleStory): each pops in, floats up and
 * fades in about a second; hits in a row stack upward. With random damage the roll 0-9 sits small
 * to the left, and the number grows and warms with it (9: big and red); a roll of 0 is a grey MISS.
 */
public final class DamagePopups {

    private DamagePopups() {
    }

    static final int LIFE = 26;

    /** One number in the air. */
    static final class Popup {
        final int entity;
        final double x, y, z;
        final String text;
        final String roll;
        final int color;
        final float scale;
        int age;

        Popup(int entity, double x, double y, double z, String text, String roll, int color, float scale) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.roll = roll;
            this.color = color;
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

    /** What a hit shows: its text, colour and size from the damage and the roll (-1 = not rolled). */
    static Object[] look(float amount, int roll) {
        if (roll == 0) {
            return new Object[] { "MISS", 0xAAAAAA, 0.9f };
        }
        String text = Math.abs(amount - Math.round(amount)) < 0.05 ? Integer.toString(Math.round(amount))
            : String.format(Locale.ROOT, "%.1f", amount);
        if (roll < 0) {
            return new Object[] { text, 0xFFE066, 1.1f };
        }
        int color = roll == 9 ? 0xFF4030 : roll >= 7 ? 0xFF9A30 : roll >= 4 ? 0xFFE066 : 0xFFFFFF;
        float scale = 0.85f + roll * 0.08f;
        return new Object[] { text, color, scale };
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
        Object[] look = look(amount, roll);
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
                (String) look[0], roll > 0 ? Integer.toString(roll) : "", (Integer) look[1], (Float) look[2]));
            if (POPUPS.size() > 64) {
                POPUPS.remove(0);
            }
        }
    }

    static void render(WorldRenderContext ctx) {
        List<Popup> list;
        synchronized (POPUPS) {
            if (POPUPS.isEmpty()) {
                return;
            }
            list = new ArrayList<>(POPUPS);
        }
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        MatrixStack m = new MatrixStack(); // camera-relative, as the entities are drawn
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        float delta = ctx.tickCounter().getTickDelta(false);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        for (Popup p : list) {
            float age = p.age + delta;
            double rise = age * 0.035;
            float fade = age > LIFE - 8 ? Math.max(0, (LIFE - age) / 8f) : 1f;
            int alpha = Math.max(8, (int) (fade * 255));
            // pops in big and settles
            float pop = age < 3 ? 1f + (3 - age) * 0.18f : 1f;
            float s = 0.028f * p.scale * pop;
            m.push();
            m.translate(p.x - cam.x, p.y + rise - cam.y, p.z - cam.z);
            m.multiply(camera.getRotation());
            m.scale(s, -s, s);
            Matrix4f mat = m.peek().getPositionMatrix();
            OrderedText text = Text.literal(p.text).asOrderedText();
            float w = font.getWidth(text);
            font.drawWithOutline(text, -w / 2f, 0, (alpha << 24) | p.color, (alpha << 24), mat, consumers, 0xF000F0);
            if (!p.roll.isEmpty()) {
                // the roll, small, to the upper left
                m.push();
                m.translate(-w / 2f - 2, -1, 0);
                m.scale(0.6f, 0.6f, 0.6f);
                OrderedText r = Text.literal(p.roll).asOrderedText();
                font.drawWithOutline(r, -font.getWidth(r), 0, (alpha << 24) | p.color, (alpha << 24), m.peek().getPositionMatrix(),
                    consumers, 0xF000F0);
                m.pop();
            }
            m.pop();
        }
        consumers.draw();
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
