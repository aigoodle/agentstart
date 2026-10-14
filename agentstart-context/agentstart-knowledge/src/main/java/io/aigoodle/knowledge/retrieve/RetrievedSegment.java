package io.aigoodle.knowledge.retrieve;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * A scored chunk returned from vector retrieval with source attribution.
 */
@Data
@Builder
public class RetrievedSegment {

    private String segmentId;
    private String datasetId;
    private String documentId;
    private Integer position;

    private String content;

    /** Larger parent context for PARENT_CHILD chunks; null otherwise. */
    private String parentContent;

    /** Context assembled from adjacent chunks without changing the ranked hit itself. */
    private String expandedContext;

    private double score;
    private double vectorScore;
    private Map<String, Object> metadata;

    /** Best text to feed an LLM: the parent context when available, else the chunk. */
    public String contextText() {
        if (expandedContext != null && !expandedContext.isBlank()) return expandedContext;
        return parentContent != null && !parentContent.isBlank() ? parentContent : content;
    }
}
