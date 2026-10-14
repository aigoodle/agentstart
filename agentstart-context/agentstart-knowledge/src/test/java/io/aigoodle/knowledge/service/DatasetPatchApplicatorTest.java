package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.exception.PlatformException;
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

    private static DatasetEntity dataset(IndexingTechnique technique, String embeddingModelId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setIndexingTechnique(technique);
        dataset.setEmbeddingModelId(embeddingModelId);
        return dataset;
    }
}
