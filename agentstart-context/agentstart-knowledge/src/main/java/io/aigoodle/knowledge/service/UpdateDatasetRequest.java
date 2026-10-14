package io.aigoodle.knowledge.service;

import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import lombok.Data;

/**
 * Patch body for {@link DatasetService#update(String, UpdateDatasetRequest)}. All
 * fields are optional; omitted fields keep their current value in the database.
 */
@Data
public class UpdateDatasetRequest {

    private String name;
    private String description;
    private String embeddingModelId;
    private IndexingTechnique indexingTechnique;
    private ProcessRule processRule;
    private RetrievalConfig retrievalConfig;
    private String vectorStore;
}
