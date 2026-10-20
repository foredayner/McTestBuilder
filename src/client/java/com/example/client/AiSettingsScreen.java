package com.example.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

public class AiSettingsScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int LABEL_COLOR = 0xFFCCCCCC;
    private static final int SECTION_COLOR = 0xFF88AAFF;

    private static final List<String> OLLAMA_MODELS = List.of(
        "gemma2:2b", "llama3", "llama3.2", "mistral", "qwen2.5", "codellama"
    );
    private static final List<String> OPENAI_MODELS = List.of(
        "gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-5", "gpt-4-turbo", "gpt-3.5-turbo"
    );

    private final Screen parent;
    private AiBuilderConfig draft;

    private int panelX;
    private int panelY;
    private int panelHeight;

    private CyclingButtonWidget<AiProvider> providerButton;
    private CyclingButtonWidget<String> modelPresetButton;
    private TextFieldWidget modelField;
    private TextFieldWidget apiUrlField;
    private TextFieldWidget apiKeyField;
    private BuildSpeedSlider speedSlider;
    private ButtonWidget forbiddenBlocksButton;
    private CyclingButtonWidget<Boolean> debugToggle;
    private CyclingButtonWidget<Boolean> webSearchToggle;

    public AiSettingsScreen(Screen parent) {
        super(Text.literal("Minecraft AI Build Assistant Settings"));
        this.parent = parent;
        this.draft = AiBuilderConfig.get().copy();
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        buildWidgets();
        updateConnectionFields();
    }

    private void layoutPanel() {
        this.panelHeight = Math.min(this.height - 40, 450);
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
    }

    private void buildWidgets() {
        int x = this.panelX + 16;
        int fieldWidth = PANEL_WIDTH - 32;
        int presetY = this.panelY + 78;

        this.providerButton = CyclingButtonWidget.builder(
                (AiProvider provider) -> Text.literal(provider.getDisplayName())
            )
            .initially(this.draft.provider)
            .values(Arrays.asList(AiProvider.values()))
            .build(x, this.panelY + 52, fieldWidth, 20, Text.literal("Provider"), (button, provider) -> {
                this.draft.provider = provider;
                this.draft.apiUrl = provider.getDefaultApiUrl();
                this.apiUrlField.setText(this.draft.apiUrl);
                rebuildModelPresetButton(x, presetY, fieldWidth);
                updateConnectionFields();
                updateWebSearchToggle();
            });
        this.addDrawableChild(this.providerButton);

        this.modelPresetButton = createModelPresetButton(x, presetY, fieldWidth);
        this.addDrawableChild(this.modelPresetButton);

        this.modelField = new TextFieldWidget(this.textRenderer, x, this.panelY + 116, fieldWidth, 20, Text.literal("Model Name"));
        this.modelField.setMaxLength(128);
        this.modelField.setText(this.draft.modelName);
        this.modelField.setPlaceholder(Text.literal("Enter model name..."));
        this.addSelectableChild(this.modelField);

        this.apiUrlField = new TextFieldWidget(this.textRenderer, x, this.panelY + 164, fieldWidth, 20, Text.literal("API URL"));
        this.apiUrlField.setMaxLength(256);
        this.apiUrlField.setText(this.draft.apiUrl);
        this.addSelectableChild(this.apiUrlField);

        this.apiKeyField = new TextFieldWidget(this.textRenderer, x, this.panelY + 212, fieldWidth, 20, Text.literal("API Key"));
        this.apiKeyField.setMaxLength(512);
        this.apiKeyField.setText(this.draft.apiKey);
        this.apiKeyField.setPlaceholder(Text.literal("sk-..."));
        this.apiKeyField.setRenderTextProvider(this::maskApiKey); // 1.21.4 name of addFormatter
        this.addSelectableChild(this.apiKeyField);

        this.speedSlider = new BuildSpeedSlider(x, this.panelY + 248, fieldWidth, this.draft.ticksPerBlock);
        this.addDrawableChild(this.speedSlider);

        this.forbiddenBlocksButton = ButtonWidget.builder(forbiddenBlocksLabel(), button -> openForbiddenBlocksScreen())
            .dimensions(x, this.panelY + 292, fieldWidth, 20)
            .build();
        this.addDrawableChild(this.forbiddenBlocksButton);

        this.debugToggle = CyclingButtonWidget.onOffBuilder(this.draft.debugLogEnabled)
            .build(x, this.panelY + 328, fieldWidth, 20, Text.literal("Debug Log"), (button, enabled) -> this.draft.debugLogEnabled = enabled);
        this.addDrawableChild(this.debugToggle);

        this.webSearchToggle = CyclingButtonWidget.onOffBuilder(this.draft.openAiWebSearchEnabled)
            .build(x, this.panelY + 356, fieldWidth, 20, Text.literal("OpenAI Web Search (default)"), (button, enabled) -> this.draft.openAiWebSearchEnabled = enabled);
        this.addDrawableChild(this.webSearchToggle);
        updateWebSearchToggle();

        int buttonY = this.panelY + this.panelHeight - 28;
        int buttonWidth = (fieldWidth - 8) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveSettings())
            .dimensions(x, buttonY, buttonWidth, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.client.setScreen(this.parent))
            .dimensions(x + buttonWidth + 8, buttonY, buttonWidth, 20)
            .build());
    }

    private CyclingButtonWidget<String> createModelPresetButton(int x, int y, int width) {
        List<String> presets = getModelPresets(this.draft.provider);
        String initial = presets.contains(this.draft.modelName) ? this.draft.modelName : presets.get(0);

        return CyclingButtonWidget.builder((String model) -> Text.literal(model))
            .values(presets)
            .initially(initial)
            .build(x, y, width, 20, Text.literal("Model Preset"), (button, model) -> this.modelField.setText(model));
    }

    private void rebuildModelPresetButton(int x, int y, int width) {
        if (this.modelPresetButton != null) {
            this.remove(this.modelPresetButton);
        }

        List<String> presets = getModelPresets(this.draft.provider);
        if (!presets.contains(this.modelField.getText())) {
            this.modelField.setText(presets.get(0));
        }

        this.modelPresetButton = createModelPresetButton(x, y, width);
        this.addDrawableChild(this.modelPresetButton);
    }

    private List<String> getModelPresets(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> OLLAMA_MODELS;
            case OPENAI -> OPENAI_MODELS;
            case CUSTOM -> List.of("custom-model");
        };
    }

    private OrderedText maskApiKey(String text, int firstCharacterIndex) {
        return OrderedText.styledForwardsVisitedString("•".repeat(text.length()), Style.EMPTY);
    }

    private void updateConnectionFields() {
        boolean showApiKey = this.draft.provider != AiProvider.OLLAMA;
        this.apiKeyField.visible = showApiKey;
        this.apiKeyField.active = showApiKey;
    }

    private Text forbiddenBlocksLabel() {
        int count = this.draft.forbiddenBlocks == null ? 0 : this.draft.forbiddenBlocks.size();
        return Text.literal("Forbidden Blocks (" + count + ")");
    }

    private void openForbiddenBlocksScreen() {
        if (this.client == null) {
            return;
        }

        this.client.setScreen(new ForbiddenBlocksScreen(this, this.draft.forbiddenBlocks, updated -> {
            this.draft.forbiddenBlocks = new ArrayList<>(updated);
            if (this.forbiddenBlocksButton != null) {
                this.forbiddenBlocksButton.setMessage(forbiddenBlocksLabel());
            }
        }));
    }

    private void updateWebSearchToggle() {
        boolean openAi = this.draft.provider == AiProvider.OPENAI;
        this.webSearchToggle.visible = openAi;
        this.webSearchToggle.active = openAi;
    }

    private void saveSettings() {
        String apiKey = this.draft.provider == AiProvider.OLLAMA
            ? this.draft.apiKey
            : this.apiKeyField.getText();
        this.draft.applyFrom(
            this.providerButton.getValue(),
            this.modelField.getText(),
            this.apiUrlField.getText(),
            apiKey,
            this.speedSlider.getTicks(),
            this.draft.forbiddenBlocksPath,
            this.draft.forbiddenBlocks,
            this.debugToggle.getValue(),
            this.webSearchToggle.getValue()
        );

        AiBuilderConfig.get().applyFrom(
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
        AiBuilderConfig.save();
        AiClientNetworking.sendSettingsSync(AiBuilderConfig.get());
        AiClientState.addLog("[Settings] Saved.");

        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        layoutPanel();

        drawContext.fill(0, 0, this.width, this.height, 0x80000000);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + this.panelHeight, 0xF0202027);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + 28, 0xFF2E3744);
        drawContext.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + PANEL_WIDTH / 2, this.panelY + 9, 0xFFFFFFFF);

        drawSectionLabel(drawContext, "1. AI Select", this.panelX + 16, this.panelY + 40);
        drawSectionLabel(drawContext, "2. Connection Settings", this.panelX + 16, this.panelY + 144);
        drawSectionLabel(drawContext, "3. General & Security", this.panelX + 16, this.panelY + 236);

        super.render(drawContext, mouseX, mouseY, delta);

        renderFieldLabel(drawContext, "Model Name", this.panelX + 16, this.panelY + 104);
        renderFieldLabel(drawContext, "API URL", this.panelX + 16, this.panelY + 156);
        if (this.draft.provider != AiProvider.OLLAMA) {
            renderFieldLabel(drawContext, "API Key", this.panelX + 16, this.panelY + 200);
        }
        if (this.draft.provider == AiProvider.OPENAI && this.webSearchToggle != null && this.webSearchToggle.visible) {
            renderFieldLabel(drawContext, "Web search: gpt-4o / gpt-4.1 / gpt-5 only", this.panelX + 16, this.panelY + 378);
        }
        renderFieldLabel(drawContext, "Click to pick blocks from icon list", this.panelX + 16, this.panelY + 280);

        if (this.modelField != null) {
            this.modelField.render(drawContext, mouseX, mouseY, delta);
        }
        if (this.apiUrlField != null) {
            this.apiUrlField.render(drawContext, mouseX, mouseY, delta);
        }
        if (this.apiKeyField != null && this.apiKeyField.visible) {
            this.apiKeyField.render(drawContext, mouseX, mouseY, delta);
        }
    }

    private void drawSectionLabel(DrawContext drawContext, String text, int x, int y) {
        drawContext.drawText(this.textRenderer, Text.literal(text), x, y, SECTION_COLOR, false);
    }

    private void renderFieldLabel(DrawContext drawContext, String text, int x, int y) {
        drawContext.drawText(this.textRenderer, Text.literal(text), x, y, LABEL_COLOR, false);
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
