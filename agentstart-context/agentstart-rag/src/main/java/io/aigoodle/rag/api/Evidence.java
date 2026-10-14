package io.aigoodle.rag.api;

import java.util.List;
import java.util.Map;

public record Evidence(
        String datasetId,
        String documentId,
        String segmentId,
        Integer position,
        String content,
        String context,
        double retrievalScore,
        Double fusionScore,
        Double rerankScore,
        List<String> matchedQueryIds,
        Map<String, Object> metadata) {
    public Evidence {
        matchedQueryIds = matchedQueryIds == null ? List.of() : List.copyOf(matchedQueryIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
