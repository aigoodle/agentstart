package io.aigoodle.rag.api;

import java.util.Map;

public record RetrievalCandidate(
        String datasetId,
        String documentId,
        String segmentId,
        Integer position,
        String content,
        String parentContent,
        double score,
        RagPolicy.Channel channel,
        String queryId,
        Map<String, Object> metadata) {
    public RetrievalCandidate {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
