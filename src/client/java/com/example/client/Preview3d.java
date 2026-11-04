package com.example.client;

import com.example.AiBuildAssistantMod;
import com.example.ai.build.BuildPlan;
import com.example.ai.build.BuildRecord;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D pictures of saved designs (objects, equipment and buildings), seen from the front-left and
 * above. A picture is drawn once into an image off screen and kept as a texture, so a full 보관함
 * costs no more than flat pictures; pictures not drawn yet are made a few per frame (buildings one
 * at a time) so opening a page never stalls. Objects and equipment can also be drawn live (the
 * detail view, turned with the mouse).
 */
public final class Preview3d {

    /** Pixels of a kept picture (square). */
    public static final int THUMB = 128;
    public static final int BIG = 256;
    /** The usual view: 30 degrees from above, 35 to the side. */
    public static final float PITCH = 30;
    public static final float YAW = 35;
    private static final int MAX_KEPT = 240;
    /** Buildings with more visible blocks than this are drawn from their outermost blocks only. */
    private static final int MAX_BLOCKS = 60_000;
    private static final long FRAME_BUDGET_NS = 6_000_000;

    private record Pending(String key, BuildRecord record, int px) {
    }

    private static final LinkedHashMap<String, Identifier> KEPT = new LinkedHashMap<>(64, 0.75F, true);
    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static final java.util.Set<String> QUEUED = new java.util.HashSet<>();
    private static int made;

    private Preview3d() {
    }

    /**
     * Draw the kept 3D picture of a design in a screen box, or queue it (and draw nothing yet).
     * {@code px}: THUMB for grid cells, BIG for the detail view.
     */
    public static boolean drawKept(DrawContext ctx, BuildRecord record, int x, int y, int size, int px) {
        if (record == null || record.plan == null) {
            return false;
        }
        String key = record.id + "@" + px;
        Identifier id = KEPT.get(key);
        if (id == null) {
            // Asked for this frame: made at its end (pictures of pages no longer shown are not made).
            if (QUEUED.add(key)) {
                QUEUE.add(new Pending(key, record, px));
            }
            return false;
        }
        ctx.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0, 0, size, size, px, px, px, px);
        return true;
    }

    /**
     * Make the pictures this frame asked for, within its time (at least one); call once per frame
     * after drawing. The rest are asked for again by the next frame if they are still on screen.
     */
    public static void work(DrawContext ctx) {
        if (QUEUE.isEmpty()) {
            return;
        }
        ctx.draw(); // what the screen drew so far goes out before the off-screen picture
        long start = System.nanoTime();
        boolean first = true;
        while (!QUEUE.isEmpty() && (first || System.nanoTime() - start < FRAME_BUDGET_NS)) {
            Pending p = QUEUE.poll();
            QUEUED.remove(p.key());
            boolean big = !p.record().plan.isObject() && p.record().plan.ops.size() > 400;
            if (big && !first) {
                QUEUE.addFirst(p); // a big building takes a frame of its own
                QUEUED.add(p.key());
                break;
            }
            first = false;
            long t0 = System.nanoTime();
            try {
                keep(p.key(), render(p.record().plan, p.px()));
                debug("made " + p.key() + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
            } catch (RuntimeException e) {
                System.out.println("[ERROR] 3D picture of " + p.record().id + " failed: " + e.getMessage());
                keep(p.key(), null);
            }
        }
        if (!QUEUE.isEmpty()) {
            debug(QUEUE.size() + " left for the next frame");
        }
        // Left for the next frame to ask again (kept here only so tests can finish them).
        MISSED.clear();
        MISSED.addAll(QUEUE);
        QUEUE.clear();
        QUEUED.clear();
    }

    private static final List<Pending> MISSED = new ArrayList<>();

    private static void debug(String s) {
        if (System.getProperty("fabric.client.gametest") != null || Boolean.getBoolean("aib.debug3d")) {
            System.out.println("[3D] " + s);
        }
    }

    /** Forget a design's pictures (deleted or changed). */
    public static void forget(String recordId) {
        for (String key : new ArrayList<>(KEPT.keySet())) {
            if (key.startsWith(recordId + "@")) {
                Identifier id = KEPT.remove(key);
                if (id != null) {
                    MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
                }
            }
        }
    }

    private static void keep(String key, NativeImage image) {
        if (image == null) {
            image = new NativeImage(1, 1, true); // nothing to show: an empty picture, not tried again
        }
        Identifier id = Identifier.of(AiBuildAssistantMod.MOD_ID, "preview3d/" + (made++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        KEPT.put(key, id);
        while (KEPT.size() > MAX_KEPT) {
            Map.Entry<String, Identifier> oldest = KEPT.entrySet().iterator().next();
            KEPT.remove(oldest.getKey());
            MinecraftClient.getInstance().getTextureManager().destroyTexture(oldest.getValue());
        }
    }

    // ---------------------------------------------------------------- drawing

    /** The design drawn once off screen into a px x px image (transparent around it). */
    static NativeImage render(BuildPlan plan, int px) {
        MinecraftClient client = MinecraftClient.getInstance();
        SimpleFramebuffer fb = new SimpleFramebuffer(px, px, true);
        net.minecraft.client.gl.Framebuffer main = client.framebuffer;
        // The game's render layers draw into "the main buffer": for this picture, that is ours.
        client.framebuffer = fb;
        try {
            fb.setClearColor(0, 0, 0, 0);
            fb.clear();
            fb.beginWrite(true);
            // The screen's own projection stays: it spans the window in GUI units, which now fill this
            // picture, so a px x px drawing is stretched to those units.
            float guiW = client.getWindow().getFramebufferWidth() / (float) client.getWindow().getScaleFactor();
            float guiH = client.getWindow().getFramebufferHeight() / (float) client.getWindow().getScaleFactor();
            VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
            MatrixStack m = new MatrixStack();
            m.scale(guiW / px, guiH / px, 1);
            DiffuseLighting.enableGuiDepthLighting();
            try {
                if (plan.isObject()) {
                    drawParts(m, buffers, plan, px / 2.0F, px / 2.0F, px, YAW, PITCH, 1);
                } else {
                    drawBuilding(m, buffers, plan, px);
                }
                buffers.draw();
            } finally {
                DiffuseLighting.disableGuiDepthLighting();
            }
            // Read it back with its transparency (a screenshot would make the background black).
            NativeImage image = new NativeImage(px, px, false);
            RenderSystem.bindTexture(fb.getColorAttachment());
            image.loadFromTextureImage(0, false);
            image.mirrorVertically();
            return image;
        } finally {
            client.framebuffer = main;
            fb.delete();
            main.beginWrite(true);
        }
    }

    /** An object's or equipment's pieces in 3D in a screen box (live: for the detail view, turned by the mouse). */
    public static void drawLive(DrawContext ctx, BuildPlan plan, int x, int y, int size, float yaw, float pitch, float zoom) {
        if (plan == null || plan.parts.isEmpty()) {
            return;
        }
        ctx.draw();
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 150);
        DiffuseLighting.enableGuiDepthLighting();
        ctx.draw(buffers -> drawParts(m, buffers, plan, x + size / 2.0F, y + size / 2.0F, size, yaw, pitch, zoom));
        ctx.draw();
        DiffuseLighting.disableGuiDepthLighting();
        m.pop();
    }

    /** Pieces centred on (cx, cy), fitted into size, seen from yaw / pitch degrees. */
    private static void drawParts(MatrixStack m, VertexConsumerProvider buffers, BuildPlan plan, float cx, float cy, int size,
                                  float yaw, float pitch, float zoom) {
        double[] b = plan.partBounds();
        float scale = fit(b, size) * zoom;
        m.push();
        m.translate(cx, cy, 0);
        m.scale(scale, -scale, scale);
        m.multiply(new Quaternionf().rotateX((float) Math.toRadians(pitch)));
        m.multiply(new Quaternionf().rotateY((float) Math.toRadians(180 + yaw)));
        m.translate((float) -(b[0] + b[3]) / 2, (float) -(b[1] + b[4]) / 2, (float) -(b[2] + b[5]) / 2);
        GearModelRenderer.drawPlan(m, buffers, plan, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        m.pop();
    }

    /** Blocks per screen pixel so the whole box shows, turned and tilted, inside size. */
    private static float fit(double[] b, int size) {
        double w = b[3] - b[0], h = b[4] - b[1], d = b[5] - b[2];
        double across = Math.sqrt(w * w + d * d);
        double span = Math.max(across, h * 0.87 + across * 0.5);
        return (float) (size * 0.9 / Math.max(0.3, span));
    }

    /** A building's blocks (its outside: blocks with an open side) and its furniture, in the objects' frame. */
    private static void drawBuilding(MatrixStack m, VertexConsumerProvider buffers, BuildPlan plan, int px) {
        // Turned so that the player faced +Z like an object's designer: its front looks back at the viewer.
        int turns = BuildPlan.turnsBetween(plan.facing, "south");
        Map<Long, BlockState> blocks = blocks(plan, turns);
        if (blocks.isEmpty() && (plan.props == null || plan.props.isEmpty())) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : blocks.keySet()) {
            int x = kx(k), y = ky(k), z = kz(k);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        if (blocks.isEmpty()) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 1;
        }
        double[] b = { minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1 };
        float scale = fit(b, px);
        m.push();
        m.translate(px / 2.0F, px / 2.0F, 0);
        m.scale(scale, -scale, scale);
        m.multiply(new Quaternionf().rotateX((float) Math.toRadians(PITCH)));
        m.multiply(new Quaternionf().rotateY((float) Math.toRadians(180 + YAW)));
        m.translate((float) -(b[0] + b[3]) / 2, (float) -(b[1] + b[4]) / 2, (float) -(b[2] + b[5]) / 2);
        var renderer = MinecraftClient.getInstance().getBlockRenderManager();
        int drawn = 0;
        for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
            long k = e.getKey();
            int x = kx(k), y = ky(k), z = kz(k);
            if (!open(blocks, x, y, z)) {
                continue; // inside the walls: never seen
            }
            if (++drawn > MAX_BLOCKS) {
                break;
            }
            m.push();
            m.translate(x, y, z);
            renderer.renderBlockAsEntity(e.getValue(), m, buffers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            m.pop();
        }
        // Library furniture, where the building puts it.
        if (plan.props != null) {
            for (BuildPlan.Prop prop : plan.props) {
                com.example.ai.build.PropLibrary.Placed placed;
                try {
                    placed = com.example.ai.build.PropLibrary.place(prop);
                } catch (RuntimeException ex) {
                    continue;
                }
                if (placed == null) {
                    continue;
                }
                BuildPlan turned = com.example.ai.api.MinecraftAI.turned(placed.plan(), turns);
                GearModelRenderer.drawPlan(m, buffers, turned, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            }
        }
        m.pop();
    }

    private static boolean open(Map<Long, BlockState> blocks, int x, int y, int z) {
        int[][] sides = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] s : sides) {
            BlockState n = blocks.get(key(x + s[0], y + s[1], z + s[2]));
            if (n == null || !n.isOpaque() || !n.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                return true;
            }
        }
        return false;
    }

    /** The blocks a building's commands leave, turned by quarter turns. */
    static Map<Long, BlockState> blocks(BuildPlan plan, int turns) {
        Map<Long, BlockState> out = new HashMap<>();
        Map<String, BlockState> states = new HashMap<>();
        for (BuildPlan.Op op : plan.ops) {
            int[] a = BuildPlan.rotate(op.x1, op.z1, turns);
            int[] c = BuildPlan.rotate(op.x2, op.z2, turns);
            int x0 = Math.min(a[0], c[0]), x1 = Math.max(a[0], c[0]);
            int y0 = Math.min(op.y1, op.y2), y1 = Math.max(op.y1, op.y2);
            int z0 = Math.min(a[1], c[1]), z1 = Math.max(a[1], c[1]);
            boolean air = op.isAir();
            BlockState state = air ? null : states.computeIfAbsent(op.block, s -> parse(s, turns));
            if (!air && state == null) {
                continue;
            }
            String mode = "set".equals(op.kind) ? "replace" : op.mode;
            long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (air && volume > 200_000) {
                final int fx0 = x0, fx1 = x1, fy0 = y0, fy1 = y1, fz0 = z0, fz1 = z1;
                out.keySet().removeIf(k -> kx(k) >= fx0 && kx(k) <= fx1 && ky(k) >= fy0 && ky(k) <= fy1 && kz(k) >= fz0 && kz(k) <= fz1);
                continue;
            }
            boolean shellOnly = "outline".equals(mode) || "hollow".equals(mode) || volume > 200_000;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                        long k = key(x, y, z);
                        if (!shell && shellOnly) {
                            if ("hollow".equals(mode)) {
                                out.remove(k);
                            }
                            continue;
                        }
                        if ("keep".equals(mode) && out.containsKey(k)) {
                            continue;
                        }
                        if (air) {
                            out.remove(k);
                        } else {
                            out.put(k, state);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static BlockState parse(String block, int turns) {
        try {
            String turned = com.example.ai.api.MinecraftAI.turnBlockState(block, turns);
            return BlockArgumentParser.block(Registries.BLOCK, turned, false).blockState();
        } catch (Exception e) {
            return null;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static int kx(long k) { return (int) (k << 1 >> 43); }
    private static int ky(long k) { return (int) (k << 22 >> 43); }
    private static int kz(long k) { return (int) (k << 43 >> 43); }

    /** Tests: how many pictures wait to be made. */
    public static int pendingForTests() {
        return QUEUE.size();
    }

    /** Tests: make every waiting picture now. */
    public static void finishForTests() {
        debug("finishing " + MISSED.size() + " missed and " + QUEUE.size() + " queued");
        List<Pending> all = new ArrayList<>(MISSED);
        all.addAll(QUEUE);
        MISSED.clear();
        QUEUE.clear();
        QUEUED.clear();
        for (Pending p : all) {
            try {
                keep(p.key(), render(p.record().plan, p.px()));
            } catch (RuntimeException e) {
                keep(p.key(), null);
            }
        }
    }
}
