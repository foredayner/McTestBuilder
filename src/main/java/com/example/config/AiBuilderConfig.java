package com.example.config;

import com.example.AiBuildAssistantMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AiBuilderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Path.of("config", "ai_builder");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.json");

    private static AiBuilderConfig instance = new AiBuilderConfig();

    public AiProvider provider = AiProvider.OLLAMA;
    public String modelName = "qwen2.5-coder:7b";
    public String apiUrl = "http://localhost:11434";
    public String apiKey = "";
    public int ticksPerBlock = 5;
    public String forbiddenBlocksPath = "config/ai_builder/forbidden_blocks.txt";
    public List<String> forbiddenBlocks = new ArrayList<>(ForbiddenBlockRegistry.defaultBlocks());
    public boolean debugLogEnabled = false;
    public boolean openAiWebSearchEnabled = false;
    /** How many times a failing script is sent back to the AI to be fixed (0 = off). */
    public int autoRetries = 2;
    /** Ollama: minutes to keep the model loaded after a request (-1 = until Ollama stops). */
    public int keepAliveMinutes = 30;
    /** Last model typed for each provider, so switching provider does not lose it. */
    public java.util.Map<String, String> modelByProvider = new java.util.HashMap<>();

    private AiBuilderConfig() {
    }

    public static AiBuilderConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
                AiBuilderConfig loaded = GSON.fromJson(json, AiBuilderConfig.class);
                if (loaded != null) {
                    loaded.normalize();
                    loaded.ensureForbiddenBlocksLoaded();
                    instance = loaded;
                }
            } else {
                instance = new AiBuilderConfig();
                instance.normalize();
                instance.ensureForbiddenBlocksLoaded();
                save();
            }
        } catch (IOException | JsonSyntaxException exception) {
            AiBuildAssistantMod.LOGGER.error("Failed to load AI builder config, using defaults", exception);
            instance = new AiBuilderConfig();
            instance.normalize();
            instance.ensureForbiddenBlocksLoaded();
        }

        ForbiddenBlockRegistry.reloadFromList(instance.forbiddenBlocks);
        ForbiddenBlockRegistry.writeToFile(instance.forbiddenBlocksPath, instance.forbiddenBlocks);
    }

    public static void save() {
        instance.normalize();
        instance.forbiddenBlocks = ForbiddenBlockRegistry.normalizeList(instance.forbiddenBlocks);
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
            ForbiddenBlockRegistry.reloadFromList(instance.forbiddenBlocks);
            ForbiddenBlockRegistry.writeToFile(instance.forbiddenBlocksPath, instance.forbiddenBlocks);
        } catch (IOException exception) {
            AiBuildAssistantMod.LOGGER.error("Failed to save AI builder config", exception);
        }
    }

    private void ensureForbiddenBlocksLoaded() {
        if (forbiddenBlocks == null || forbiddenBlocks.isEmpty()) {
            forbiddenBlocks = ForbiddenBlockRegistry.loadFromFile(forbiddenBlocksPath);
            return;
        }

        forbiddenBlocks = ForbiddenBlockRegistry.normalizeList(forbiddenBlocks);
        if (forbiddenBlocks.isEmpty()) {
            forbiddenBlocks = new ArrayList<>(ForbiddenBlockRegistry.defaultBlocks());
        }
    }

    public void applyFrom(
        AiProvider provider,
        String modelName,
        String apiUrl,
        String apiKey,
        int ticksPerBlock,
        String forbiddenBlocksPath,
        List<String> forbiddenBlocks,
        boolean debugLogEnabled,
        boolean openAiWebSearchEnabled
    ) {
        this.provider = provider == null ? AiProvider.OLLAMA : provider;
        this.modelName = modelName == null || modelName.isBlank() ? "qwen2.5-coder:7b" : modelName.trim();
        this.apiUrl = apiUrl == null || apiUrl.isBlank() ? this.provider.getDefaultApiUrl() : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.ticksPerBlock = Math.clamp(ticksPerBlock, 1, 20);
        this.forbiddenBlocksPath = forbiddenBlocksPath == null || forbiddenBlocksPath.isBlank()
            ? "config/ai_builder/forbidden_blocks.txt"
            : forbiddenBlocksPath.trim();
        this.forbiddenBlocks = ForbiddenBlockRegistry.normalizeList(forbiddenBlocks);
        if (this.forbiddenBlocks.isEmpty()) {
            this.forbiddenBlocks = new ArrayList<>(ForbiddenBlockRegistry.defaultBlocks());
        }
        this.debugLogEnabled = debugLogEnabled;
        this.openAiWebSearchEnabled = openAiWebSearchEnabled;
        normalize();
    }

    public void applyFrom(
        AiProvider provider,
        String modelName,
        String apiUrl,
        String apiKey,
        int ticksPerBlock,
        String forbiddenBlocksPath,
        boolean debugLogEnabled,
        boolean openAiWebSearchEnabled
    ) {
        applyFrom(
            provider,
            modelName,
            apiUrl,
            apiKey,
            ticksPerBlock,
            forbiddenBlocksPath,
            forbiddenBlocks,
            debugLogEnabled,
            openAiWebSearchEnabled
        );
    }

    public void applyFrom(
        AiProvider provider,
        String modelName,
        String apiUrl,
        String apiKey,
        int ticksPerBlock,
        String forbiddenBlocksPath,
        boolean debugLogEnabled
    ) {
        applyFrom(provider, modelName, apiUrl, apiKey, ticksPerBlock, forbiddenBlocksPath, forbiddenBlocks, debugLogEnabled, openAiWebSearchEnabled);
    }

    public AiBuilderConfig copy() {
        AiBuilderConfig copy = new AiBuilderConfig();
        copy.applyFrom(
            provider,
            modelName,
            apiUrl,
            apiKey,
            ticksPerBlock,
            forbiddenBlocksPath,
            forbiddenBlocks,
            debugLogEnabled,
            openAiWebSearchEnabled
        );
        copy.autoRetries = autoRetries;
        copy.keepAliveMinutes = keepAliveMinutes;
        copy.modelByProvider = new java.util.HashMap<>(modelByProvider);
        return copy;
    }

    private void normalize() {
        if (provider == null) {
            provider = AiProvider.OLLAMA;
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "qwen2.5-coder:7b";
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = provider.getDefaultApiUrl();
        }
        if (apiKey == null) {
            apiKey = "";
        }
        ticksPerBlock = Math.clamp(ticksPerBlock, 1, 20);
        autoRetries = Math.clamp(autoRetries, 0, 5);
        keepAliveMinutes = Math.clamp(keepAliveMinutes, -1, 1440);
        if (modelByProvider == null) {
            modelByProvider = new java.util.HashMap<>();
        }
        modelByProvider.put(provider.name(), modelName);
        if (forbiddenBlocksPath == null || forbiddenBlocksPath.isBlank()) {
            forbiddenBlocksPath = "config/ai_builder/forbidden_blocks.txt";
        }
    }
}
