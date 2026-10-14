package io.aigoodle.knowledge.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for creating a dataset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonCreator(mode = JsonCreator.Mode.DISABLED))
public class CreateDatasetRequest {

    private String tenantId;
    private String name;
    private String description;
    private String embeddingModelId;

    @Builder.Default
    private IndexingTechnique indexingTechnique = IndexingTechnique.HIGH_QUALITY;

    private ProcessRule processRule;
    private RetrievalConfig retrievalConfig;
    private String vectorStore;
}
