package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.rag.api.RagPolicy;
import java.util.List;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import org.junit.jupiter.api.Test;

class DatasetPatchApplicatorTest {

    @Test
    void appliesOnlyFieldsSuppliedByThePatch() {
        DatasetEntity dataset = dataset(IndexingTechnique.HIGH_QUALITY, "test-embedding");
        dataset.setName("Existing name");
        dataset.setDescription("Existing description");
        UpdateDatasetRequest patch = new UpdateDatasetRequest();
        patch.setDescription("Updated description");

        DatasetPatchApplicator.apply(dataset, patch);

        assertThat(dataset.getName()).isEqualTo("Existing name");
        assertThat(dataset.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void preventsClearingTheEmbeddingModel() {
        DatasetEntity dataset = dataset(IndexingTechnique.HIGH_QUALITY, "test-embedding");
        UpdateDatasetRequest patch = new UpdateDatasetRequest();
        patch.setEmbeddingModelId(" ");

        assertThatThrownBy(() -> DatasetPatchApplicator.apply(dataset, patch))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("embedding model id");
        assertThat(dataset.getEmbeddingModelId()).isEqualTo("test-embedding");
    }

    @Test
    void persistsPolicyAndPreservesItWhenLegacyClientsUpdateRecallSettings() {
        DatasetEntity dataset = dataset(IndexingTechnique.HIGH_QUALITY, "test-embedding");
        RagPolicy defaults = RagPolicy.defaults();
        RagPolicy policy = new RagPolicy(
                defaults.queryPlanning(),
                new RagPolicy.Retrieval(List.of(RagPolicy.Channel.SPARSE), 42, 2, java.util.Map.of()),
                defaults.fusion(), defaults.rerank(), defaults.context(), defaults.budget());
        RetrievalConfig configured = new RetrievalConfig();
        configured.setRagPolicy(policy);
        UpdateDatasetRequest first = new UpdateDatasetRequest();
        first.setRetrievalConfig(configured);
        DatasetPatchApplicator.apply(dataset, first);
        assertThat(JsonUtils.parse(dataset.getRetrievalConfigJson(), RetrievalConfig.class).getRagPolicy())
                .isEqualTo(policy);

        RetrievalConfig legacy = new RetrievalConfig();
        legacy.setTopK(17);
        UpdateDatasetRequest second = new UpdateDatasetRequest();
        second.setRetrievalConfig(legacy);
        DatasetPatchApplicator.apply(dataset, second);
        RetrievalConfig stored = JsonUtils.parse(dataset.getRetrievalConfigJson(), RetrievalConfig.class);
        assertThat(stored.getTopK()).isEqualTo(17);
        assertThat(stored.getRagPolicy()).isEqualTo(policy);
    }

    private static DatasetEntity dataset(IndexingTechnique technique, String embeddingModelId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setIndexingTechnique(technique);
        dataset.setEmbeddingModelId(embeddingModelId);
        return dataset;
    }
}
