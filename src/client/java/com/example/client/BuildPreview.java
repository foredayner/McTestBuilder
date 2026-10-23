package com.example.client;

import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Pixel previews of a recorded build, coloured like a Minecraft map (each block's map colour):
 * a top view (front of the build at the top) and a front view (as seen from where the player
 * stood). Computed from the plan, so they can be redrawn any time.
 */
public final class BuildPreview {

    private static final int MAX_SIDE = 128;
    private static final long MAX_FILL = 2_000_000L;
    private static final int GLASS = 0xFFBFD9E8;
    private static final Map<String, BuildPreview> CACHE = new HashMap<>();

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final int blocks;
    public final int topW;
    public final int topH;
    public final int frontW;
    public final int frontH;
    public final int[] top;    // ARGB, 0 = empty
    public final int[] front;

    private BuildPreview(int sx, int sy, int sz, int blocks, int tw, int th, int[] top, int fw, int fh, int[] front) {
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.blocks = blocks;
        this.topW = tw;
        this.topH = th;
        this.top = top;
        this.frontW = fw;
        this.frontH = fh;
        this.front = front;
    }

    public static synchronized BuildPreview of(BuildRecord record) {
        return CACHE.computeIfAbsent(record.id, id -> compute(record.plan));
    }

    public static synchronized void forget(BuildRecord record) {
        CACHE.remove(record.id);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static int kx(long k) { return (int) (k << 1 >> 43); }
    private static int ky(long k) { return (int) (k << 22 >> 43); }
    private static int kz(long k) { return (int) (k << 43 >> 43); }

    static BuildPreview compute(BuildPlan plan) {
        if (plan.isObject()) {
            return computeObject(plan);
        }
        // Turn the build so the player faced north: forward = -Z, right = +X.
        int turns = BuildPlan.turnsBetween(plan.facing, "north");
        Map<Long, Integer> voxels = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();

        for (BuildPlan.Op op : plan.ops) {
            int[] a = BuildPlan.rotate(op.x1, op.z1, turns);
            int[] b = BuildPlan.rotate(op.x2, op.z2, turns);
            int x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]);
            int y0 = Math.min(op.y1, op.y2), y1 = Math.max(op.y1, op.y2);
            int z0 = Math.min(a[1], b[1]), z1 = Math.max(a[1], b[1]);
            boolean air = op.isAir();
            int color = air ? 0 : colors.computeIfAbsent(op.block, BuildPreview::colorOf);
            String mode = "set".equals(op.kind) ? "replace" : op.mode;
            long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);

            if (air && volume > MAX_FILL) {
                final int fx0 = x0, fx1 = x1, fy0 = y0, fy1 = y1, fz0 = z0, fz1 = z1;
                voxels.keySet().removeIf(k -> kx(k) >= fx0 && kx(k) <= fx1 && ky(k) >= fy0 && ky(k) <= fy1 && kz(k) >= fz0 && kz(k) <= fz1);
                continue;
            }
            boolean shellOnly = "outline".equals(mode) || "hollow".equals(mode) || volume > MAX_FILL;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                        long k = key(x, y, z);
                        if (!shell && shellOnly) {
                            if ("hollow".equals(mode)) {
                                voxels.remove(k);
                            }
                            continue;
                        }
                        if ("keep".equals(mode) && voxels.containsKey(k)) {
                            continue;
                        }
                        if (air) {
                            voxels.remove(k);
                        } else {
                            voxels.put(k, color);
                        }
                    }
                }
            }
        }

        return fromVoxels(voxels, 1, voxels.size());
    }

    /**
     * Objects: rasterise each rotated part into a fine grid (r cells per block) so pieces smaller
     * than a block still show.
     */
    static BuildPreview computeObject(BuildPlan plan) {
        int turns = BuildPlan.turnsBetween(plan.facing, "north");
        Map<String, Integer> colors = new HashMap<>();
        double ext = 0.5;
        for (BuildPlan.Part p : plan.parts) {
            double half = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
            ext = Math.max(ext, Math.max(Math.abs(p.x), Math.max(Math.abs(p.y), Math.abs(p.z))) + half);
        }
        Map<Long, Integer> voxels = new HashMap<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (BuildPlan.Part p : plan.parts) {
            double half = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
            minX = Math.min(minX, p.x - half); maxX = Math.max(maxX, p.x + half);
            minY = Math.min(minY, p.y - half); maxY = Math.max(maxY, p.y + half);
            minZ = Math.min(minZ, p.z - half); maxZ = Math.max(maxZ, p.z + half);
        }
        double extent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        int r = (int) Math.max(2, Math.min(16, Math.floor(96 / Math.max(0.1, extent))));

        for (BuildPlan.Part p : plan.parts) {
            double cx = p.x, cz = p.z;
            for (int i = 0; i < turns; i++) {
                double t = cx;
                cx = -cz;
                cz = t;
            }
            Quaternionf q = new Quaternionf()
                .rotateY((float) Math.toRadians(-90.0 * turns))
                .rotateY((float) Math.toRadians(p.yaw))
                .rotateX((float) Math.toRadians(p.pitch))
                .rotateZ((float) Math.toRadians(p.roll));
            Quaternionf inverse = new Quaternionf(q).conjugate();
            double hx = p.sx / 2, hy = p.sy / 2, hz = p.sz / 2;
            double reach = Math.sqrt(hx * hx + hy * hy + hz * hz);
            int color = colors.computeIfAbsent(p.block, BuildPreview::colorOf);
            Vector3f local = new Vector3f();
            for (int ix = (int) Math.floor((cx - reach) * r); ix <= (int) Math.ceil((cx + reach) * r); ix++) {
                for (int iy = (int) Math.floor((p.y - reach) * r); iy <= (int) Math.ceil((p.y + reach) * r); iy++) {
                    for (int iz = (int) Math.floor((cz - reach) * r); iz <= (int) Math.ceil((cz + reach) * r); iz++) {
                        local.set((float) ((ix + 0.5) / r - cx), (float) ((iy + 0.5) / r - p.y), (float) ((iz + 0.5) / r - cz));
                        inverse.transform(local);
                        if (Math.abs(local.x) <= hx && Math.abs(local.y) <= hy && Math.abs(local.z) <= hz) {
                            voxels.put(key(ix, iy, iz), color);
                        }
                    }
                }
            }
        }
        BuildPreview preview = fromVoxels(voxels, r, plan.parts.size());
        return preview;
    }

    /** Top and front projections of a voxel map with r cells per block. */
    private static BuildPreview fromVoxels(Map<Long, Integer> voxels, int r, int count) {
        if (voxels.isEmpty()) {
            return new BuildPreview(0, 0, 0, 0, 1, 1, new int[1], 1, 1, new int[1]);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : voxels.keySet()) {
            minX = Math.min(minX, kx(k)); maxX = Math.max(maxX, kx(k));
            minY = Math.min(minY, ky(k)); maxY = Math.max(maxY, ky(k));
            minZ = Math.min(minZ, kz(k)); maxZ = Math.max(maxZ, kz(k));
        }
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;

        // Top view: highest block per column, darker the lower it is. Row 0 = farthest (north).
        int[] topY = new int[sx * sz];
        int[] top = new int[sx * sz];
        java.util.Arrays.fill(topY, Integer.MIN_VALUE);
        // Front view: nearest block per (x, y) seen from the south. Row 0 = top.
        int[] frontZ = new int[sx * sy];
        int[] front = new int[sx * sy];
        java.util.Arrays.fill(frontZ, Integer.MIN_VALUE);
        for (Map.Entry<Long, Integer> e : voxels.entrySet()) {
            int x = kx(e.getKey()) - minX, y = ky(e.getKey()) - minY, z = kz(e.getKey()) - minZ;
            int ti = z * sx + x;
            if (y > topY[ti]) {
                topY[ti] = y;
                top[ti] = shade(e.getValue(), sy <= 1 ? 1.0 : 0.55 + 0.45 * y / (sy - 1));
            }
            int fi = (sy - 1 - y) * sx + x;
            if (z > frontZ[fi]) {
                frontZ[fi] = z;
                front[fi] = shade(e.getValue(), sz <= 1 ? 1.0 : 0.6 + 0.4 * z / (sz - 1));
            }
        }

        int[] t = shrink(top, sx, sz);
        int[] f = shrink(front, sx, sy);
        return new BuildPreview((sx + r - 1) / r, (sy + r - 1) / r, (sz + r - 1) / r, count,
            Math.min(sx, MAX_SIDE), Math.min(sz, MAX_SIDE), t, Math.min(sx, MAX_SIDE), Math.min(sy, MAX_SIDE), f);
    }

    /** Nearest-neighbour downscale so no side is over MAX_SIDE pixels. */
    private static int[] shrink(int[] src, int w, int h) {
        if (w <= MAX_SIDE && h <= MAX_SIDE) {
            return src;
        }
        int nw = Math.min(w, MAX_SIDE), nh = Math.min(h, MAX_SIDE);
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                out[y * nw + x] = src[(y * h / nh) * w + (x * w / nw)];
            }
        }
        return out;
    }

    private static int shade(int argb, double f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int colorOf(String block) {
        try {
            String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
            if (id.contains("glass")) {
                return GLASS;
            }
            Block b = Registries.BLOCK.get(Identifier.of(id));
            int rgb = b.getDefaultMapColor().color;
            return rgb == 0 ? 0xFF9A9A9A : 0xFF000000 | rgb;
        } catch (RuntimeException exception) {
            return 0xFF9A9A9A;
        }
    }

    /** Draw an ARGB image fitted (pixel-art) into a box; same-colour runs become one rectangle. */
    public static void draw(DrawContext ctx, int[] img, int w, int h, int x, int y, int boxW, int boxH) {
        if (w <= 0 || h <= 0) {
            return;
        }
        double scale = Math.min((double) boxW / w, (double) boxH / h);
        int dw = (int) Math.floor(w * scale), dh = (int) Math.floor(h * scale);
        int ox = x + (boxW - dw) / 2, oy = y + (boxH - dh) / 2;
        for (int row = 0; row < h; row++) {
            int ty = oy + (int) Math.floor(row * scale), by = oy + (int) Math.floor((row + 1) * scale);
            if (by <= ty) {
                by = ty + 1;
            }
            int col = 0;
            while (col < w) {
                int c = img[row * w + col];
                int end = col + 1;
                while (end < w && img[row * w + end] == c) {
                    end++;
                }
                if (c != 0) {
                    int lx = ox + (int) Math.floor(col * scale), rx = ox + (int) Math.floor(end * scale);
                    ctx.fill(lx, ty, Math.max(rx, lx + 1), by, c);
                }
                col = end;
            }
        }
    }

    /** Item icon size in pixels. */
    public static final int ICON = 16;

    /** The front view fitted into a 16 x 16 icon, standing on the bottom edge (ARGB, 0 = clear). */
    public int[] icon() {
        int[] out = new int[ICON * ICON];
        int w = Math.max(1, frontW), h = Math.max(1, frontH);
        double s = Math.min((double) ICON / w, (double) ICON / h);
        int nw = Math.max(1, Math.min(ICON, (int) Math.round(w * s)));
        int nh = Math.max(1, Math.min(ICON, (int) Math.round(h * s)));
        int ox = (ICON - nw) / 2, oy = ICON - nh;
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int sy = Math.min(h - 1, (int) (y / s)), sx = Math.min(w - 1, (int) (x / s));
                out[(oy + y) * ICON + ox + x] = front[sy * w + sx];
            }
        }
        return out;
    }

    /** Save both views as PNG files, scaled up so they are easy to look at. */
    public void writePngs(Path topFile, Path frontFile) throws IOException {
        writePng(topFile, top, topW, topH);
        writePng(frontFile, front, frontW, frontH);
    }

    private static void writePng(Path file, int[] img, int w, int h) throws IOException {
        int s = Math.max(1, 256 / Math.max(w, h));
        BufferedImage out = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * s; y++) {
            for (int x = 0; x < w * s; x++) {
                out.setRGB(x, y, img[(y / s) * w + (x / s)]);
            }
        }
        ImageIO.write(out, "png", file.toFile());
    }
}
