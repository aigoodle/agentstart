package io.aigoodle.rag.api;

import java.util.List;
import java.util.Map;

public record RagResult(
        String originalQuery, QueryPlan queryPlan, List<Evidence> evidence, Map<String, Long> stageLatencyMillis) {
    public RagResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        stageLatencyMillis = stageLatencyMillis == null ? Map.of() : Map.copyOf(stageLatencyMillis);
    }
}
