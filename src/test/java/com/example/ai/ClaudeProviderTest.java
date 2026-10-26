package com.example.ai;

import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeProviderTest {

    @Test
    void messagesEndpoint() {
        assertEquals("https://api.anthropic.com/v1/messages", AiApiEndpoints.resolveAnthropicMessagesUrl("https://api.anthropic.com"));
        assertEquals("https://api.anthropic.com/v1/messages", AiApiEndpoints.resolveAnthropicMessagesUrl("https://api.anthropic.com/v1/"));
        assertEquals("https://api.anthropic.com/v1/messages", AiApiEndpoints.resolveAnthropicMessagesUrl("https://api.anthropic.com/v1/messages"));
        assertEquals("https://api.anthropic.com/v1/messages", AiApiEndpoints.resolveAnthropicMessagesUrl(""));
    }

    @Test
    void claudeFiveModelsThinkAtMediumEffortWithRoomForTheScript() {
        assertTrue(AiHttpService.thinksByDefault("claude-sonnet-5-5"));
        assertTrue(AiHttpService.thinksByDefault("claude-haiku-5-5"));
        assertTrue(AiHttpService.thinksByDefault("claude-fable-5-1"));
        assertTrue(AiHttpService.thinksByDefault("claude-opus-6"));
        assertFalse(AiHttpService.thinksByDefault("claude-sonnet-4-5"));
        assertFalse(AiHttpService.thinksByDefault("claude-3-5-haiku-latest"));
        assertFalse(AiHttpService.thinksByDefault("gpt-5"));
        assertTrue(AiHttpService.ADAPTIVE_MAX_TOKENS > 16000);
        assertEquals(64000, AiHttpService.maxTokensFor("claude-haiku-5-5"), "Haiku gets room for big buildings");
        assertEquals(32000, AiHttpService.maxTokensFor("claude-sonnet-5-5"));
    }

    @Test
    void requestCarriesKeyVersionAndModel() {
        AiBuilderConfig config = AiBuilderConfig.get();
        AiProvider before = config.provider;
        String key = config.apiKey;
        try {
            config.provider = AiProvider.CLAUDE;
            config.apiUrl = "https://api.anthropic.com";
            config.apiKey = "sk-ant-test";
            config.modelName = "claude-haiku-5-5";
            HttpRequest request = AiHttpService.buildRequest(new AiPrompt("fixed instructions", "Object request: apple"), true);
            assertEquals("https://api.anthropic.com/v1/messages", request.uri().toString());
            assertEquals("sk-ant-test", request.headers().firstValue("x-api-key").orElse(""));
            assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElse(""));
            assertTrue(request.headers().firstValue("Authorization").isEmpty(), "Claude does not use a Bearer header");
            config.apiKey = "";
            assertThrows(IllegalStateException.class, () -> AiHttpService.buildRequest(new AiPrompt("a", "b"), false));
        } finally {
            config.provider = before;
            config.apiKey = key;
        }
    }

    @Test
    void picturesGoBeforeTheText() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0 };
        AiImage file = AiImage.ofBytes(png);
        assertEquals("image/png", file.mediaType());
        assertThrows(IllegalArgumentException.class, () -> AiImage.ofBytes(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 }));
        AiImage web = AiImage.ofUrl(" https://example.org/police.jpg ");
        var claude = AiHttpService.imageContentForTests("Describe it", List.of(web, file), true);
        assertEquals(3, claude.size());
        assertEquals("url", claude.get(0).getAsJsonObject().getAsJsonObject("source").get("type").getAsString());
        assertEquals("https://example.org/police.jpg", claude.get(0).getAsJsonObject().getAsJsonObject("source").get("url").getAsString());
        assertEquals("base64", claude.get(1).getAsJsonObject().getAsJsonObject("source").get("type").getAsString());
        assertEquals("Describe it", claude.get(2).getAsJsonObject().get("text").getAsString());
        var openAi = AiHttpService.imageContentForTests("Describe it", List.of(file), false);
        assertTrue(openAi.get(0).getAsJsonObject().getAsJsonObject("image_url").get("url").getAsString().startsWith("data:image/png;base64,"));

        AiBuilderConfig config = AiBuilderConfig.get();
        AiProvider before = config.provider;
        try {
            config.provider = AiProvider.OLLAMA;
            assertThrows(IllegalStateException.class, () -> AiHttpService.buildRequest(new AiPrompt("a", "b"), null, List.of(web)),
                "a connection that cannot see pictures says so");
        } finally {
            config.provider = before;
        }
    }

    @Test
    void readsTextSearchesUsageAndErrors() {
        String reply = """
            {"content":[
              {"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"japanese pavilion roof"}},
              {"type":"web_search_tool_result","tool_use_id":"s1","content":[]},
              {"type":"text","text":"ai.addSphere('red_concrete', 0, 0.5, 2, 0.5)\n"},
              {"type":"text","text":"ai.addCone('lime_concrete', 0, 1, 2, 0.1, 0.2)","citations":[]}
            ],"stop_reason":"end_turn",
            "usage":{"input_tokens":120,"output_tokens":80,"cache_read_input_tokens":2000,"cache_creation_input_tokens":0}}
            """;
        String code = AiHttpService.extractResponseBody(reply, AiProvider.CLAUDE);
        assertTrue(code.startsWith("ai.addSphere") && code.contains("ai.addCone"), code);
        assertEquals(List.of("japanese pavilion roof"), AiHttpService.extractWebSearchQueries(reply, AiProvider.CLAUDE));
        assertArrayEquals(new int[] { 120, 80, 2000, 0 }, AiHttpService.usage(reply));
        assertArrayEquals(new int[] { 10, 5, 0, 0 }, AiHttpService.usage("{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"));

        String error = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AiHttpService.extractResponseBody(error, AiProvider.CLAUDE));
        assertTrue(e.getMessage().contains("invalid x-api-key"), e.getMessage());
    }

    @Test
    void promptParts() {
        assertEquals("b", new AiPrompt("", "b").full());
        assertEquals("a\n\nb", new AiPrompt("a", "b").full());
        AiPrompt object = AiController.objectPrompt("빨간 사과");
        assertTrue(object.user().contains("빨간 사과"));
        assertFalse(object.system().contains("빨간 사과"), "the fixed part does not change with the request (so it can be cached)");
        assertTrue(object.system().contains("ai.addTube") && object.system().contains("ai.mirrorOn"));
        AiPrompt fixed = AiController.fixPrompt(object, "ai.foo()", "no such function");
        assertEquals(object.system(), fixed.system());
        assertTrue(fixed.user().contains("YOUR PREVIOUS SCRIPT FAILED") && fixed.user().contains("ai.foo()"));
        assertFalse(AiController.buildingPrompt("집", "flat", false).system().contains("Web search"));
        assertTrue(AiController.buildingPrompt("집", "flat", true).system().contains("Web search is ON"));
    }
}
