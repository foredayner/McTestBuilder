package com.example.client;

import com.example.ai.build.BuildRecord;
import com.example.client.network.AiClientNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Main window, three tabs:
 *  Create  - request a build, watch the log, accept or cancel
 *  History - finished builds with previews; open one to read the full request and rebuild it
 *  Logs    - saved log files by day
 */
public class AiPromptScreen extends Screen {

    private enum Tab { CREATE, HISTORY, LOGS }

    private static final double PANEL_WIDTH_RATIO = 0.35;
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int LINE = 11;
    private static final int ROW = 40;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int LOG_TEXT_COLOR = 0xFFB0B9C6;
    private static final int DIM = 0xFF888888;
    private static final int ACCENT = 0xFF88AAFF;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Kept across re-inits (the screen rebuilds its widgets when state changes).
    private static Tab tab = Tab.CREATE;
    private static String selectedId = null;
    private static int logScroll = 0;      // lines up from the bottom
    private static int listScroll = 0;     // rows
    private static int detailScroll = 0;   // pixels
    private static int fileScroll = 0;     // lines up from the bottom
    private static int dayIndex = 0;

    private TextFieldWidget promptField;
    private String draftPrompt = "";
    private boolean confirmDelete = false;
    private int panelX;
    private int panelWidth;
    private int panelY;
    private int panelHeight;
    private List<String> fileLines = List.of();
    private List<String> days = List.of();

    public AiPromptScreen() {
        super(Text.literal("Minecraft AI Build Assistant"));
    }

    public void onStateUpdated(boolean rebuildWidgets) {
        if (rebuildWidgets) {
            rebuild();
        }
    }

    private void rebuild() {
        if (this.promptField != null) {
            this.draftPrompt = this.promptField.getText();
        }
        this.clearChildren();
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        AiClientState.setOpenScreen(this);
        layoutPanel();
        this.promptField = null;
        buildHeader();
        switch (tab) {
            case CREATE -> buildCreate();
            case HISTORY -> buildHistory();
            case LOGS -> buildLogs();
        }
    }

    @Override
    public void removed() {
        AiClientState.clearOpenScreen(this);
        super.removed();
    }

    private void layoutPanel() {
        if (tab == Tab.CREATE) {
            this.panelWidth = Math.max(MIN_PANEL_WIDTH, (int) (this.width * PANEL_WIDTH_RATIO));
        } else {
            this.panelWidth = Math.min(this.width - 20, Math.max(320, (int) (this.width * 0.6)));
        }
        this.panelWidth = Math.min(this.panelWidth, this.width - 20);
        this.panelX = this.width - this.panelWidth - 10;
        this.panelY = 10;
        this.panelHeight = this.height - 20;
    }

    private int bodyTop() { return this.panelY + 32; }
    private int bodyBottom() { return this.panelY + this.panelHeight - 6; }
    private int bodyLeft() { return this.panelX + 8; }
    private int bodyRight() { return this.panelX + this.panelWidth - 8; }

    // ---------------------------------------------------------------- header

    private void buildHeader() {
        int x = this.panelX + 6;
        for (Tab t : Tab.values()) {
            String label = switch (t) {
                case CREATE -> "Create";
                case HISTORY -> "History";
                case LOGS -> "Logs";
            };
            ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> switchTab(t))
                .dimensions(x, this.panelY + 5, 54, 20)
                .build();
            button.active = tab != t;
            this.addDrawableChild(button);
            x += 56;
        }
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), button -> this.client.setScreen(new AiSettingsScreen(this)))
            .dimensions(this.panelX + this.panelWidth - 50, this.panelY + 5, 20, 20)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("×"), button -> this.close())
            .dimensions(this.panelX + this.panelWidth - 25, this.panelY + 5, 20, 20)
            .build());
    }

    private void switchTab(Tab t) {
        tab = t;
        this.confirmDelete = false;
        if (t == Tab.LOGS) {
            fileScroll = 0;
        }
        rebuild();
    }

    // ---------------------------------------------------------------- create tab

    private int createLogBottom() {
        return this.panelY + this.panelHeight - (AiClientState.hasPendingApproval() ? 90 : 64);
    }

    private void buildCreate() {
        int fieldY = this.panelY + this.panelHeight - (AiClientState.hasPendingApproval() ? 85 : 58);
        int sendWidth = 55;
        int webWidth = 45;
        int fieldWidth = this.panelWidth - sendWidth - webWidth - 20;

        this.promptField = new TextFieldWidget(this.textRenderer, this.panelX + 5, fieldY, fieldWidth, 20, Text.literal(""));
        this.promptField.setMaxLength(256);
        this.promptField.setPlaceholder(Text.literal("Describe the build..."));
        this.promptField.setText(this.draftPrompt);
        this.promptField.setFocused(true);
        this.addSelectableChild(this.promptField);
        this.setFocused(this.promptField);

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
            int buttonY = this.panelY + this.panelHeight - 58;
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
        logScroll = 0;
        AiClientState.addLog("[Request] " + prompt);
        if (AiClientState.willUseWebSearch()) {
            AiClientState.addLog("[Web] Search enabled for this request");
        }
        AiClientState.addLog("[Waiting...] Processing...");
        AiClientState.sendPromptText(prompt);
        this.promptField.setText("");
        this.draftPrompt = "";
    }

    private void renderCreate(DrawContext ctx) {
        int top = bodyTop();
        int bottom = createLogBottom();
        box(ctx, bodyLeft(), top, bodyRight(), bottom);

        List<String> logs = AiClientState.getLogs();
        if (logs.isEmpty()) {
            ctx.drawText(this.textRenderer, "Logs will appear here...", bodyLeft() + 5, top + 5, DIM, false);
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String log : logs) {
            lines.addAll(this.textRenderer.wrapLines(Text.literal(log), bodyRight() - bodyLeft() - 10));
        }
        drawScrolledFromBottom(ctx, lines, top, bottom, logScroll, LOG_TEXT_COLOR);
    }

    // ---------------------------------------------------------------- history tab

    private BuildRecord selected() {
        if (selectedId == null) {
            return null;
        }
        for (BuildRecord r : BuildHistory.list()) {
            if (r.id.equals(selectedId)) {
                return r;
            }
        }
        selectedId = null;
        return null;
    }

    private void buildHistory() {
        BuildRecord record = selected();
        if (record == null) {
            return;
        }
        int bottomY = bodyBottom() - 20;
        int third = (bodyRight() - bodyLeft() - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("< Back"), b -> {
                selectedId = null;
                rebuild();
            })
            .dimensions(bodyLeft(), bottomY, third, 20)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Build here"), b -> {
                AiClientNetworking.sendRebuild(record);
                AiClientState.addLog("[History] Rebuild requested: builds in front of you, facing where you look.");
            })
            .dimensions(bodyLeft() + third + 4, bottomY, third, 20)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(this.confirmDelete ? "Really delete?" : "Delete"), b -> {
                if (!this.confirmDelete) {
                    this.confirmDelete = true;
                    rebuild();
                    return;
                }
                BuildHistory.delete(record);
                selectedId = null;
                this.confirmDelete = false;
                rebuild();
            })
            .dimensions(bodyLeft() + 2 * third + 8, bottomY, third, 20)
            .build());
    }

    private void renderHistory(DrawContext ctx, int mouseX, int mouseY) {
        BuildRecord record = selected();
        if (record != null) {
            renderDetail(ctx, record);
            return;
        }
        int top = bodyTop();
        int bottom = bodyBottom();
        List<BuildRecord> records = BuildHistory.list();
        if (records.isEmpty()) {
            ctx.drawText(this.textRenderer, "No builds yet. Accepted builds appear here.", bodyLeft() + 4, top + 6, DIM, false);
            return;
        }
        int visible = Math.max(1, (bottom - top) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, records.size() - visible));
        int textX = bodyLeft() + ROW + 2;
        int textW = bodyRight() - textX - 4;
        for (int i = 0; i < visible && listScroll + i < records.size(); i++) {
            BuildRecord r = records.get(listScroll + i);
            int y = top + i * ROW;
            boolean hover = mouseX >= bodyLeft() && mouseX < bodyRight() && mouseY >= y && mouseY < y + ROW - 2;
            ctx.fill(bodyLeft(), y, bodyRight(), y + ROW - 2, hover ? 0xCC2E3744 : 0xAA000000);
            BuildPreview p = BuildPreview.of(r);
            ctx.fill(bodyLeft() + 2, y + 2, bodyLeft() + ROW - 4, y + ROW - 4, 0xFF15151A);
            BuildPreview.draw(ctx, p.top, p.topW, p.topH, bodyLeft() + 3, y + 3, ROW - 8, ROW - 8);
            ctx.drawText(this.textRenderer, ellipsize(r.prompt.replace('\n', ' '), textW), textX, y + 6, TEXT, false);
            String info = DATE.format(new Date(r.createdAt)) + "  " + p.sizeX + "x" + p.sizeY + "x" + p.sizeZ + "  " + r.model;
            ctx.drawText(this.textRenderer, ellipsize(info, textW), textX, y + 20, DIM, false);
        }
        if (records.size() > visible) {
            String more = (listScroll + 1) + "-" + Math.min(records.size(), listScroll + visible) + " / " + records.size();
            ctx.drawText(this.textRenderer, more, bodyRight() - this.textRenderer.getWidth(more), bottom - 9, DIM, false);
        }
    }

    private void renderDetail(DrawContext ctx, BuildRecord r) {
        int top = bodyTop();
        int bottom = bodyBottom() - 24;
        int left = bodyLeft();
        int width = bodyRight() - left;
        BuildPreview p = BuildPreview.of(r);

        ctx.enableScissor(left, top, bodyRight(), bottom);
        int y = top + 2 - detailScroll;
        ctx.drawText(this.textRenderer, "Request", left, y, ACCENT, false);
        y += LINE + 2;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(r.prompt), width - 4)) {
            ctx.drawText(this.textRenderer, line, left + 2, y, TEXT, false);
            y += LINE;
        }
        y += 6;
        int img = Math.min(150, (width - 10) / 2);
        ctx.drawText(this.textRenderer, "Top view (front at top)", left, y, ACCENT, false);
        ctx.drawText(this.textRenderer, "Front view", left + img + 10, y, ACCENT, false);
        y += LINE + 2;
        ctx.fill(left, y, left + img, y + img, 0xFF15151A);
        ctx.fill(left + img + 10, y, left + 2 * img + 10, y + img, 0xFF15151A);
        BuildPreview.draw(ctx, p.top, p.topW, p.topH, left + 2, y + 2, img - 4, img - 4);
        BuildPreview.draw(ctx, p.front, p.frontW, p.frontH, left + img + 12, y + 2, img - 4, img - 4);
        y += img + 8;
        String[] info = {
            "Size: " + p.sizeX + " x " + p.sizeY + " x " + p.sizeZ + " (W x H x L), " + p.blocks + " blocks",
            "Created: " + DATE.format(new Date(r.createdAt)),
            "AI: " + r.provider + " / " + r.model,
            "Build here: placed in front of you, turned to face where you look."
        };
        for (String s : info) {
            ctx.drawText(this.textRenderer, ellipsize(s, width), left, y, s.startsWith("Build here") ? DIM : TEXT, false);
            y += LINE;
        }
        ctx.disableScissor();
        int contentHeight = y + detailScroll - top;
        detailScroll = Math.max(0, Math.min(detailScroll, contentHeight - (bottom - top)));
    }

    // ---------------------------------------------------------------- logs tab

    private void buildLogs() {
        this.days = LogStore.days();
        dayIndex = Math.max(0, Math.min(dayIndex, Math.max(0, this.days.size() - 1)));
        this.fileLines = this.days.isEmpty() ? List.of() : LogStore.read(this.days.get(dayIndex));
        ButtonWidget older = ButtonWidget.builder(Text.literal("<"), b -> {
                dayIndex++;
                fileScroll = 0;
                rebuild();
            })
            .dimensions(bodyLeft(), bodyTop(), 20, 20)
            .build();
        older.active = dayIndex < this.days.size() - 1;
        this.addDrawableChild(older);
        ButtonWidget newer = ButtonWidget.builder(Text.literal(">"), b -> {
                dayIndex--;
                fileScroll = 0;
                rebuild();
            })
            .dimensions(bodyLeft() + 120, bodyTop(), 20, 20)
            .build();
        newer.active = dayIndex > 0;
        this.addDrawableChild(newer);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reload"), b -> rebuild())
            .dimensions(bodyRight() - 60, bodyTop(), 60, 20)
            .build());
    }

    private void renderLogs(DrawContext ctx) {
        String day = this.days.isEmpty() ? "(no logs)" : this.days.get(dayIndex);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(day), bodyLeft() + 70, bodyTop() + 6, TEXT);
        int top = bodyTop() + 26;
        int bottom = bodyBottom();
        box(ctx, bodyLeft(), top, bodyRight(), bottom);
        if (this.fileLines.isEmpty()) {
            ctx.drawText(this.textRenderer, "Nothing logged yet.", bodyLeft() + 5, top + 5, DIM, false);
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String line : this.fileLines) {
            lines.addAll(this.textRenderer.wrapLines(Text.literal(line.replace("\t", "  ")), bodyRight() - bodyLeft() - 10));
        }
        drawScrolledFromBottom(ctx, lines, top, bottom, fileScroll, LOG_TEXT_COLOR);
    }

    // ---------------------------------------------------------------- shared drawing

    private void box(DrawContext ctx, int l, int t, int r, int b) {
        ctx.fill(l - 1, t - 1, r + 1, b + 1, 0xFF000000);
        ctx.fill(l, t, r, b, 0xCC000000);
    }

    /** Lines anchored at the bottom; scroll = lines moved up. Clamps the scroll it was given. */
    private int drawScrolledFromBottom(DrawContext ctx, List<OrderedText> lines, int top, int bottom, int scroll, int color) {
        int visible = Math.max(1, (bottom - top - 8) / LINE);
        int maxScroll = Math.max(0, lines.size() - visible);
        int s = Math.max(0, Math.min(scroll, maxScroll));
        int end = lines.size() - s;
        int start = Math.max(0, end - visible);
        int y = top + 4;
        for (int i = start; i < end; i++) {
            ctx.drawTextWithShadow(this.textRenderer, lines.get(i), bodyLeft() + 5, y, color);
            y += LINE;
        }
        if (s > 0) {
            String hint = "▼ " + s;
            ctx.drawText(this.textRenderer, hint, bodyRight() - this.textRenderer.getWidth(hint) - 4, bottom - 10, DIM, false);
        }
        return maxScroll;
    }

    private String ellipsize(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        return this.textRenderer.trimToWidth(text, Math.max(0, width - this.textRenderer.getWidth("…"))) + "…";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layoutPanel();
        ctx.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, 0xE6202027);
        ctx.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 30, 0xFF2E3744);

        switch (tab) {
            case CREATE -> renderCreate(ctx);
            case HISTORY -> renderHistory(ctx, mouseX, mouseY);
            case LOGS -> renderLogs(ctx);
        }

        super.render(ctx, mouseX, mouseY, delta);
        if (this.promptField != null) {
            this.promptField.render(ctx, mouseX, mouseY, delta);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == Tab.HISTORY && selected() == null && button == 0
            && mouseX >= bodyLeft() && mouseX < bodyRight() && mouseY >= bodyTop() && mouseY < bodyBottom()) {
            int index = listScroll + (int) ((mouseY - bodyTop()) / ROW);
            List<BuildRecord> records = BuildHistory.list();
            if (index >= 0 && index < records.size()) {
                selectedId = records.get(index).id;
                detailScroll = 0;
                this.confirmDelete = false;
                rebuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0D || mouseX < this.panelX || mouseX > this.panelX + this.panelWidth) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int step = verticalAmount > 0 ? 1 : -1;
        switch (tab) {
            case CREATE -> logScroll = Math.max(0, logScroll + step * 3);
            case LOGS -> fileScroll = Math.max(0, fileScroll + step * 3);
            case HISTORY -> {
                if (selected() != null) {
                    detailScroll = Math.max(0, detailScroll - step * 20);
                } else {
                    listScroll = Math.max(0, listScroll - step);
                }
            }
        }
        return true;
    }

    private Text webSearchLabel() {
        if (!AiClientState.isWebSearchAvailable()) {
            return Text.literal("Web");
        }
        return Text.literal(AiClientState.willUseWebSearch() ? "Web:ON" : "Web:OFF");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.CREATE && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
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
}
