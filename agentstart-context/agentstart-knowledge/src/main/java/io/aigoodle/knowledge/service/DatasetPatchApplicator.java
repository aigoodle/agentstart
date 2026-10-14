package io.aigoodle.knowledge.service;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;

/** Applies the fields supplied by a dataset settings patch. */
final class DatasetPatchApplicator {

    private DatasetPatchApplicator() {}

    static void apply(DatasetEntity dataset, UpdateDatasetRequest patch) {
        IndexingTechnique resultingTechnique =
                patch.getIndexingTechnique() == null ? dataset.getIndexingTechnique() : patch.getIndexingTechnique();
        String resultingEmbeddingModelId =
                patch.getEmbeddingModelId() == null ? dataset.getEmbeddingModelId() : patch.getEmbeddingModelId();
        DatasetDefinitionFactory.requireEmbeddingModelFor(resultingTechnique, resultingEmbeddingModelId);

        if (patch.getName() != null && !patch.getName().isBlank()) {
            dataset.setName(patch.getName());
        }
        if (patch.getDescription() != null) {
            dataset.setDescription(patch.getDescription());
        }
        if (patch.getEmbeddingModelId() != null) {
            dataset.setEmbeddingModelId(patch.getEmbeddingModelId());
        }
        if (patch.getIndexingTechnique() != null) {
            dataset.setIndexingTechnique(patch.getIndexingTechnique());
        }
        if (patch.getProcessRule() != null) {
            dataset.setProcessRuleJson(JsonUtils.toJson(patch.getProcessRule()));
        }
        if (patch.getRetrievalConfig() != null) {
            dataset.setRetrievalConfigJson(JsonUtils.toJson(patch.getRetrievalConfig()));
        }
        if (patch.getVectorStore() != null) {
            dataset.setVectorStore(patch.getVectorStore());
        }
    }
}
