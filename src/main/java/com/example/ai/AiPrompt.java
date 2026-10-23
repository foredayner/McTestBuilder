package com.example.ai;

/**
 * What is sent to the AI: a fixed part (instructions, API, examples) and the part that changes
 * with each request (the request, the terrain, a failed script). Claude gets the fixed part as a
 * cached system prompt; other providers get both joined into one text.
 */
public record AiPrompt(String system, String user) {

    public String full() {
        return system == null || system.isBlank() ? user : system + "\n\n" + user;
    }
}
