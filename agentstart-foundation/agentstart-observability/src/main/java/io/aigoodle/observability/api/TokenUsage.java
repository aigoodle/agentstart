package io.aigoodle.observability.api;

/**
 * Token counts for one LLM call.
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public static TokenUsage of(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        int resolvedPromptTokens = promptTokens == null ? 0 : promptTokens;
        int resolvedCompletionTokens = completionTokens == null ? 0 : completionTokens;
        int resolvedTotalTokens = totalTokens == null ? resolvedPromptTokens + resolvedCompletionTokens : totalTokens;
        return new TokenUsage(resolvedPromptTokens, resolvedCompletionTokens, resolvedTotalTokens);
    }
}
