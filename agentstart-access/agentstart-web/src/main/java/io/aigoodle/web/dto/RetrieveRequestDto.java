package io.aigoodle.web.dto;

import io.aigoodle.knowledge.enums.RetrievalMethod;
import java.util.Map;
import lombok.Data;

/**
 * Frontend-facing retrieval request. Mirrors {@code RetrievalRequest} but decouples
 * the wire format from the internal builder so we can add fields without breaking
 * clients.
 */
@Data
public class RetrieveRequestDto {

    private String query;
    private RetrievalMethod method;
    private Integer topK;
    private Double scoreThreshold;
    private Map<String, Object> metadataFilter;
}
