package com.example.agents.observability;

import org.springframework.stereotype.Component;

/**
 * Estimates token counts from text content.
 * Uses a simple heuristic: ~4 characters per token for English text.
 * This provides rough estimates when actual usage metadata isn't available.
 */
@Component
public class TokenUsageEstimator {

    private static final double CHARS_PER_TOKEN = 4.0;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public TokenEstimate estimate(String input, String output) {
        int inputTokens = estimateTokens(input);
        int outputTokens = estimateTokens(output);
        return new TokenEstimate(inputTokens, outputTokens, inputTokens + outputTokens);
    }

    public record TokenEstimate(int inputTokens, int outputTokens, int totalTokens) {}
}
