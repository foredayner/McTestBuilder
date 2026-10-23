package com.example.config;

public enum AiProvider {
    OLLAMA("Ollama (Local)"),
    OPENAI("OpenAI API"),
    CLAUDE("Claude (Anthropic)"),
    CUSTOM("Custom EndPoint");

    private final String displayName;

    AiProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresApiKey() {
        return this == OPENAI || this == CLAUDE;
    }

    /** OpenAI (Responses API web_search) and Claude (Messages API web_search tool). */
    public boolean supportsWebSearch() {
        return this == OPENAI || this == CLAUDE;
    }

    public String getDefaultApiUrl() {
        return switch (this) {
            case OLLAMA -> "http://localhost:11434";
            case OPENAI -> "https://api.openai.com";
            case CLAUDE -> "https://api.anthropic.com";
            case CUSTOM -> "http://localhost:11434";
        };
    }
}
