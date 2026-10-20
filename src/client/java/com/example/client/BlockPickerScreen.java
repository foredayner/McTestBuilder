package com.example.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class BlockPickerScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 300;
    private static final int COLS = 9;
    private static final int ROWS = 8;
    private static final int SLOT_SIZE = 20;
    private static final int GRID_WIDTH = COLS * SLOT_SIZE;

    private static final int SECTION_COLOR = 0xFF88AAFF;
    private static final int HINT_COLOR = 0xFF888888;

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final List<String> blockIds;

    private int panelX;
    private int panelY;
    private int gridX;
    private int gridY;
    private int scrollRow;

    public BlockPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.literal("Pick Block"));
        this.parent = parent;
        this.onSelect = onSelect;
        this.blockIds = BlockIconHelper.getPickableBlockIds();
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        rebuildWidgets();
    }

    private void layoutPanel() {
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
        this.gridX = this.panelX + (PANEL_WIDTH - GRID_WIDTH) / 2;
        this.gridY = this.panelY + 44;
    }

    private void rebuildWidgets() {
        this.clearChildren();

        int listTop = this.gridY;
        int listHeight = ROWS * SLOT_SIZE;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("^"), button -> scroll(-1))
            .dimensions(this.panelX + PANEL_WIDTH - 18, listTop, 12, 16)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("v"), button -> scroll(1))
            .dimensions(this.panelX + PANEL_WIDTH - 18, listTop + listHeight - 16, 12, 16)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> cancel())
            .dimensions(this.panelX + PANEL_WIDTH - 76, this.panelY + PANEL_HEIGHT - 28, 60, 20)
            .build());
    }

    private void scroll(int delta) {
        this.scrollRow += delta;
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (blockIds.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - ROWS);
        this.scrollRow = Math.clamp(this.scrollRow, 0, maxScroll);
    }

    private void selectBlock(String blockId) {
        this.onSelect.accept(blockId);
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void cancel() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        layoutPanel();
        clampScroll();

        drawContext.fill(0, 0, this.width, this.height, 0x80000000);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, 0xF0202027);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + 28, 0xFF2E3744);
        drawContext.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + PANEL_WIDTH / 2, this.panelY + 9, 0xFFFFFFFF);
        drawContext.drawText(this.textRenderer, Text.literal("Click an icon to add"), this.panelX + 16, this.panelY + 32, SECTION_COLOR, false);

        int gridBottom = this.gridY + ROWS * SLOT_SIZE;
        drawContext.fill(this.gridX - 4, this.gridY - 4, this.gridX + GRID_WIDTH + 4, gridBottom + 4, 0xCC000000);

        String hoveredBlockId = null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (this.scrollRow + row) * COLS + col;
                if (index >= this.blockIds.size()) {
                    break;
                }

                int slotX = this.gridX + col * SLOT_SIZE;
                int slotY = this.gridY + row * SLOT_SIZE;
                String blockId = this.blockIds.get(index);

                drawContext.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x80404040);
                BlockIconHelper.drawBlockIcon(drawContext, blockId, slotX + 2, slotY + 2);

                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    hoveredBlockId = blockId;
                    drawContext.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x66FFFFFF);
                }
            }
        }

        drawContext.drawText(
            this.textRenderer,
            Text.literal(this.blockIds.size() + " blocks"),
            this.panelX + 16,
            this.panelY + PANEL_HEIGHT - 22,
            HINT_COLOR,
            false
        );

        super.render(drawContext, mouseX, mouseY, delta);

        if (hoveredBlockId != null) {
            BlockIconHelper.drawBlockTooltip(drawContext, hoveredBlockId, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            String blockId = blockAt(mouseX, mouseY);
            if (blockId != null) {
                selectBlock(blockId);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0D) {
            scroll(verticalAmount > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String blockAt(double mouseX, double mouseY) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (this.scrollRow + row) * COLS + col;
                if (index >= this.blockIds.size()) {
                    return null;
                }

                int slotX = this.gridX + col * SLOT_SIZE;
                int slotY = this.gridY + row * SLOT_SIZE;
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return this.blockIds.get(index);
                }
            }
        }
        return null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }
}
