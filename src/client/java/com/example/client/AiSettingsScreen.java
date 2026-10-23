package com.example.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import com.example.client.network.AiClientNetworking;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settings. The form scrolls (mouse wheel) when the window is short, with Save / Cancel always
 * visible at the bottom. Typed values survive re-layout (scrolling, window resize, the
 * forbidden-blocks screen), and Esc saves like vanilla option screens; Cancel discards.
 */
public class AiSettingsScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int HEADER = 30;
    private static final int FOOTER = 32;
    private static final int CONTENT_HEIGHT = 380;
    private static final int LABEL_COLOR = 0xFFCCCCCC;
    private static final int SECTION_COLOR = 0xFF88AAFF;

    private static final List<String> OLLAMA_MODELS = List.of(
        "qwen2.5-coder:7b", "qwen2.5-coder:14b", "qwen2.5-coder:3b", "gemma2:2b", "llama3.2", "mistral", "qwen2.5"
    );
    private static final List<String> OPENAI_MODELS = List.of(
        "gpt-4.1-mini", "gpt-4.1", "gpt-4o", "gpt-4o-mini", "gpt-5"
    );
    private static final List<Integer> KEEP_ALIVE = List.of(5, 30, 120, -1);

    private final Screen parent;
    private final AiBuilderConfig draft;
    private boolean saved = false;
    private int scroll = 0;

    private int panelX;
    private int panelY;
    private int panelHeight;

    private CyclingButtonWidget<AiProvider> providerButton;
    private CyclingButtonWidget<String> modelPresetButton;
    private TextFieldWidget modelField;
    private TextFieldWidget apiUrlField;
    private TextFieldWidget apiKeyField;
    private CyclingButtonWidget<Integer> keepAliveButton;
    private BuildSpeedSlider speedSlider;
    private CyclingButtonWidget<Integer> retryButton;
    private ButtonWidget forbiddenBlocksButton;
    private CyclingButtonWidget<Boolean> debugToggle;
    private CyclingButtonWidget<Boolean> webSearchToggle;
    private final List<ClickableWidget> scrolled = new ArrayList<>();

    public AiSettingsScreen(Screen parent) {
        super(Text.literal("Minecraft AI Build Assistant Settings"));
        this.parent = parent;
        this.draft = AiBuilderConfig.get().copy();
    }

    @Override
    protected void init() {
        super.init();
        syncDraft(); // keep what was typed when the screen is laid out again
        layoutPanel();
        buildWidgets();
    }

    private void layoutPanel() {
        this.panelHeight = Math.min(this.height - 16, HEADER + CONTENT_HEIGHT + FOOTER);
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    private int viewTop() { return this.panelY + HEADER; }
    private int viewBottom() { return this.panelY + this.panelHeight - FOOTER; }
    private int maxScroll() { return Math.max(0, CONTENT_HEIGHT - (this.panelHeight - HEADER - FOOTER)); }
    /** Screen y of a row at content offset rel. */
    private int at(int rel) { return viewTop() + rel - this.scroll; }

    private <T extends ClickableWidget> T scrolledChild(T widget) {
        this.scrolled.add(widget);
        return this.addDrawableChild(widget);
    }

    private void buildWidgets() {
        this.scrolled.clear();
        int x = this.panelX + 16;
        int w = PANEL_WIDTH - 32;

        this.providerButton = scrolledChild(CyclingButtonWidget.builder((AiProvider p) -> Text.literal(p.getDisplayName()))
            .values(Arrays.asList(AiProvider.values()))
            .initially(this.draft.provider)
            .build(x, at(20), w, 20, Text.literal("Provider"), (button, provider) -> switchProvider(provider)));

        this.modelPresetButton = scrolledChild(createModelPresetButton(x, at(44), w));

        this.modelField = new TextFieldWidget(this.textRenderer, x, at(80), w, 20, Text.literal("Model Name"));
        this.modelField.setMaxLength(128);
        this.modelField.setText(this.draft.modelName);
        this.modelField.setPlaceholder(Text.literal("Enter model name..."));
        scrolledChild(this.modelField);

        this.apiUrlField = new TextFieldWidget(this.textRenderer, x, at(132), w, 20, Text.literal("API URL"));
        this.apiUrlField.setMaxLength(256);
        this.apiUrlField.setText(this.draft.apiUrl);
        scrolledChild(this.apiUrlField);

        this.apiKeyField = new TextFieldWidget(this.textRenderer, x, at(176), w, 20, Text.literal("API Key"));
        this.apiKeyField.setMaxLength(512);
        this.apiKeyField.setText(this.draft.apiKey);
        this.apiKeyField.setPlaceholder(Text.literal("sk-..."));
        this.apiKeyField.setRenderTextProvider(this::maskApiKey); // 1.21.4 name of addFormatter
        scrolledChild(this.apiKeyField);

        this.keepAliveButton = scrolledChild(CyclingButtonWidget.builder((Integer m) -> Text.literal(keepAliveLabel(m)))
            .values(KEEP_ALIVE)
            .initially(KEEP_ALIVE.contains(this.draft.keepAliveMinutes) ? this.draft.keepAliveMinutes : 30)
            .build(x, at(176), w, 20, Text.literal("Keep model loaded"), (button, m) -> this.draft.keepAliveMinutes = m));

        this.speedSlider = scrolledChild(new BuildSpeedSlider(x, at(220), w, this.draft.ticksPerBlock));

        this.retryButton = scrolledChild(CyclingButtonWidget.builder((Integer n) -> Text.literal(n == 0 ? "off" : n + " time(s)"))
            .values(List.of(0, 1, 2, 3, 4, 5))
            .initially(this.draft.autoRetries)
            .build(x, at(244), w, 20, Text.literal("Auto-fix failed scripts"), (button, n) -> this.draft.autoRetries = n));

        this.forbiddenBlocksButton = scrolledChild(ButtonWidget.builder(forbiddenBlocksLabel(), button -> openForbiddenBlocksScreen())
            .dimensions(x, at(296), w, 20)
            .build());

        this.debugToggle = scrolledChild(CyclingButtonWidget.onOffBuilder(this.draft.debugLogEnabled)
            .build(x, at(320), w, 20, Text.literal("Debug Log"), (button, enabled) -> this.draft.debugLogEnabled = enabled));

        this.webSearchToggle = scrolledChild(CyclingButtonWidget.onOffBuilder(this.draft.openAiWebSearchEnabled)
            .build(x, at(344), w, 20, Text.literal("OpenAI Web Search (default)"), (button, enabled) -> this.draft.openAiWebSearchEnabled = enabled));

        int buttonY = this.panelY + this.panelHeight - 26;
        int buttonWidth = (w - 8) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveSettings())
            .dimensions(x, buttonY, buttonWidth, 20)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
                this.saved = true; // discard
                this.client.setScreen(this.parent);
            })
            .dimensions(x + buttonWidth + 8, buttonY, buttonWidth, 20)
            .build());

        updateVisibility();
    }

    /** Provider-dependent rows, and anything scrolled outside the visible area, are hidden. */
    private void updateVisibility() {
        boolean ollama = this.draft.provider == AiProvider.OLLAMA;
        boolean openAi = this.draft.provider == AiProvider.OPENAI;
        for (ClickableWidget widget : this.scrolled) {
            boolean shown = widget.getY() >= viewTop() && widget.getY() + widget.getHeight() <= viewBottom();
            if (widget == this.apiKeyField) {
                shown &= !ollama;
            } else if (widget == this.keepAliveButton) {
                shown &= ollama;
            } else if (widget == this.webSearchToggle) {
                shown &= openAi;
            }
            widget.visible = shown;
            widget.active = shown;
        }
    }

    private void switchProvider(AiProvider provider) {
        AiProvider previous = this.draft.provider; // the button already shows the new one
        syncDraft();
        if (previous != null) {
            this.draft.modelByProvider.put(previous.name(), this.draft.modelName);
        }
        this.draft.provider = provider;
        this.draft.apiUrl = provider.getDefaultApiUrl();
        String remembered = this.draft.modelByProvider.get(provider.name());
        this.draft.modelName = remembered != null && !remembered.isBlank() ? remembered : getModelPresets(provider).get(0);
        this.clearChildren();
        this.init();
    }

    /** Copy what is on screen into the draft (no-op before the widgets exist). */
    private void syncDraft() {
        if (this.modelField == null) {
            return;
        }
        this.draft.provider = this.providerButton.getValue();
        this.draft.modelName = this.modelField.getText().trim();
        this.draft.apiUrl = this.apiUrlField.getText().trim();
        if (this.draft.provider != AiProvider.OLLAMA) {
            this.draft.apiKey = this.apiKeyField.getText().trim();
        }
        this.draft.keepAliveMinutes = this.keepAliveButton.getValue();
        this.draft.ticksPerBlock = this.speedSlider.getTicks();
        this.draft.autoRetries = this.retryButton.getValue();
        this.draft.debugLogEnabled = this.debugToggle.getValue();
        this.draft.openAiWebSearchEnabled = this.webSearchToggle.getValue();
    }

    private CyclingButtonWidget<String> createModelPresetButton(int x, int y, int width) {
        List<String> presets = new ArrayList<>(getModelPresets(this.draft.provider));
        // Show the model actually in use, even when it is not one of the presets.
        if (this.draft.modelName != null && !this.draft.modelName.isBlank() && !presets.contains(this.draft.modelName)) {
            presets.add(0, this.draft.modelName);
        }
        return CyclingButtonWidget.builder((String model) -> Text.literal(model))
            .values(presets)
            .initially(presets.contains(this.draft.modelName) ? this.draft.modelName : presets.get(0))
            .build(x, y, width, 20, Text.literal("Model Preset"), (button, model) -> this.modelField.setText(model));
    }

    private List<String> getModelPresets(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> OLLAMA_MODELS;
            case OPENAI -> OPENAI_MODELS;
            case CUSTOM -> List.of("custom-model");
        };
    }

    private static String keepAliveLabel(int minutes) {
        if (minutes < 0) {
            return "always (until Ollama stops)";
        }
        return minutes >= 60 ? (minutes / 60) + " h" : minutes + " min";
    }

    private OrderedText maskApiKey(String text, int firstCharacterIndex) {
        return OrderedText.styledForwardsVisitedString("•".repeat(text.length()), Style.EMPTY);
    }

    private Text forbiddenBlocksLabel() {
        int count = this.draft.forbiddenBlocks == null ? 0 : this.draft.forbiddenBlocks.size();
        return Text.literal("Forbidden Blocks (" + count + ")");
    }

    private void openForbiddenBlocksScreen() {
        if (this.client == null) {
            return;
        }
        syncDraft();
        this.client.setScreen(new ForbiddenBlocksScreen(this, this.draft.forbiddenBlocks, updated -> {
            this.draft.forbiddenBlocks = new ArrayList<>(updated);
            if (this.forbiddenBlocksButton != null) {
                this.forbiddenBlocksButton.setMessage(forbiddenBlocksLabel());
            }
        }));
    }

    private void saveSettings() {
        syncDraft();
        AiBuilderConfig config = AiBuilderConfig.get();
        config.applyFrom(
            this.draft.provider,
            this.draft.modelName,
            this.draft.apiUrl,
            this.draft.apiKey,
            this.draft.ticksPerBlock,
            this.draft.forbiddenBlocksPath,
            this.draft.forbiddenBlocks,
            this.draft.debugLogEnabled,
            this.draft.openAiWebSearchEnabled
        );
        config.autoRetries = this.draft.autoRetries;
        config.keepAliveMinutes = this.draft.keepAliveMinutes;
        config.modelByProvider = new java.util.HashMap<>(this.draft.modelByProvider);
        AiBuilderConfig.save();
        AiClientNetworking.sendSettingsSync(AiBuilderConfig.get());
        AiClientState.addLog("[Settings] Saved. Model: " + config.modelName);
        this.saved = true;

        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** Esc keeps the changes, like vanilla option screens. */
    @Override
    public void close() {
        if (!this.saved) {
            saveSettings();
            return;
        }
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0D && maxScroll() > 0) {
            int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(verticalAmount) * 24));
            if (next != this.scroll) {
                syncDraft();
                this.scroll = next;
                this.clearChildren();
                this.init();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
        ctx.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + this.panelHeight, 0xF0202027);
        ctx.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + 28, 0xFF2E3744);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + PANEL_WIDTH / 2, this.panelY + 9, 0xFFFFFFFF);

        int x = this.panelX + 16;
        ctx.enableScissor(this.panelX, viewTop(), this.panelX + PANEL_WIDTH, viewBottom());
        label(ctx, "1. AI Select", x, at(6), SECTION_COLOR);
        label(ctx, "Model Name", x, at(68), LABEL_COLOR);
        label(ctx, "2. Connection Settings", x, at(108), SECTION_COLOR);
        label(ctx, "API URL", x, at(120), LABEL_COLOR);
        if (this.draft.provider == AiProvider.OLLAMA) {
            label(ctx, "Keep the model in memory between builds", x, at(164), LABEL_COLOR);
        } else {
            label(ctx, "API Key", x, at(164), LABEL_COLOR);
        }
        label(ctx, "3. Building", x, at(204), SECTION_COLOR);
        label(ctx, "4. General & Security", x, at(272), SECTION_COLOR);
        label(ctx, "Click to pick blocks from icon list", x, at(284), LABEL_COLOR);
        if (this.draft.provider == AiProvider.OPENAI) {
            label(ctx, "Web search: gpt-4o / gpt-4.1 / gpt-5 only", x, at(368), LABEL_COLOR);
        }
        ctx.disableScissor();

        super.render(ctx, mouseX, mouseY, delta);

        if (maxScroll() > 0) {
            int trackTop = viewTop() + 2, trackBottom = viewBottom() - 2;
            int barH = Math.max(12, (trackBottom - trackTop) * (this.panelHeight - HEADER - FOOTER) / CONTENT_HEIGHT);
            int barY = trackTop + (trackBottom - trackTop - barH) * this.scroll / maxScroll();
            ctx.fill(this.panelX + PANEL_WIDTH - 6, trackTop, this.panelX + PANEL_WIDTH - 3, trackBottom, 0x40FFFFFF);
            ctx.fill(this.panelX + PANEL_WIDTH - 6, barY, this.panelX + PANEL_WIDTH - 3, barY + barH, 0xC0FFFFFF);
        }
    }

    private void label(DrawContext ctx, String text, int x, int y, int color) {
        ctx.drawText(this.textRenderer, Text.literal(text), x, y, color, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }

    private static final class BuildSpeedSlider extends SliderWidget {

        BuildSpeedSlider(int x, int y, int width, int ticks) {
            super(x, y, width, 20, Text.literal(""), normalize(ticks));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Build Speed: " + getTicks() + " ticks/block"));
        }

        @Override
        protected void applyValue() {
            updateMessage();
        }

        int getTicks() {
            return (int) Math.round(this.value * 19.0D) + 1;
        }

        private static double normalize(int ticks) {
            return (Math.clamp(ticks, 1, 20) - 1) / 19.0D;
        }
    }
}
