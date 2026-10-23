package com.example.ai;

import java.net.URI;
import java.net.URISyntaxException;

public final class AiApiEndpoints {

    private static final String OPENAI_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String OPENAI_RESPONSES_PATH = "/v1/responses";
    private static final String OLLAMA_GENERATE_PATH = "/api/generate";

    private AiApiEndpoints() {
    }

    public static String resolveOpenAiChatCompletionsUrl(String apiUrl) {
        return resolveEndpoint(apiUrl, OPENAI_CHAT_COMPLETIONS_PATH, "https://api.openai.com");
    }

    public static String resolveOpenAiResponsesUrl(String apiUrl) {
        return resolveEndpoint(apiUrl, OPENAI_RESPONSES_PATH, "https://api.openai.com");
    }

    /** Anthropic Messages API: https://api.anthropic.com -> .../v1/messages (also accepts .../v1). */
    public static String resolveAnthropicMessagesUrl(String apiUrl) {
        String base = apiUrl == null || apiUrl.isBlank() ? "https://api.anthropic.com" : apiUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1/messages")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/messages";
        }
        return base + "/v1/messages";
    }

    public static String resolveOllamaGenerateUrl(String apiUrl) {
        return resolveEndpoint(apiUrl, OLLAMA_GENERATE_PATH, "http://localhost:11434");
    }

    static String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }

        String trimmed = apiKey.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private static String resolveEndpoint(String apiUrl, String requiredSuffix, String defaultBase) {
        String base = sanitizeBaseUrl(apiUrl, defaultBase);
        String suffix = normalizePath(requiredSuffix);
        String currentPath = normalizePath(getPath(base));

        if (pathEndsWith(currentPath, suffix)) {
            return dedupeRepeatedSuffix(base, suffix);
        }

        if (suffix.equals(OPENAI_CHAT_COMPLETIONS_PATH) && pathEndsWith(currentPath, "/chat/completions")) {
            return dedupeRepeatedSuffix(base, "/chat/completions");
        }

        if (suffix.equals(OPENAI_RESPONSES_PATH) && pathEndsWith(currentPath, "/responses")) {
            return dedupeRepeatedSuffix(base, "/responses");
        }

        if (suffix.equals(OPENAI_CHAT_COMPLETIONS_PATH) && pathEndsWith(currentPath, "/v1")) {
            return joinUrl(base, "/chat/completions");
        }

        if (suffix.equals(OPENAI_RESPONSES_PATH) && pathEndsWith(currentPath, "/v1")) {
            return joinUrl(base, "/responses");
        }

        return joinUrl(base, suffix);
    }

    private static String dedupeRepeatedSuffix(String url, String suffix) {
        String normalizedSuffix = normalizePath(suffix);
        String result = trimTrailingSlash(url);
        String lowerPath = normalizePath(getPath(result)).toLowerCase();
        String lowerSuffix = normalizedSuffix.toLowerCase();

        while (lowerPath.endsWith(lowerSuffix + lowerSuffix)) {
            result = result.substring(0, result.length() - normalizedSuffix.length());
            result = trimTrailingSlash(result);
            lowerPath = normalizePath(getPath(result)).toLowerCase();
        }

        return result;
    }

    private static String sanitizeBaseUrl(String apiUrl, String defaultBase) {
        String trimmed = apiUrl == null ? "" : apiUrl.trim();
        if (trimmed.isEmpty()) {
            return trimTrailingSlash(defaultBase);
        }

        trimmed = trimTrailingSlash(trimmed);

        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return trimTrailingSlash(defaultBase);
            }
            return trimmed;
        } catch (IllegalArgumentException exception) {
            return trimTrailingSlash(defaultBase);
        }
    }

    private static String joinUrl(String base, String suffix) {
        return trimTrailingSlash(base) + normalizePath(suffix);
    }

    private static String getPath(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static boolean pathEndsWith(String path, String suffix) {
        String normalizedPath = normalizePath(path).toLowerCase();
        String normalizedSuffix = normalizePath(suffix).toLowerCase();
        return normalizedPath.equals(normalizedSuffix) || normalizedPath.endsWith(normalizedSuffix);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
