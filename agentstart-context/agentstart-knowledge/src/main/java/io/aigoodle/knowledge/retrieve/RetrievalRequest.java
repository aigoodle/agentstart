package io.aigoodle.knowledge.retrieve;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.aigoodle.knowledge.enums.RetrievalMethod;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A retrieval query. Null overrides fall back to the dataset's configured defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonCreator(mode = JsonCreator.Mode.DISABLED))
public class RetrievalRequest {

    private String query;

    private Integer topK;

    private RetrievalMethod method;

    private Double scoreThreshold;

    /** Override the dense/vector fusion weight (0..1). */
    /** Equality filter applied to chunk metadata (e.g. {@code {"category":"faq"}}). */
    private Map<String, Object> metadataFilter;
}
