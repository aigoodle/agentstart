package io.aigoodle.rag.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record RagPolicy(
        QueryPlanning queryPlanning,
        Retrieval retrieval,
        Fusion fusion,
        Rerank rerank,
        Context context,
        Budget budget) {
    public enum QueryStrategy {
        DIRECT,
        MULTI_QUERY,
        HYDE,
        STEP_BACK,
        DECOMPOSITION
    }

    public enum Channel {
        VECTOR,
        SPARSE,
        RAPTOR,
        GRAPH,
        MULTIMODAL
    }

    public enum FusionMethod {
        NONE,
        RRF,
        WEIGHTED_SCORE
    }

    public record QueryPlanning(QueryStrategy strategy, String modelId, int maxQueries, boolean includeOriginal) {}

    public record Retrieval(
            List<Channel> channels, int candidateLimitPerQuery, int parallelism, Map<String, Object> metadataFilter) {}

    public record Fusion(FusionMethod method, int rrfK, Map<Channel, Double> channelWeights) {}

    public record Rerank(
            boolean enabled,
            String provider,
            String model,
            int candidateLimit,
            int topN,
            Duration timeout,
            boolean failOpen) {}

    public record Context(
            boolean parentExpansion,
            int neighborWindow,
            boolean deduplicate,
            boolean compression,
            String compressionProvider,
            int maxTokens) {}

    public record Budget(
            int maxQueries,
            int maxRetrievalRounds,
            int maxLlmCalls,
            int maxCandidates,
            int maxEvidenceItems,
            Duration timeout) {}

    public static RagPolicy defaults() {
        return new RagPolicy(
                new QueryPlanning(QueryStrategy.DIRECT, null, 1, true),
                new Retrieval(List.of(Channel.VECTOR), 30, 4, Map.of()),
                new Fusion(FusionMethod.NONE, 60, Map.of()),
                new Rerank(false, null, null, 30, 8, Duration.ofSeconds(5), true),
                new Context(true, 0, true, false, null, 6000),
                new Budget(4, 1, 2, 100, 12, Duration.ofSeconds(15)));
    }
}
