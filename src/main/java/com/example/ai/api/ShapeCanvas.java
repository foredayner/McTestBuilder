package com.example.ai.api;

import com.example.ai.build.BuildPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Smooth shapes for objects (spheres, cones, capsules, curved tubes, rounded boxes...).
 *
 * <p>Shapes are kept as maths while the script runs. When recording ends they are painted, in
 * order, into one voxel grid (a later shape paints over an earlier one where they overlap), and
 * each block's voxels are covered with as few boxes as possible: those boxes become the
 * object's display parts. The grid is 1/16 block (one texture pixel) when the part budget
 * allows; big objects automatically use a coarser grid.
 */
public final class ShapeCanvas {

    /** Grid sizes tried, finest first (cells per block). */
    private static final int[] RESOLUTIONS = { 16, 12, 10, 8, 6, 5, 4, 3, 2 };
    /** Equipment: drawn by the client, so a finer grid (1/32 block) is tried first. */
    private static final int[] FINE_RESOLUTIONS = { 32, 24, 16, 12, 10, 8, 6, 5, 4, 3, 2 };
    /** Most voxels one painting may test, so huge objects cannot stall the server. */
    private static final long MAX_CELLS = 3_000_000L;

    /** Inside test and bounding box of one shape. */
    interface Shape {
        boolean inside(double x, double y, double z);

        /** {minX, minY, minZ, maxX, maxY, maxZ} */
        double[] bounds();
    }

    private record Painted(String block, Shape shape, double mirrorX, boolean mirrored, int group) {
        boolean inside(double x, double y, double z) {
            return shape.inside(mirrored ? 2 * mirrorX - x : x, y, z);
        }

        double[] bounds() {
            double[] b = shape.bounds();
            if (!mirrored) {
                return b;
            }
            return new double[] { 2 * mirrorX - b[3], b[1], b[2], 2 * mirrorX - b[0], b[4], b[5] };
        }

        /** A point that is certainly in the shape (its centre), so even a tiny shape shows. */
        double[] centre() {
            double[] b = bounds();
            return new double[] { (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2 };
        }
    }

    private final List<Painted> shapes = new ArrayList<>();
    private final int[] resolutions;

    public ShapeCanvas() {
        this(false);
    }

    public ShapeCanvas(boolean fine) {
        resolutions = fine ? FINE_RESOLUTIONS : RESOLUTIONS;
    }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }

    public void add(String block, Shape shape, Double mirrorX) {
        add(block, shape, mirrorX, 0, 0);
    }

    /**
     * group: animation group of the shape (0 = still); mirrorGroup: the group its mirrored copy
     * goes into (a mirrored wing flaps on its own pivot).
     */
    public void add(String block, Shape shape, Double mirrorX, int group, int mirrorGroup) {
        shapes.add(new Painted(block, shape, 0, false, group));
        if (mirrorX != null) {
            double[] b = shape.bounds();
            // A shape centred on the mirror line is its own mirror image.
            if (Math.abs((b[0] + b[3]) / 2 - mirrorX) > 1e-6) {
                shapes.add(new Painted(block, shape, mirrorX, true, mirrorGroup));
            }
        }
    }

    /**
     * Turn the shapes into parts, using the finest grid whose box count fits {@code budget}.
     * If even the coarsest grid is over, its result is returned (the plan check reports it).
     */
    public List<BuildPlan.Part> toParts(int budget) {
        if (shapes.isEmpty()) {
            return List.of();
        }
        double[] all = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (Painted p : shapes) {
            double[] b = p.bounds();
            for (int i = 0; i < 3; i++) {
                all[i] = Math.min(all[i], b[i]);
                all[i + 3] = Math.max(all[i + 3], b[i + 3]);
            }
        }
        List<BuildPlan.Part> best = null;
        for (int res : resolutions) {
            double cells = (all[3] - all[0]) * res * (all[4] - all[1]) * res * (all[5] - all[2]) * res;
            if (cells > MAX_CELLS) {
                continue;
            }
            List<BuildPlan.Part> parts = paint(res);
            best = parts;
            if (parts.size() <= budget) {
                return parts;
            }
        }
        return best == null ? paint(resolutions[resolutions.length - 1]) : best;
    }

    /**
     * Paint every shape into a grid of {@code res} cells per block and cover each block's cells
     * with boxes. Each animation group is painted on its own, since its parts move separately.
     */
    List<BuildPlan.Part> paint(int res) {
        List<Integer> groups = new ArrayList<>();
        for (Painted p : shapes) {
            if (!groups.contains(p.group())) {
                groups.add(p.group());
            }
        }
        List<BuildPlan.Part> all = new ArrayList<>();
        for (int group : groups) {
            for (BuildPlan.Part part : paint(res, group)) {
                part.group = group;
                all.add(part);
            }
        }
        return all;
    }

    private List<BuildPlan.Part> paint(int res, int group) {
        Map<Long, Integer> cells = new HashMap<>();
        List<String> blocks = new ArrayList<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Painted p : shapes) {
            if (p.group() != group) {
                continue;
            }
            int id = index.computeIfAbsent(p.block(), k -> {
                blocks.add(k);
                return blocks.size() - 1;
            });
            double[] b = p.bounds();
            int x0 = (int) Math.floor(b[0] * res), x1 = (int) Math.ceil(b[3] * res);
            int y0 = (int) Math.floor(b[1] * res), y1 = (int) Math.ceil(b[4] * res);
            int z0 = (int) Math.floor(b[2] * res), z1 = (int) Math.ceil(b[5] * res);
            boolean any = false;
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    for (int z = z0; z < z1; z++) {
                        if (covers(p, x, y, z, res)) {
                            cells.put(key(x, y, z), id);
                            any = true;
                        }
                    }
                }
            }
            if (!any) {
                double[] c = p.centre();
                cells.put(key((int) Math.floor(c[0] * res), (int) Math.floor(c[1] * res), (int) Math.floor(c[2] * res)), id);
            }
        }

        List<BuildPlan.Part> parts = new ArrayList<>();
        for (int id = 0; id < blocks.size(); id++) {
            List<int[]> mine = new ArrayList<>();
            for (Map.Entry<Long, Integer> e : cells.entrySet()) {
                if (e.getValue() == id) {
                    long k = e.getKey();
                    mine.add(new int[] { kx(k), ky(k), kz(k) });
                }
            }
            for (int[] box : cover(mine)) {
                double w = (box[3] - box[0] + 1.0) / res, h = (box[4] - box[1] + 1.0) / res, l = (box[5] - box[2] + 1.0) / res;
                parts.add(new BuildPlan.Part(blocks.get(id),
                    box[0] / (double) res + w / 2, box[1] / (double) res + h / 2, box[2] / (double) res + l / 2,
                    w, h, l, 0, 0, 0));
            }
        }
        return parts;
    }

    /**
     * A cell belongs to a shape when its centre is inside, or (for shapes thinner than a cell,
     * like a thin stem) one of eight points around the centre is: so thin parts stay connected
     * instead of breaking into floating bits.
     */
    private static boolean covers(Painted p, int x, int y, int z, int res) {
        double cx = (x + 0.5) / res, cy = (y + 0.5) / res, cz = (z + 0.5) / res;
        if (p.inside(cx, cy, cz)) {
            return true;
        }
        double o = 0.3 / res;
        for (int i = 0; i < 8; i++) {
            if (p.inside(cx + ((i & 1) == 0 ? -o : o), cy + ((i & 2) == 0 ? -o : o), cz + ((i & 4) == 0 ? -o : o))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cover a set of cells with boxes {x0, y0, z0, x1, y1, z1} (inclusive). Each box is grown as
     * far as the cells allow, also through cells another box already covers (same block, so the
     * overlap cannot be seen), which needs far fewer boxes than cutting the set into pieces.
     */
    static List<int[]> cover(List<int[]> cells) {
        List<int[]> out = new ArrayList<>();
        if (cells.isEmpty()) {
            return out;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
        }
        int nx = maxX - minX + 1, ny = maxY - minY + 1, nz = maxZ - minZ + 1;
        boolean[] full = new boolean[nx * ny * nz];
        boolean[] done = new boolean[nx * ny * nz];
        for (int[] c : cells) {
            full[((c[1] - minY) * nz + (c[2] - minZ)) * nx + (c[0] - minX)] = true;
        }
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    int i = (y * nz + z) * nx + x;
                    if (!full[i] || done[i]) {
                        continue;
                    }
                    int x0 = x, x1 = x;
                    while (x0 > 0 && full[(y * nz + z) * nx + x0 - 1]) x0--;
                    while (x1 < nx - 1 && full[(y * nz + z) * nx + x1 + 1]) x1++;
                    int z0 = z, z1 = z;
                    while (z0 > 0 && row(full, nx, nz, y, z0 - 1, x0, x1)) z0--;
                    while (z1 < nz - 1 && row(full, nx, nz, y, z1 + 1, x0, x1)) z1++;
                    int y0 = y, y1 = y;
                    while (y0 > 0 && layer(full, nx, nz, y0 - 1, z0, z1, x0, x1)) y0--;
                    while (y1 < ny - 1 && layer(full, nx, nz, y1 + 1, z0, z1, x0, x1)) y1++;
                    for (int yy = y0; yy <= y1; yy++) {
                        for (int zz = z0; zz <= z1; zz++) {
                            for (int xx = x0; xx <= x1; xx++) {
                                done[(yy * nz + zz) * nx + xx] = true;
                            }
                        }
                    }
                    out.add(new int[] { x0 + minX, y0 + minY, z0 + minZ, x1 + minX, y1 + minY, z1 + minZ });
                }
            }
        }
        return out;
    }

    private static boolean row(boolean[] full, int nx, int nz, int y, int z, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            if (!full[(y * nz + z) * nx + x]) {
                return false;
            }
        }
        return true;
    }

    private static boolean layer(boolean[] full, int nx, int nz, int y, int z0, int z1, int x0, int x1) {
        for (int z = z0; z <= z1; z++) {
            if (!row(full, nx, nz, y, z, x0, x1)) {
                return false;
            }
        }
        return true;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static int kx(long k) { return (int) (k << 1 >> 43); }
    private static int ky(long k) { return (int) (k << 22 >> 43); }
    private static int kz(long k) { return (int) (k << 43 >> 43); }

    // ---------------------------------------------------------------- shapes

    static Shape ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz) {
        double ax = Math.max(1e-4, Math.abs(rx)), ay = Math.max(1e-4, Math.abs(ry)), az = Math.max(1e-4, Math.abs(rz));
        return new Shape() {
            public boolean inside(double x, double y, double z) {
                double dx = (x - cx) / ax, dy = (y - cy) / ay, dz = (z - cz) / az;
                return dx * dx + dy * dy + dz * dz <= 1;
            }

            public double[] bounds() {
                return new double[] { cx - ax, cy - ay, cz - az, cx + ax, cy + ay, cz + az };
            }
        };
    }

    /** Upright: radius r0 at the bottom, r1 at the top. A cylinder has r0 == r1, a cone r1 == 0. */
    static Shape frustum(double cx, double y0, double cz, double r0, double r1, double height) {
        double h = Math.max(1e-4, Math.abs(height));
        double a = Math.abs(r0), b = Math.abs(r1), r = Math.max(a, b);
        return new Shape() {
            public boolean inside(double x, double y, double z) {
                double t = (y - y0) / h;
                if (t < 0 || t > 1) {
                    return false;
                }
                double rad = a + (b - a) * t;
                double dx = x - cx, dz = z - cz;
                return dx * dx + dz * dz <= rad * rad;
            }

            public double[] bounds() {
                return new double[] { cx - r, y0, cz - r, cx + r, y0 + h, cz + r };
            }
        };
    }

    /** Rounded rod from p to q, radius r0 at p and r1 at q (possibly several segments: a tube). */
    static Shape tube(List<double[]> points, double r0, double r1) {
        int n = points.size();
        double[] length = new double[n];
        for (int i = 1; i < n; i++) {
            double[] a = points.get(i - 1), b = points.get(i);
            length[i] = length[i - 1] + Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        }
        double total = Math.max(1e-6, length[n - 1]);
        double ra = Math.abs(r0), rb = Math.abs(r1), rMax = Math.max(ra, rb);
        double[] box = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (double[] p : points) {
            for (int i = 0; i < 3; i++) {
                box[i] = Math.min(box[i], p[i] - rMax);
                box[i + 3] = Math.max(box[i + 3], p[i] + rMax);
            }
        }
        return new Shape() {
            public boolean inside(double x, double y, double z) {
                for (int i = 1; i < n; i++) {
                    double[] a = points.get(i - 1), b = points.get(i);
                    double vx = b[0] - a[0], vy = b[1] - a[1], vz = b[2] - a[2];
                    double len2 = vx * vx + vy * vy + vz * vz;
                    double t = len2 < 1e-12 ? 0 : ((x - a[0]) * vx + (y - a[1]) * vy + (z - a[2]) * vz) / len2;
                    t = Math.max(0, Math.min(1, t));
                    double along = (length[i - 1] + t * (length[i] - length[i - 1])) / total;
                    double rad = ra + (rb - ra) * along;
                    double dx = x - (a[0] + t * vx), dy = y - (a[1] + t * vy), dz = z - (a[2] + t * vz);
                    if (dx * dx + dy * dy + dz * dz <= rad * rad) {
                        return true;
                    }
                }
                return false;
            }

            public double[] bounds() {
                return box.clone();
            }
        };
    }

    /** Smooth curve through the points (Catmull-Rom), as a list of close points. */
    static List<double[]> curve(List<double[]> points) {
        if (points.size() < 3) {
            return points;
        }
        List<double[]> out = new ArrayList<>();
        int steps = 8;
        for (int i = 0; i < points.size() - 1; i++) {
            double[] p0 = points.get(Math.max(0, i - 1)), p1 = points.get(i), p2 = points.get(i + 1);
            double[] p3 = points.get(Math.min(points.size() - 1, i + 2));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps, t2 = t * t, t3 = t2 * t;
                double[] q = new double[3];
                for (int k = 0; k < 3; k++) {
                    q[k] = 0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                        + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                }
                out.add(q);
            }
        }
        out.add(points.get(points.size() - 1));
        return out;
    }

    /** Box with min corner (x, y, z) and size, edges rounded by radius (0 = sharp). */
    static Shape roundBox(double x, double y, double z, double sx, double sy, double sz, double radius) {
        double w = Math.abs(sx), h = Math.abs(sy), l = Math.abs(sz);
        double r = Math.max(0, Math.min(Math.abs(radius), Math.min(w, Math.min(h, l)) / 2));
        double ix0 = x + r, iy0 = y + r, iz0 = z + r, ix1 = x + w - r, iy1 = y + h - r, iz1 = z + l - r;
        return new Shape() {
            public boolean inside(double px, double py, double pz) {
                double dx = Math.max(0, Math.max(ix0 - px, px - ix1));
                double dy = Math.max(0, Math.max(iy0 - py, py - iy1));
                double dz = Math.max(0, Math.max(iz0 - pz, pz - iz1));
                return dx * dx + dy * dy + dz * dz <= r * r + 1e-12;
            }

            public double[] bounds() {
                return new double[] { x, y, z, x + w, y + h, z + l };
            }
        };
    }

    private static double sq(double v) {
        return v * v;
    }
}
