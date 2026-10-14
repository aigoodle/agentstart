package io.aigoodle.rag.api;

import java.util.List;
import java.util.Map;

public record RagRequest(
        String tenantId,
        List<String> datasetIds,
        String query,
        String conversationId,
        RagPolicy policy,
        Map<String, Object> metadataFilter) {
    public RagRequest {
        datasetIds = datasetIds == null ? List.of() : List.copyOf(datasetIds);
        policy = policy == null ? RagPolicy.defaults() : policy;
        metadataFilter = metadataFilter == null ? Map.of() : Map.copyOf(metadataFilter);
    }
}
