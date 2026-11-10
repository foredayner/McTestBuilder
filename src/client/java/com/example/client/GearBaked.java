package com.example.client;

import com.example.ai.build.BuildPlan;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * An equipment model's still pieces worked out once (like a baked block model): every face's
 * corners, normal, texture and colour in the model's own frame, so a frame only hands them on.
 * Faces hidden inside or against another solid, unturned piece are left out. Moving pieces
 * (animated, colour waves) and smooth round pieces are still drawn one by one each frame.
 */
final class GearBaked {

    /** Corner indices of each face (as GearModelRenderer.FACES): bit 0 = +x, 1 = +y, 2 = +z; then the normal. */
    private static final int[][] FACES = {
        { 4, 5, 7, 6, 0, 0, 1 },
        { 1, 0, 2, 3, 0, 0, -1 },
        { 5, 1, 3, 7, 1, 0, 0 },
        { 0, 4, 6, 2, -1, 0, 0 },
        { 6, 7, 3, 2, 0, 1, 0 },
        { 0, 1, 5, 4, 0, -1, 0 },
    };

    /** Per part: drawn each frame (moving, colour wave or round) instead of from the baked faces. */
    final boolean[] moving;
    /** Per part: a small detail (under 0.1 across), left out far away. */
    final boolean[] small;
    /** Baked faces, solid then see-through: 4 vertices each of x, y, z, nx, ny, nz, u, v; and the colour and part per face. */
    private final float[] solid, clear;
    private final int[] solidColor, clearColor, solidPart, clearPart;

    private GearBaked(boolean[] moving, boolean[] small, float[] solid, int[] solidColor, int[] solidPart, float[] clear,
                      int[] clearColor, int[] clearPart) {
        this.moving = moving;
        this.small = small;
        this.solid = solid;
        this.solidColor = solidColor;
        this.solidPart = solidPart;
        this.clear = clear;
        this.clearColor = clearColor;
        this.clearPart = clearPart;
    }

    static GearBaked of(GearModelRenderer.Model model, GearModelRenderer.Waves waves) {
        List<BuildPlan.Part> parts = model.parts();
        int n = parts.size();
        boolean[] moving = new boolean[n];
        boolean[] small = new boolean[n];
        for (int i = 0; i < n; i++) {
            BuildPlan.Part p = parts.get(i);
            moving[i] = p.shape != null || p.group != 0 && model.anims().containsKey(p.group)
                || waves != null && waves.of(p.group) != null;
            small[i] = Math.max(p.sx, Math.max(p.sy, p.sz)) < 0.1;
        }
        Faces s = new Faces(), c = new Faces();
        for (int i = 0; i < n; i++) {
            if (moving[i]) {
                continue;
            }
            BuildPlan.Part p = parts.get(i);
            Faces out = model.clear()[i] ? c : s;
            Sprite sprite = model.sprites().get(i);
            int color = model.colors()[i];
            boolean square = p.yaw == 0 && p.pitch == 0 && p.roll == 0;
            Matrix4f m = new Matrix4f().translate((float) p.x, (float) p.y, (float) p.z)
                .rotate(new Quaternionf().rotateY((float) Math.toRadians(p.yaw)).rotateX((float) Math.toRadians(p.pitch))
                    .rotateZ((float) Math.toRadians(p.roll)));
            float hx = (float) p.sx / 2, hy = (float) p.sy / 2, hz = (float) p.sz / 2;
            Vector3f[] corner = new Vector3f[8];
            for (int k = 0; k < 8; k++) {
                corner[k] = m.transformPosition(new Vector3f((k & 1) == 0 ? -hx : hx, (k & 2) == 0 ? -hy : hy, (k & 4) == 0 ? -hz : hz));
            }
            for (int f = 0; f < FACES.length; f++) {
                int[] face = FACES[f];
                if (square && !model.clear()[i] && hidden(parts, model, moving, i, face)) {
                    continue;
                }
                Vector3f normal = m.transformDirection(new Vector3f(face[4], face[5], face[6])).normalize();
                float w, h;
                if (face[6] != 0) { w = (float) p.sx; h = (float) p.sy; }
                else if (face[4] != 0) { w = (float) p.sz; h = (float) p.sy; }
                else { w = (float) p.sx; h = (float) p.sz; }
                float u0 = sprite.getMinU(), v0 = sprite.getMinV();
                float u1 = u0 + (sprite.getMaxU() - u0) * Math.min(1, w);
                float v1 = v0 + (sprite.getMaxV() - v0) * Math.min(1, h);
                float[][] uv = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
                for (int k = 0; k < 4; k++) {
                    Vector3f at = corner[face[k]];
                    out.add(at.x, at.y, at.z, normal.x, normal.y, normal.z, uv[k][0], uv[k][1]);
                }
                out.face(color, i);
            }
        }
        return new GearBaked(moving, small, s.vertices(), s.colors(), s.parts(), c.vertices(), c.colors(), c.parts());
    }

    /** Whether a face of unturned part i lies against or inside another solid, unturned, still piece that covers all of it. */
    private static boolean hidden(List<BuildPlan.Part> parts, GearModelRenderer.Model model, boolean[] moving, int i, int[] face) {
        BuildPlan.Part a = parts.get(i);
        double eps = 1e-4;
        double[] amin = { a.x - a.sx / 2, a.y - a.sy / 2, a.z - a.sz / 2 };
        double[] amax = { a.x + a.sx / 2, a.y + a.sy / 2, a.z + a.sz / 2 };
        int axis = face[4] != 0 ? 0 : face[5] != 0 ? 1 : 2;
        int dir = face[4] + face[5] + face[6];
        double plane = dir > 0 ? amax[axis] : amin[axis];
        for (int j = 0; j < parts.size(); j++) {
            BuildPlan.Part b = parts.get(j);
            if (j == i || moving[j] || model.clear()[j] || b.yaw != 0 || b.pitch != 0 || b.roll != 0 || !opaque(b.block)) {
                continue;
            }
            double[] bmin = { b.x - b.sx / 2, b.y - b.sy / 2, b.z - b.sz / 2 };
            double[] bmax = { b.x + b.sx / 2, b.y + b.sy / 2, b.z + b.sz / 2 };
            // b reaches past the face outward and starts at or before it
            boolean across = dir > 0 ? bmin[axis] <= plane + eps && bmax[axis] > plane + eps
                : bmax[axis] >= plane - eps && bmin[axis] < plane - eps;
            if (!across) {
                continue;
            }
            boolean covers = true;
            for (int k = 0; k < 3 && covers; k++) {
                if (k != axis && (bmin[k] > amin[k] + eps || bmax[k] < amax[k] - eps)) {
                    covers = false;
                }
            }
            if (covers) {
                return true;
            }
        }
        return false;
    }

    /** A block that hides what is behind it (not glass, leaves, bars and other see-through or cut-out ones). */
    private static boolean opaque(String block) {
        if (block == null) {
            return false;
        }
        if (block.startsWith("#") || block.contains("paint")) {
            return !block.contains("/");
        }
        String b = block.toLowerCase(java.util.Locale.ROOT);
        return !(b.contains("glass") || b.contains("leaves") || b.contains("ice") || b.contains("bars") || b.contains("fence")
            || b.contains("door") || b.contains("slime") || b.contains("honey") || b.contains("chain") || b.contains("lantern")
            || b.contains("torch") || b.contains("web") || b.contains("vine") || b.contains("ladder") || b.contains("flower")
            || b.contains("sapling") || b.contains("grass") || b.contains("coral") || b.contains("scaffold") || b.contains("candle"));
    }

    /** Hand the baked faces on (the light comes from now). */
    void draw(MatrixStack.Entry e, VertexConsumer buffer, int light, int overlay, boolean seeThrough, boolean far) {
        float[] v = seeThrough ? clear : solid;
        int[] colors = seeThrough ? clearColor : solidColor;
        int[] part = seeThrough ? clearPart : solidPart;
        Matrix4f pos = e.getPositionMatrix();
        for (int f = 0; f < colors.length; f++) {
            if (far && small[part[f]]) {
                continue;
            }
            int color = colors[f];
            for (int k = 0; k < 4; k++) {
                int o = (f * 4 + k) * 8;
                buffer.vertex(pos, v[o], v[o + 1], v[o + 2]).color(color).texture(v[o + 6], v[o + 7]).overlay(overlay).light(light)
                    .normal(e, v[o + 3], v[o + 4], v[o + 5]);
            }
        }
    }

    /** How many faces were baked (solid and see-through): for tests. */
    int faces() {
        return solidColor.length + clearColor.length;
    }

    /** A growing list of faces. */
    private static final class Faces {
        private float[] v = new float[8 * 4 * 64];
        private int[] colors = new int[64], parts = new int[64];
        private int nv, nf;

        void add(float x, float y, float z, float nx, float ny, float nz, float u, float w) {
            if (nv + 8 > v.length) {
                v = java.util.Arrays.copyOf(v, v.length * 2);
            }
            v[nv++] = x;
            v[nv++] = y;
            v[nv++] = z;
            v[nv++] = nx;
            v[nv++] = ny;
            v[nv++] = nz;
            v[nv++] = u;
            v[nv++] = w;
        }

        void face(int color, int part) {
            if (nf == colors.length) {
                colors = java.util.Arrays.copyOf(colors, nf * 2);
                parts = java.util.Arrays.copyOf(parts, nf * 2);
            }
            colors[nf] = color;
            parts[nf++] = part;
        }

        float[] vertices() {
            return java.util.Arrays.copyOf(v, nv);
        }

        int[] colors() {
            return java.util.Arrays.copyOf(colors, nf);
        }

        int[] parts() {
            return java.util.Arrays.copyOf(parts, nf);
        }
    }
}
