package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import org.junit.jupiter.api.Test;

class DatasetDefinitionFactoryTest {

    @Test
    void resolvesDefaultsBeforeValidatingTheDataset() {
        CreateDatasetRequest request = CreateDatasetRequest.builder()
                .name("Product handbook")
                .indexingTechnique(null)
                .build();

        assertThatThrownBy(() -> DatasetDefinitionFactory.create(request))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("embedding model id");
    }

    @Test
    void createsVectorDatasetWithAnEmbeddingModel() {
        CreateDatasetRequest request = CreateDatasetRequest.builder()
                .tenantId(" ")
                .name("FAQ")
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .embeddingModelId("test-embedding")
                .build();

        DatasetEntity dataset = DatasetDefinitionFactory.create(request);

        assertThat(dataset.getTenantId()).isEqualTo("default");
        assertThat(dataset.getIndexingTechnique()).isEqualTo(IndexingTechnique.HIGH_QUALITY);
        assertThat(dataset.getDocumentCount()).isZero();
        assertThat(dataset.getSegmentCount()).isZero();
        assertThat(dataset.getProcessRuleJson()).isEqualTo(JsonUtils.toJson(ProcessRule.naive()));
        assertThat(dataset.getRetrievalConfigJson()).isEqualTo(JsonUtils.toJson(RetrievalConfig.vector()));
    }
}
