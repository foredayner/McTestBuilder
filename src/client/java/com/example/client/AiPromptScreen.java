package com.example.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AiPromptScreen extends Screen {

    private static final double PANEL_WIDTH_RATIO = 0.35;
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int LOG_LINE_HEIGHT = 11;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LOG_TEXT_COLOR = 0xFFB0B9C6;
    private static final int EMPTY_HINT_COLOR = 0xFF888888;

    private TextFieldWidget promptField;
    private int panelX;
    private int panelWidth;
    private int panelY;
    private int panelHeight;

    public AiPromptScreen() {
        super(Text.literal("Minecraft AI Build Assistant"));
    }

    public void onStateUpdated(boolean rebuildWidgets) {
        if (rebuildWidgets) {
            this.clearChildren();
            this.init();
        }
    }

    @Override
    protected void init() {
        super.init();
        AiClientState.setOpenScreen(this);
        layoutPanel();
        buildWidgets();
    }

    @Override
    public void removed() {
        AiClientState.clearOpenScreen(this);
        super.removed();
    }

    private void layoutPanel() {
        this.panelWidth = Math.max(MIN_PANEL_WIDTH, (int) (this.width * PANEL_WIDTH_RATIO));
        this.panelX = this.width - this.panelWidth - 10;
        this.panelY = 20;
        this.panelHeight = this.height - 40;
    }

    private void buildWidgets() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), button -> this.client.setScreen(new AiSettingsScreen(this)))
            .dimensions(this.panelX + this.panelWidth - 50, this.panelY + 5, 20, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("×"), button -> this.close())
            .dimensions(this.panelX + this.panelWidth - 25, this.panelY + 5, 20, 20)
            .build());

        int fieldY = this.panelY + this.panelHeight - (AiClientState.hasPendingApproval() ? 85 : 60);
        int sendWidth = 55;
        int webWidth = 45;
        int fieldWidth = this.panelWidth - sendWidth - webWidth - 20;

        this.promptField = new TextFieldWidget(this.textRenderer, this.panelX + 5, fieldY, fieldWidth, 20, Text.literal(""));
        this.promptField.setMaxLength(256);
        this.promptField.setPlaceholder(Text.literal("Describe the build..."));
        this.promptField.setFocused(true);
        this.addSelectableChild(this.promptField);

        int webX = this.panelX + fieldWidth + 10;
        ButtonWidget webButton = ButtonWidget.builder(webSearchLabel(), button -> {
                AiClientState.toggleSessionWebSearch();
                button.setMessage(webSearchLabel());
            })
            .dimensions(webX, fieldY, webWidth, 20)
            .build();
        webButton.active = AiClientState.isWebSearchAvailable();
        this.addDrawableChild(webButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Send"), button -> sendBuild())
            .dimensions(webX + webWidth + 5, fieldY, sendWidth, 20)
            .build());

        if (AiClientState.hasPendingApproval()) {
            int buttonY = this.panelY + this.panelHeight - 55;
            int buttonWidth = (this.panelWidth - 22) / 2;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), button -> AiClientState.approve())
                .dimensions(this.panelX + 8, buttonY, buttonWidth, 20)
                .build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> AiClientState.cancel())
                .dimensions(this.panelX + 14 + buttonWidth, buttonY, buttonWidth, 20)
                .build());
        }
    }

    private void sendBuild() {
        if (this.promptField == null) {
            return;
        }

        String prompt = this.promptField.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }

        AiClientState.addLog("[Request] " + prompt);
        if (AiClientState.willUseWebSearch()) {
            AiClientState.addLog("[Web] Search enabled for this request");
        }
        AiClientState.addLog("[Waiting...] Processing...");
        AiClientState.sendPromptText(prompt);
        this.promptField.setText("");
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        layoutPanel();

        drawContext.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, 0xE6202027);
        drawContext.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 30, 0xFF2E3744);
        drawContext.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + this.panelWidth / 2, this.panelY + 9, TITLE_COLOR);

        int contentX = this.panelX + 8;
        int contentY = this.panelY + 35;
        int contentHeight = this.panelHeight - (AiClientState.hasPendingApproval() ? 125 : 100);

        drawBorder(drawContext, contentX - 1, contentY - 1, this.panelWidth - 14, contentHeight + 2, 0xFF000000);
        drawContext.fill(contentX, contentY, this.panelX + this.panelWidth - 8, contentY + contentHeight, 0xCC000000);

        super.render(drawContext, mouseX, mouseY, delta);

        renderLogs(drawContext, contentX, contentY, contentHeight);

        if (this.promptField != null) {
            this.promptField.render(drawContext, mouseX, mouseY, delta);
        }
    }

    private void renderLogs(DrawContext drawContext, int contentX, int contentY, int contentHeight) {
        List<String> logs = AiClientState.getLogs();
        int textWidth = this.panelWidth - 22;

        if (logs.isEmpty()) {
            drawContext.drawText(this.textRenderer, Text.literal("Logs will appear here..."), contentX + 5, contentY + 5, EMPTY_HINT_COLOR, false);
            return;
        }

        List<OrderedText> wrappedLines = new ArrayList<>();
        for (String log : logs) {
            wrappedLines.addAll(this.textRenderer.wrapLines(Text.literal(log), textWidth));
        }

        int maxVisibleLines = Math.max(1, (contentHeight - 8) / LOG_LINE_HEIGHT);
        int startIndex = Math.max(0, wrappedLines.size() - maxVisibleLines);
        int logY = contentY + 5;

        for (int i = startIndex; i < wrappedLines.size(); i++) {
            if (logY + LOG_LINE_HEIGHT > contentY + contentHeight) {
                break;
            }
            drawContext.drawTextWithShadow(this.textRenderer, wrappedLines.get(i), contentX + 5, logY, LOG_TEXT_COLOR);
            logY += LOG_LINE_HEIGHT;
        }
    }

    private Text webSearchLabel() {
        if (!AiClientState.isWebSearchAvailable()) {
            return Text.literal("Web");
        }
        return Text.literal(AiClientState.willUseWebSearch() ? "Web:ON" : "Web:OFF");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sendBuild();
            return true;
        }
        if (this.promptField != null && this.promptField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void drawBorder(DrawContext drawContext, int x, int y, int width, int height, int color) {
        drawContext.fill(x, y, x + width, y + 1, color);
        drawContext.fill(x, y + height - 1, x + width, y + height, color);
        drawContext.fill(x, y, x + 1, y + height, color);
        drawContext.fill(x + width - 1, y, x + width, y + height, color);
    }
}
