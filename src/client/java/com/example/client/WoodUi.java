package com.example.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Cozy farm-game look drawn with plain rectangles (no texture files): warm orange wood frames
 * with a light top-left edge and a dark bottom-right edge, cream parchment pages, brown ink.
 */
public final class WoodUi {

    public static final int OUTLINE = 0xFF4A2511;
    public static final int WOOD = 0xFFD9893C;
    public static final int WOOD_LIGHT = 0xFFF6BD62;
    public static final int WOOD_DARK = 0xFF95481B;
    public static final int PARCHMENT = 0xFFFCE3B4;
    public static final int PARCHMENT_DEEP = 0xFFF4CF92;
    public static final int PARCHMENT_LINE = 0xFFD9A462;
    public static final int PAPER = 0xFFFFF4DC;
    public static final int INK = 0xFF5A2A0A;
    public static final int INK_SOFT = 0xFF8C5A2E;
    public static final int TAG = 0xFFE07B2C;
    public static final int LEAF = 0xFF6E8B3D;
    public static final int LEAF_PAPER = 0xFFEAF4D2;

    private WoodUi() {
    }

    public static void outline(DrawContext ctx, int l, int t, int r, int b, int color) {
        ctx.fill(l, t, r, t + 1, color);
        ctx.fill(l, b - 1, r, b, color);
        ctx.fill(l, t, l + 1, b, color);
        ctx.fill(r - 1, t, r, b, color);
    }

    /** Raised box: dark outline, light top/left edge, dark bottom/right edge. */
    public static void bevel(DrawContext ctx, int l, int t, int r, int b, int fill, int light, int dark, int edge) {
        ctx.fill(l, t, r, b, OUTLINE);
        ctx.fill(l + 1, t + 1, r - 1, b - 1, dark);
        ctx.fill(l + 1, t + 1, r - 1 - edge, b - 1 - edge, light);
        ctx.fill(l + 1 + edge, t + 1 + edge, r - 1 - edge, b - 1 - edge, fill);
    }

    /** Sunken box (a slot or a text well): dark top/left, light bottom/right. */
    public static void inset(DrawContext ctx, int l, int t, int r, int b, int fill) {
        ctx.fill(l, t, r, b, PARCHMENT_LINE);
        ctx.fill(l, t, r - 1, b - 1, INK_SOFT);
        ctx.fill(l + 1, t + 1, r - 1, b - 1, fill);
    }

    /** The big window: thick wooden frame with corner pegs around a parchment page. */
    public static void frame(DrawContext ctx, int l, int t, int r, int b) {
        ctx.fill(l + 3, t + 3, r + 3, b + 3, 0x55000000); // soft shadow
        bevel(ctx, l, t, r, b, WOOD, WOOD_LIGHT, WOOD_DARK, 3);
        // Wood grain.
        for (int y = t + 3; y < b - 3; y += 6) {
            ctx.fill(l + 2, y, l + 7, y + 1, 0x22000000);
            ctx.fill(r - 7, y + 3, r - 2, y + 4, 0x22000000);
        }
        int pl = l + 8, pt = t + 8, pr = r - 8, pb = b - 8;
        ctx.fill(pl - 1, pt - 1, pr + 1, pb + 1, WOOD_DARK);
        ctx.fill(pl, pt, pr, pb, PARCHMENT);
        // Corner pegs.
        for (int[] c : new int[][] { { l + 2, t + 2 }, { r - 7, t + 2 }, { l + 2, b - 7 }, { r - 7, b - 7 } }) {
            ctx.fill(c[0], c[1], c[0] + 5, c[1] + 5, OUTLINE);
            ctx.fill(c[0] + 1, c[1] + 1, c[0] + 4, c[1] + 4, WOOD_LIGHT);
            ctx.fill(c[0] + 2, c[1] + 2, c[0] + 3, c[1] + 3, WOOD_DARK);
        }
    }

    /** Wooden name plaque with centred text (window title, speaker names). */
    public static void plaque(DrawContext ctx, TextRenderer font, String text, int cx, int y, int color) {
        int w = font.getWidth(text) + 16;
        int l = cx - w / 2;
        bevel(ctx, l, y, l + w, y + 16, WOOD, WOOD_LIGHT, WOOD_DARK, 2);
        ctx.drawText(font, text, cx - font.getWidth(text) / 2, y + 4, color, false);
    }

    /** Button drawn as a small wooden board with brown lettering. */
    public static class WoodButton extends ButtonWidget {
        private final boolean danger;

        public WoodButton(int x, int y, int width, int height, Text message, PressAction onPress, boolean danger) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.danger = danger;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hover = this.active && this.isHovered();
            int fill;
            int light;
            int dark;
            if (this.danger) {
                fill = hover ? 0xFFE0573E : 0xFFC9402C;
                light = 0xFFF28B6E;
                dark = 0xFF7D2214;
            } else if (!this.active) {
                fill = 0xFFD8B98C;
                light = 0xFFE9D2AE;
                dark = 0xFFB08E62;
            } else {
                fill = hover ? 0xFFF4B868 : 0xFFE9A55A;
                light = 0xFFFDD995;
                dark = 0xFFA45A22;
            }
            int l = this.getX(), t = this.getY(), r = l + this.width, b = t + this.height;
            bevel(ctx, l, t, r, b, fill, light, dark, 2);
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            int color = this.danger ? 0xFFFFF4DC : this.active ? INK : 0xFF9A7A55;
            String label = this.getMessage().getString();
            ctx.drawText(font, label, l + (this.width - font.getWidth(label)) / 2 + 1, t + (this.height - 8) / 2 + 1, color, false);
        }
    }
}
