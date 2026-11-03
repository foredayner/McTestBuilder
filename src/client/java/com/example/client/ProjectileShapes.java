package com.example.client;

import com.example.AiBuildAssistantMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Projectile shapes drawn by the mod (ai.shot model): a crescent sword wave, a shuriken, a disc,
 * a ring, a star, a glowing orb, an arrowhead, a feather. Smooth, thin and glowing, made in the
 * projectile's own frame: y = where it flies, x = side to side, z = up (flat shapes lie in x-y,
 * so they fly flat unless tilted). The shape is about {@code size} blocks across, centred.
 */
public final class ProjectileShapes {

    private static final Identifier WHITE = Identifier.of(AiBuildAssistantMod.MOD_ID, "textures/misc/white.png");

    private ProjectileShapes() {
    }

    public static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, String model, float size, int color, int color2,
                            float thickness) {
        VertexConsumer b = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        MatrixStack.Entry e = matrices.peek();
        int edge = 0xFF000000 | (color & 0xFFFFFF);
        // The middle is lighter (a glowing core); the halo takes the second colour if there is one.
        int core = 0xFF000000 | mix(color, 0xFFFFFF, 0.45F);
        int halo = 0x55000000 | ((color2 >= 0 ? color2 : color) & 0xFFFFFF);
        float t = Math.max(0.01F, thickness * size) / 2;
        switch (model) {
            case "crescent" -> {
                float[][][] arcs = crescent(size);
                strip(b, e, arcs[0], arcs[1], t, edge, core);
                float[][][] glow = crescent(size * 1.18F);
                strip(b, e, glow[0], glow[1], 0, halo, halo);
            }
            case "ring" -> {
                float[][] outer = circle(size * 0.5F, 32), inner = circle(size * 0.32F, 32);
                strip(b, e, close(outer), close(inner), t, edge, core);
                strip(b, e, close(circle(size * 0.58F, 32)), close(circle(size * 0.26F, 32)), 0, halo, halo);
            }
            case "orb" -> {
                // Three crossing discs and a halo: a glowing ball from every side.
                float[][] c = circle(size * 0.5F, 24);
                fan(b, e, c, 0, edge, core, 0);
                fan(b, e, c, 0, edge, core, 1);
                fan(b, e, c, 0, edge, core, 2);
                fan(b, e, circle(size * 0.68F, 24), 0, halo, halo, 0);
            }
            default -> {
                float[][] outline = switch (model) {
                    case "shuriken" -> star(4, size * 0.5F, size * 0.14F);
                    case "star" -> star(5, size * 0.5F, size * 0.21F);
                    case "arrowhead" -> new float[][] { { 0, 0.5F * size }, { -0.3F * size, -0.3F * size }, { 0, -0.12F * size },
                        { 0.3F * size, -0.3F * size } };
                    case "feather" -> feather(size);
                    default -> circle(size * 0.5F, 28); // disc
                };
                fan(b, e, outline, t, edge, core, 0);
                rim(b, e, outline, t, edge);
                float[][] bigger = new float[outline.length][];
                for (int i = 0; i < outline.length; i++) {
                    bigger[i] = new float[] { outline[i][0] * 1.2F, outline[i][1] * 1.2F };
                }
                fan(b, e, bigger, 0, halo, halo, 0);
            }
        }
    }

    // ---------------------------------------------------------------- outlines (x, y)

    static float[][] circle(float r, int n) {
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            out[i] = new float[] { (float) (r * Math.sin(a)), (float) (r * Math.cos(a)) };
        }
        return out;
    }

    static float[][] close(float[][] ring) {
        float[][] out = new float[ring.length + 1][];
        System.arraycopy(ring, 0, out, 0, ring.length);
        out[ring.length] = ring[0];
        return out;
    }

    /** A star with n points: outer and inner radius, the first point ahead (+y). */
    static float[][] star(int n, float outer, float inner) {
        float[][] out = new float[n * 2][];
        for (int i = 0; i < n * 2; i++) {
            double a = Math.PI * i / n;
            float r = i % 2 == 0 ? outer : inner;
            out[i] = new float[] { (float) (r * Math.sin(a)), (float) (r * Math.cos(a)) };
        }
        return out;
    }

    static float[][] feather(float size) {
        int n = 12;
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            float y = -0.5F + (float) i / n;
            pts.add(new float[] { (float) (0.17 * Math.sin(Math.PI * (y + 0.5))) * size, y * size });
        }
        for (int i = n - 1; i > 0; i--) {
            float y = -0.5F + (float) i / n;
            pts.add(new float[] { (float) (-0.17 * Math.sin(Math.PI * (y + 0.5))) * size, y * size });
        }
        return pts.toArray(new float[0][]);
    }

    /**
     * A crescent: the round front edge ahead (+y), the hollow behind, thin at the tips. Returns the
     * outer and inner edges, point by point.
     */
    static float[][][] crescent(float size) {
        int n = 20;
        float r = size * 0.52F;
        double max = Math.toRadians(78);
        float[][] outer = new float[n + 1][], inner = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = -max + 2 * max * i / n;
            double thin = Math.cos(Math.PI / 2 * a / max); // 1 in the middle, 0 at the tips
            double ri = r * (1 - 0.38 * thin);
            float shift = -r * 0.62F;
            outer[i] = new float[] { (float) (r * Math.sin(a)), (float) (r * Math.cos(a)) + shift };
            inner[i] = new float[] { (float) (ri * Math.sin(a)), (float) (ri * Math.cos(a) - r * 0.12 * thin) + shift };
        }
        return new float[][][] { outer, inner };
    }

    // ---------------------------------------------------------------- drawing

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** A point of a flat shape in the plane given by axis: 0 = x-y (flat), 1 = y-z, 2 = x-z. */
    private static float[] at(float u, float v, float w, int plane) {
        return switch (plane) {
            case 1 -> new float[] { w, u, v };
            case 2 -> new float[] { u, w, v };
            default -> new float[] { u, v, w };
        };
    }

    private static void vert(VertexConsumer b, MatrixStack.Entry e, float[] p, int argb) {
        b.vertex(e, p[0], p[1], p[2]).color(argb).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
            .normal(e, 0, 0, 1);
    }

    /** A quad seen from both sides. */
    private static void quad(VertexConsumer b, MatrixStack.Entry e, float[] p0, float[] p1, float[] p2, float[] p3, int c0, int c1, int c2,
                             int c3) {
        vert(b, e, p0, c0);
        vert(b, e, p1, c1);
        vert(b, e, p2, c2);
        vert(b, e, p3, c3);
        vert(b, e, p3, c3);
        vert(b, e, p2, c2);
        vert(b, e, p1, c1);
        vert(b, e, p0, c0);
    }

    /** A filled outline (seen from its middle), on both faces t above and below its plane. */
    private static void fan(VertexConsumer b, MatrixStack.Entry e, float[][] outline, float t, int edge, int core, int plane) {
        for (float face : t == 0 ? new float[] { 0 } : new float[] { t, -t }) {
            float[] c = at(0, 0, face, plane);
            for (int i = 0; i < outline.length; i++) {
                float[] p = outline[i], q = outline[(i + 1) % outline.length];
                float[] a = at(p[0], p[1], face, plane), d = at(q[0], q[1], face, plane);
                quad(b, e, c, a, d, c, core, edge, edge, core);
            }
        }
    }

    /** The edge all round a filled outline, giving it its thickness. */
    private static void rim(VertexConsumer b, MatrixStack.Entry e, float[][] outline, float t, int color) {
        for (int i = 0; i < outline.length; i++) {
            float[] p = outline[i], q = outline[(i + 1) % outline.length];
            quad(b, e, at(p[0], p[1], t, 0), at(q[0], q[1], t, 0), at(q[0], q[1], -t, 0), at(p[0], p[1], -t, 0), color, color, color, color);
        }
    }

    /** The band between two edges (a crescent or a ring), on both faces, with its edges for thickness. */
    private static void strip(VertexConsumer b, MatrixStack.Entry e, float[][] outer, float[][] inner, float t, int edge, int core) {
        for (float face : t == 0 ? new float[] { 0 } : new float[] { t, -t }) {
            for (int i = 0; i + 1 < outer.length; i++) {
                quad(b, e, at(outer[i][0], outer[i][1], face, 0), at(outer[i + 1][0], outer[i + 1][1], face, 0),
                    at(inner[i + 1][0], inner[i + 1][1], face, 0), at(inner[i][0], inner[i][1], face, 0), edge, edge, core, core);
            }
        }
        if (t > 0) {
            for (float[][] side : new float[][][] { outer, inner }) {
                for (int i = 0; i + 1 < side.length; i++) {
                    quad(b, e, at(side[i][0], side[i][1], t, 0), at(side[i + 1][0], side[i + 1][1], t, 0),
                        at(side[i + 1][0], side[i + 1][1], -t, 0), at(side[i][0], side[i][1], -t, 0), edge, edge, edge, edge);
                }
            }
        }
    }
}
