package com.example.ai.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pixel art equipment, like a resource pack: the AI draws pictures as rows of palette letters and
 * they become pieces (one per run of same-coloured pixels), so everything else (drawing,
 * previews, effects, sizes) works as for any other piece.
 *
 * <ul>
 *   <li>{@link #sprite}: a flat picture one pixel thick, like a vanilla item; in the X-Y plane,
 *       facing -Z, its bottom-centre at the origin.</li>
 *   <li>{@link #box}: a box with a picture on each face, like vanilla armour on the body.</li>
 * </ul>
 */
public final class PixelArt {

    /** Most pixels across or down one picture. */
    public static final int MAX_SIZE = 64;
    /** Thickness of a face picture on a box (thin, so the faces meet at the edges). */
    public static final double FACE_THICKNESS = 0.002;
    public static final String FACES_HELP = "faces are front, back, left, right, top, bottom";

    /** One piece: what it is made of, its centre and size (axis-aligned in the picture's frame). */
    public record Piece(String material, double cx, double cy, double cz, double sx, double sy, double sz) {
    }

    /** A run of same-coloured pixels: columns c0..c1, rows r0..r1 (row 0 = top). */
    record Run(String material, int c0, int r0, int c1, int r1) {
    }

    private PixelArt() {
    }

    /**
     * The picture as materials per pixel ([row][column], null = empty). '.' and ' ' are empty;
     * every other letter must be in the palette. Throws with a reason the AI can fix.
     */
    static String[][] grid(Map<?, ?> palette, List<?> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("pixel art needs rows: a list of strings, top row first");
        }
        if (rows.size() > MAX_SIZE) {
            throw new IllegalArgumentException("pixel art has " + rows.size() + " rows; at most " + MAX_SIZE);
        }
        Map<Character, String> colours = new java.util.HashMap<>();
        if (palette != null) {
            for (Map.Entry<?, ?> e : palette.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.length() != 1 || key.equals(".") || key.equals(" ")) {
                    throw new IllegalArgumentException("palette keys are single letters (not '.' or space), got '" + key + "'");
                }
                String value = e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
                if (Paint.is(value) && Paint.parse(value) == null) {
                    throw new IllegalArgumentException("palette '" + key + "': \"" + value + "\" is not a colour: " + Paint.HELP);
                }
                colours.put(key.charAt(0), value);
            }
        }
        int width = -1;
        String[][] out = new String[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            String row = String.valueOf(rows.get(r));
            if (width < 0) {
                width = row.length();
                if (width == 0 || width > MAX_SIZE) {
                    throw new IllegalArgumentException("pixel art rows must be 1-" + MAX_SIZE + " pixels long, row 1 has " + width);
                }
            } else if (row.length() != width) {
                throw new IllegalArgumentException("pixel art row " + (r + 1) + " has " + row.length() + " pixels but row 1 has "
                    + width + "; every row must be the same length (use '.' for empty pixels)");
            }
            out[r] = new String[width];
            for (int c = 0; c < width; c++) {
                char ch = row.charAt(c);
                if (ch == '.' || ch == ' ') {
                    continue;
                }
                String material = colours.get(ch);
                if (material == null) {
                    throw new IllegalArgumentException("pixel art row " + (r + 1) + " uses '" + ch + "', which is not in the palette");
                }
                out[r][c] = material;
            }
        }
        return out;
    }

    /** Cover the pixels with as few same-material rectangles as a simple greedy pass finds. */
    static List<Run> runs(String[][] grid) {
        int h = grid.length;
        int w = h == 0 ? 0 : grid[0].length;
        boolean[][] done = new boolean[h][w];
        List<Run> out = new ArrayList<>();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                String m = grid[r][c];
                if (m == null || done[r][c]) {
                    continue;
                }
                int c1 = c;
                while (c1 + 1 < w && m.equals(grid[r][c1 + 1]) && !done[r][c1 + 1]) {
                    c1++;
                }
                int r1 = r;
                grow:
                while (r1 + 1 < h) {
                    for (int x = c; x <= c1; x++) {
                        if (!m.equals(grid[r1 + 1][x]) || done[r1 + 1][x]) {
                            break grow;
                        }
                    }
                    r1++;
                }
                for (int y = r; y <= r1; y++) {
                    for (int x = c; x <= c1; x++) {
                        done[y][x] = true;
                    }
                }
                out.add(new Run(m, c, r, c1, r1));
            }
        }
        return out;
    }

    /**
     * A flat picture, {@code pixel} blocks per pixel and one pixel thick, in the X-Y plane facing
     * -Z, its bottom-centre at (0, 0, 0).
     */
    public static List<Piece> sprite(Map<?, ?> palette, List<?> rows, double pixel) {
        String[][] g = grid(palette, rows);
        int h = g.length;
        int w = g[0].length;
        List<Piece> out = new ArrayList<>();
        for (Run run : runs(g)) {
            double x0 = (run.c0() - w / 2.0) * pixel, x1 = (run.c1() + 1 - w / 2.0) * pixel;
            double y0 = (h - 1 - run.r1()) * pixel, y1 = (h - run.r0()) * pixel;
            out.add(new Piece(run.material(), (x0 + x1) / 2, (y0 + y1) / 2, 0, x1 - x0, y1 - y0, pixel));
        }
        return out;
    }

    /**
     * A box (min corner x, y, z and size) with a picture stretched over each face given. Faces are
     * seen from outside, in the frame where someone facing the front sees +X on their right:
     * front (-Z, left column at -X), back (+Z, left column at +X), left (-X side, left column at
     * the back), right (+X side, left column at the front), top (from above, front at the bottom
     * row, left column at -X), bottom (from below, front at the top row, left column at -X).
     */
    public static List<Piece> box(Map<?, ?> palette, double x, double y, double z, double sx, double sy, double sz,
                                  Map<?, ?> faces) {
        if (faces == null || faces.isEmpty()) {
            throw new IllegalArgumentException("pixelBox needs faces, e.g. [front: rows, left: rows]; " + FACES_HELP);
        }
        double x1 = x + sx, y1 = y + sy, z1 = z + sz, t = FACE_THICKNESS;
        List<Piece> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : faces.entrySet()) {
            String face = String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT);
            if (!(e.getValue() instanceof List<?> rows)) {
                throw new IllegalArgumentException("pixelBox face " + face + " needs a list of rows");
            }
            String[][] g = grid(palette, rows);
            int h = g.length, w = g[0].length;
            for (Run r : runs(g)) {
                // Picture coordinates: u across (left to right), v down (top to bottom), both 0-1.
                double u0 = (double) r.c0() / w, u1 = (double) (r.c1() + 1) / w;
                double v0 = (double) r.r0() / h, v1 = (double) (r.r1() + 1) / h;
                double top = y1 - v0 * sy, bottom = y1 - v1 * sy;
                double[] b = switch (face) {
                    case "front" -> new double[] { x + u0 * sx, bottom, z - t, x + u1 * sx, top, z };
                    case "back" -> new double[] { x1 - u1 * sx, bottom, z1, x1 - u0 * sx, top, z1 + t };
                    case "left" -> new double[] { x - t, bottom, z1 - u1 * sz, x, top, z1 - u0 * sz };
                    case "right" -> new double[] { x1, bottom, z + u0 * sz, x1 + t, top, z + u1 * sz };
                    case "top" -> new double[] { x + u0 * sx, y1, z1 - v1 * sz, x + u1 * sx, y1 + t, z1 - v0 * sz };
                    case "bottom" -> new double[] { x + u0 * sx, y - t, z + v0 * sz, x + u1 * sx, y, z + v1 * sz };
                    default -> throw new IllegalArgumentException("pixelBox has no face '" + face + "'; " + FACES_HELP);
                };
                out.add(new Piece(r.material(), (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2,
                    b[3] - b[0], b[4] - b[1], b[5] - b[2]));
            }
        }
        return out;
    }

    /** v turned by yaw (around Y), then pitch (X), then roll (Z), degrees: the same turn pieces use. */
    public static double[] turn(double[] v, double yaw, double pitch, double roll) {
        double[] p = rot(v, Math.toRadians(roll), 2);
        p = rot(p, Math.toRadians(pitch), 0);
        return rot(p, Math.toRadians(yaw), 1);
    }

    private static double[] rot(double[] v, double a, int axis) {
        double c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[] { v[0], c * v[1] - s * v[2], s * v[1] + c * v[2] };
            case 1 -> new double[] { c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2] };
            default -> new double[] { c * v[0] - s * v[1], s * v[0] + c * v[1], v[2] };
        };
    }
}
