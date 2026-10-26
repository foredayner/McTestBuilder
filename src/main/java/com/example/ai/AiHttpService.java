package com.example.ai;

import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class AiHttpService {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private AiHttpService() {
    }

    public static HttpRequest buildRequest(String promptText) {
        return buildRequest(promptText, false);
    }

    public static HttpRequest buildRequest(String promptText, boolean useWebSearch) {
        return buildRequest(new AiPrompt("", promptText), useWebSearch);
    }

    /** While set (by buildRequest with a model), requests use this model instead of the configured one. */
    private static final ThreadLocal<String> MODEL_OVERRIDE = new ThreadLocal<>();

    private static String model(AiBuilderConfig config) {
        String m = MODEL_OVERRIDE.get();
        return m == null || m.isBlank() ? config.modelName : m;
    }

    /** A request with another model of the same provider (e.g. a bigger one for planning). */
    public static HttpRequest buildRequest(AiPrompt prompt, boolean useWebSearch, String model) {
        MODEL_OVERRIDE.set(model);
        try {
            return buildRequest(prompt, useWebSearch);
        } finally {
            MODEL_OVERRIDE.remove();
        }
    }

    /** While set, the user message carries these pictures before its text. */
    private static final ThreadLocal<List<AiImage>> IMAGES = new ThreadLocal<>();

    /** A request with pictures for the AI to look at (Claude and OpenAI connections). */
    public static HttpRequest buildRequest(AiPrompt prompt, String model, List<AiImage> images) {
        AiProvider provider = AiBuilderConfig.get().provider;
        if (images != null && !images.isEmpty() && provider != AiProvider.CLAUDE && provider != AiProvider.OPENAI) {
            throw new IllegalStateException("This AI connection cannot look at pictures; use Claude or OpenAI.");
        }
        IMAGES.set(images);
        try {
            return buildRequest(prompt, false, model);
        } finally {
            IMAGES.remove();
        }
    }

    /** Tests: the user content a request with these pictures carries. */
    static JsonArray imageContentForTests(String text, List<AiImage> images, boolean claude) {
        IMAGES.set(images);
        try {
            return claude ? claudeContent(text) : openAiContent(text);
        } finally {
            IMAGES.remove();
        }
    }

    private static boolean hasImages() {
        List<AiImage> images = IMAGES.get();
        return images != null && !images.isEmpty();
    }

    /** Claude: image blocks, then the text. */
    private static JsonArray claudeContent(String text) {
        JsonArray content = new JsonArray();
        for (AiImage image : IMAGES.get()) {
            JsonObject block = new JsonObject();
            block.addProperty("type", "image");
            JsonObject source = new JsonObject();
            if (image.isUrl()) {
                source.addProperty("type", "url");
                source.addProperty("url", image.url());
            } else {
                source.addProperty("type", "base64");
                source.addProperty("media_type", image.mediaType());
                source.addProperty("data", image.base64());
            }
            block.add("source", source);
            content.add(block);
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text);
        content.add(t);
        return content;
    }

    /** OpenAI chat: image_url parts, then the text. */
    private static JsonArray openAiContent(String text) {
        JsonArray content = new JsonArray();
        for (AiImage image : IMAGES.get()) {
            JsonObject part = new JsonObject();
            part.addProperty("type", "image_url");
            JsonObject url = new JsonObject();
            url.addProperty("url", image.isUrl() ? image.url() : "data:" + image.mediaType() + ";base64," + image.base64());
            part.add("image_url", url);
            content.add(part);
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text);
        content.add(t);
        return content;
    }

    public static HttpRequest buildRequest(AiPrompt prompt, boolean useWebSearch) {
        AiBuilderConfig config = AiBuilderConfig.get();

        return switch (config.provider) {
            case OLLAMA -> buildOllamaRequest(config, prompt.full());
            case OPENAI -> useWebSearch
                ? buildOpenAiResponsesRequest(config, prompt.full())
                : buildOpenAiRequest(config, prompt.full());
            case CLAUDE -> buildClaudeRequest(config, prompt, useWebSearch);
            case CUSTOM -> buildCustomRequest(config, prompt.full());
        };
    }

    /** Most web searches Claude may run for one build. */
    private static final int CLAUDE_MAX_SEARCHES = 3;

    /**
     * Anthropic Messages API. The fixed instructions go in a system prompt marked for caching, so
     * repeated requests (and fix-up retries) pay much less for them.
     */
    private static HttpRequest buildClaudeRequest(AiBuilderConfig config, AiPrompt prompt, boolean useWebSearch) {
        String apiKey = AiApiEndpoints.normalizeApiKey(config.apiKey);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Claude API key is required (Settings > API 키)");
        }
        JsonObject body = new JsonObject();
        String model = model(config);
        boolean adaptive = thinksByDefault(model);
        body.addProperty("model", model);
        // Claude 5 models think before answering by default, and thinking counts against max_tokens:
        // think at medium effort and leave room for a whole building's script after it.
        body.addProperty("max_tokens", adaptive ? maxTokensFor(model) : 16000);
        if (adaptive) {
            JsonObject output = new JsonObject();
            output.addProperty("effort", "medium");
            body.add("output_config", output);
        }
        if (prompt.system() != null && !prompt.system().isBlank()) {
            JsonArray system = new JsonArray();
            JsonObject block = new JsonObject();
            block.addProperty("type", "text");
            block.addProperty("text", prompt.system());
            JsonObject cache = new JsonObject();
            cache.addProperty("type", "ephemeral");
            block.add("cache_control", cache);
            system.add(block);
            body.add("system", system);
        }
        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        if (hasImages()) {
            user.add("content", claudeContent(prompt.user()));
        } else {
            user.addProperty("content", prompt.user());
        }
        messages.add(user);
        body.add("messages", messages);
        if (useWebSearch) {
            JsonArray tools = new JsonArray();
            JsonObject search = new JsonObject();
            search.addProperty("type", "web_search_20250305");
            search.addProperty("name", "web_search");
            search.addProperty("max_uses", CLAUDE_MAX_SEARCHES);
            tools.add(search);
            body.add("tools", tools);
        }
        return HttpRequest.newBuilder()
            .uri(URI.create(AiApiEndpoints.resolveAnthropicMessagesUrl(config.apiUrl)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .timeout(Duration.ofSeconds(adaptive ? 600 : 300))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
    }

    /** Output room (thinking plus text) for models that think by default. */
    static final int ADAPTIVE_MAX_TOKENS = 32000;
    /** Haiku is cheap and quick: give it room for a big building's whole script. */
    static final int HAIKU_MAX_TOKENS = 64000;

    static int maxTokensFor(String model) {
        return model != null && model.toLowerCase(java.util.Locale.ROOT).contains("haiku") ? HAIKU_MAX_TOKENS : ADAPTIVE_MAX_TOKENS;
    }

    /** Claude 5 models (and later) think by default and take an effort level. */
    static boolean thinksByDefault(String model) {
        if (model == null) {
            return false;
        }
        var m = java.util.regex.Pattern.compile("claude-(opus|sonnet|haiku|fable|mythos)-(\\d+)").matcher(model.toLowerCase(java.util.Locale.ROOT));
        return m.find() && Integer.parseInt(m.group(2)) >= 5;
    }

    /** The text of a Claude reply (web search blocks and citations are skipped). */
    private static String extractClaudeContent(JsonObject root) {
        if (!root.has("content") || !root.get("content").isJsonArray()) {
            throw new IllegalStateException("No content in Claude response");
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement element : root.getAsJsonArray("content")) {
            if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                    text.append(block.get("text").getAsString());
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Claude returned no text"
                + (root.has("stop_reason") ? " (stop_reason " + root.get("stop_reason").getAsString() + ")" : ""));
        }
        return text.toString();
    }

    /** Search queries in a reply: OpenAI web_search_call items or Claude server_tool_use blocks. */
    public static List<String> extractWebSearchQueries(String rawBody, AiProvider provider) {
        if (provider != AiProvider.CLAUDE) {
            return extractOpenAiWebSearchQueries(rawBody);
        }
        List<String> queries = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
            if (root.has("content") && root.get("content").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("content")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject block = element.getAsJsonObject();
                    if (block.has("type") && "server_tool_use".equals(block.get("type").getAsString())
                        && block.has("input") && block.get("input").isJsonObject()
                        && block.getAsJsonObject("input").has("query")) {
                        queries.add(block.getAsJsonObject("input").get("query").getAsString());
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return queries;
    }

    /** Tokens used by one reply: {input, output, cache read, cache write}; zeros if not reported. */
    public static int[] usage(String rawBody) {
        int[] out = new int[4];
        try {
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
            if (root.has("usage") && root.get("usage").isJsonObject()) {
                JsonObject u = root.getAsJsonObject("usage");
                out[0] = intOf(u, "input_tokens") + intOf(u, "prompt_tokens");
                out[1] = intOf(u, "output_tokens") + intOf(u, "completion_tokens");
                out[2] = intOf(u, "cache_read_input_tokens");
                out[3] = intOf(u, "cache_creation_input_tokens");
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static int intOf(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : 0;
    }

    public static String extractResponseBody(String rawBody, AiProvider provider) {
        JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();

        JsonObject error = getErrorObject(root);
        if (error != null) {
            throw new IllegalStateException(formatOpenAiError(error));
        }

        return switch (provider) {
            case OLLAMA, CUSTOM -> {
                if (!root.has("response")) {
                    throw new IllegalStateException("No response field in AI response");
                }
                yield root.get("response").getAsString();
            }
            case OPENAI -> root.has("output")
                ? extractOpenAiResponsesContent(root)
                : extractOpenAiMessageContent(root);
            case CLAUDE -> extractClaudeContent(root);
        };
    }

    public static List<String> extractOpenAiWebSearchQueries(String rawBody) {
        List<String> queries = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
            if (!root.has("output")) {
                return queries;
            }

            for (JsonElement element : root.getAsJsonArray("output")) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject item = element.getAsJsonObject();
                if (!item.has("type") || !"web_search_call".equals(item.get("type").getAsString())) {
                    continue;
                }

                if (item.has("action") && item.get("action").isJsonObject()) {
                    JsonObject action = item.getAsJsonObject("action");
                    if (action.has("query") && !action.get("query").isJsonNull()) {
                        queries.add(action.get("query").getAsString());
                    }
                    if (action.has("queries") && action.get("queries").isJsonArray()) {
                        for (JsonElement queryElement : action.getAsJsonArray("queries")) {
                            if (!queryElement.isJsonNull()) {
                                queries.add(queryElement.getAsString());
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }

        return queries;
    }

    public static String extractHttpErrorMessage(String rawBody, int statusCode, AiProvider provider) {
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
                JsonObject error = getErrorObject(root);
                if (error != null) {
                    return formatOpenAiError(error);
                }
            } catch (RuntimeException ignored) {
            }
        }

        String endpoint = resolveEndpointForProvider(provider, AiBuilderConfig.get().apiUrl, false);
        return "HTTP " + statusCode + " from " + endpoint;
    }

    public static String resolveEndpointForProvider(AiProvider provider, String apiUrl) {
        return resolveEndpointForProvider(provider, apiUrl, false);
    }

    public static String resolveEndpointForProvider(AiProvider provider, String apiUrl, boolean useWebSearch) {
        return switch (provider) {
            case OLLAMA -> AiApiEndpoints.resolveOllamaGenerateUrl(apiUrl);
            case OPENAI -> useWebSearch
                ? AiApiEndpoints.resolveOpenAiResponsesUrl(apiUrl)
                : AiApiEndpoints.resolveOpenAiChatCompletionsUrl(apiUrl);
            case CLAUDE -> AiApiEndpoints.resolveAnthropicMessagesUrl(apiUrl);
            case CUSTOM -> apiUrl;
        };
    }

    public static HttpClient client() {
        return CLIENT;
    }

    private static HttpRequest buildOllamaRequest(AiBuilderConfig config, String promptText) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model(config));
        requestJson.addProperty("stream", false);
        requestJson.addProperty("prompt", promptText);
        // Keep the model in memory between builds instead of Ollama's 5-minute default, and give
        // it room for the long build prompt (+ the failed script on a retry).
        if (config.keepAliveMinutes < 0) {
            requestJson.addProperty("keep_alive", -1);
        } else {
            requestJson.addProperty("keep_alive", config.keepAliveMinutes + "m");
        }
        JsonObject options = new JsonObject();
        options.addProperty("num_ctx", 8192);
        requestJson.add("options", options);

        String endpoint = AiApiEndpoints.resolveOllamaGenerateUrl(config.apiUrl);
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
            .build();
    }

    private static HttpRequest buildOpenAiRequest(AiBuilderConfig config, String promptText) {
        String apiKey = AiApiEndpoints.normalizeApiKey(config.apiKey);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is required");
        }

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model(config));

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        if (hasImages()) {
            userMessage.add("content", openAiContent(promptText));
        } else {
            userMessage.addProperty("content", promptText);
        }
        messages.add(userMessage);
        requestJson.add("messages", messages);

        String endpoint = AiApiEndpoints.resolveOpenAiChatCompletionsUrl(config.apiUrl);
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
            .build();
    }

    private static HttpRequest buildOpenAiResponsesRequest(AiBuilderConfig config, String promptText) {
        String apiKey = AiApiEndpoints.normalizeApiKey(config.apiKey);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is required");
        }

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model(config));
        requestJson.addProperty("input", promptText);

        JsonArray tools = new JsonArray();
        JsonObject webSearchTool = new JsonObject();
        webSearchTool.addProperty("type", "web_search");
        tools.add(webSearchTool);
        requestJson.add("tools", tools);

        String endpoint = AiApiEndpoints.resolveOpenAiResponsesUrl(config.apiUrl);
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
            .build();
    }

    private static HttpRequest buildCustomRequest(AiBuilderConfig config, String promptText) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model(config));
        requestJson.addProperty("stream", false);
        requestJson.addProperty("prompt", promptText);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(config.apiUrl))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()));

        String apiKey = AiApiEndpoints.normalizeApiKey(config.apiKey);
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }

    private static String extractOpenAiMessageContent(JsonObject root) {
        if (!root.has("choices")) {
            throw new IllegalStateException("No choices field in OpenAI response");
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("OpenAI response contained no choices");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        if (!firstChoice.has("message")) {
            throw new IllegalStateException("OpenAI response missing message object");
        }

        JsonObject message = firstChoice.getAsJsonObject("message");
        if (!message.has("content")) {
            throw new IllegalStateException("OpenAI response missing message content");
        }

        JsonElement content = message.get("content");
        if (content.isJsonNull()) {
            throw new IllegalStateException("OpenAI response content was null");
        }

        return content.getAsString();
    }

    private static String extractOpenAiResponsesContent(JsonObject root) {
        if (root.has("output_text") && !root.get("output_text").isJsonNull()) {
            return root.get("output_text").getAsString();
        }

        if (!root.has("output") || !root.get("output").isJsonArray()) {
            throw new IllegalStateException("No output field in OpenAI Responses API response");
        }

        StringBuilder content = new StringBuilder();
        for (JsonElement element : root.getAsJsonArray("output")) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            if (!item.has("type") || !"message".equals(item.get("type").getAsString())) {
                continue;
            }

            if (!item.has("content") || !item.get("content").isJsonArray()) {
                continue;
            }

            for (JsonElement part : item.getAsJsonArray("content")) {
                if (!part.isJsonObject()) {
                    continue;
                }

                JsonObject partObject = part.getAsJsonObject();
                if (partObject.has("text") && !partObject.get("text").isJsonNull()) {
                    content.append(partObject.get("text").getAsString());
                }
            }
        }

        if (content.isEmpty()) {
            throw new IllegalStateException("OpenAI Responses API returned no text output");
        }

        return content.toString();
    }

    private static JsonObject getErrorObject(JsonObject root) {
        if (root == null || !root.has("error")) {
            return null;
        }

        JsonElement errorElement = root.get("error");
        if (errorElement.isJsonNull() || !errorElement.isJsonObject()) {
            return null;
        }

        return errorElement.getAsJsonObject();
    }

    private static String formatOpenAiError(JsonObject error) {
        if (error == null) {
            return "Unknown API error";
        }

        StringBuilder message = new StringBuilder();
        if (error.has("message")) {
            message.append(error.get("message").getAsString());
        } else {
            message.append("Unknown API error");
        }

        if (error.has("type") && !error.get("type").isJsonNull()) {
            message.append(" (").append(error.get("type").getAsString()).append(")");
        }

        return message.toString();
    }
}
