package io.aigoodle.knowledge.service;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;

/** Builds a persistent dataset definition from its creation request. */
final class DatasetDefinitionFactory {

    private static final String DEFAULT_TENANT = "default";

    private DatasetDefinitionFactory() {}

    static DatasetEntity create(CreateDatasetRequest request) {
        IndexingTechnique indexingTechnique = request.getIndexingTechnique() == null
                ? IndexingTechnique.HIGH_QUALITY
                : request.getIndexingTechnique();
        requireEmbeddingModelFor(indexingTechnique, request.getEmbeddingModelId());

        DatasetEntity dataset = new DatasetEntity();
        dataset.setTenantId(defaultTenant(request.getTenantId()));
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setEmbeddingModelId(request.getEmbeddingModelId());
        dataset.setIndexingTechnique(indexingTechnique);
        dataset.setProcessRuleJson(JsonUtils.toJson(defaultProcessRule(request.getProcessRule())));
        dataset.setRetrievalConfigJson(JsonUtils.toJson(defaultRetrievalConfig(request.getRetrievalConfig())));
        dataset.setVectorStore(request.getVectorStore());
        dataset.setDocumentCount(0);
        dataset.setSegmentCount(0);
        return dataset;
    }

    static void requireEmbeddingModelFor(IndexingTechnique technique, String embeddingModelId) {
        if (embeddingModelId == null || embeddingModelId.isBlank()) {
            throw new PlatformException("embedding_model_required", "A dataset requires an embedding model id", null);
        }
    }

    private static String defaultTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }

    private static ProcessRule defaultProcessRule(ProcessRule processRule) {
        return processRule == null ? ProcessRule.naive() : processRule;
    }

    private static RetrievalConfig defaultRetrievalConfig(RetrievalConfig retrievalConfig) {
        return retrievalConfig == null ? RetrievalConfig.vector() : retrievalConfig;
    }
}
