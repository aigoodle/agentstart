package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.KnowledgeTestApplication;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexVersionStatus;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.knowledge.service.CreateDatasetRequest;
import io.aigoodle.knowledge.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = KnowledgeTestApplication.class)
class IndexVersionServiceTest {
    @Autowired
    private DatasetService datasetService;

    @Autowired
    private IndexVersionService versionService;

    @Test
    void atomicallyActivatesAndRollsBackGenerations() {
        DatasetEntity dataset = datasetService.create(CreateDatasetRequest.builder()
                .tenantId("index-version-test")
                .name("versioned")
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .embeddingModelId("test-embedding")
                .build());
        var v1 = versionService.beginRebuild(
                dataset.getTenantId(), dataset.getId(), "v1", "embed-v1", "chunk-v1", "aaa");
        var v2 = versionService.beginRebuild(
                dataset.getTenantId(), dataset.getId(), "v2", "embed-v2", "chunk-v2", "bbb");

        versionService.activate(dataset.getTenantId(), dataset.getId(), v1.getId());
        versionService.activate(dataset.getTenantId(), dataset.getId(), v2.getId());

        assertThat(versionService.list(dataset.getTenantId(), dataset.getId()))
                .filteredOn(v -> v.getId().equals(v1.getId()))
                .singleElement()
                .extracting(v -> v.getStatus())
                .isEqualTo(IndexVersionStatus.RETIRED);
        assertThat(versionService.list(dataset.getTenantId(), dataset.getId()))
                .filteredOn(v -> v.getId().equals(v2.getId()))
                .singleElement()
                .extracting(v -> v.getStatus())
                .isEqualTo(IndexVersionStatus.ACTIVE);

        versionService.activate(dataset.getTenantId(), dataset.getId(), v1.getId());
        assertThat(datasetService
                        .require(dataset.getTenantId(), dataset.getId())
                        .getActiveIndexVersionId())
                .isEqualTo(v1.getId());
    }
}
