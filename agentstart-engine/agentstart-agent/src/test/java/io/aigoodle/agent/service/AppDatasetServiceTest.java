package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.service.DatasetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppDatasetServiceTest {

    private final AppMapper appMapper = mock(AppMapper.class);
    private final AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final AppDatasetService appDatasetService =
            new AppDatasetService(appMapper, modelConfigService, datasetService);

    @Test
    void rejectsCrossTenantDatasetsAlreadyPresentInStoredConfiguration() {
        AppEntity application = application("app-1", "tenant-a");
        AppModelConfigEntity configuration = configuration("[\"dataset-b\"]");
        when(appMapper.selectOne(any())).thenReturn(application);
        when(modelConfigService.findByAppId("tenant-a", "app-1")).thenReturn(configuration);
        when(datasetService.require("tenant-a", "dataset-b"))
                .thenThrow(new PlatformException("dataset_not_found", "Dataset not found", null));

        assertThatThrownBy(() -> appDatasetService.list("app-1"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Dataset not found");
    }

    @Test
    void appendsOnlyUniqueDatasetIdsWhilePreservingTheirOrder() {
        AppEntity application = application("app-1", "tenant-a");
        AppModelConfigEntity configuration = configuration("[\"dataset-1\"]");
        when(appMapper.selectOne(any())).thenReturn(application);
        when(modelConfigService.findByAppId("tenant-a", "app-1")).thenReturn(configuration);
        when(datasetService.require("tenant-a", "dataset-1")).thenReturn(dataset("dataset-1", "tenant-a"));
        when(datasetService.require("tenant-a", "dataset-2")).thenReturn(dataset("dataset-2", "tenant-a"));
        when(modelConfigService.upsert(any()))
                .thenAnswer(invocation ->
                        invocation.<AppModelConfigRegistration>getArgument(0).configuration());

        List<AttachedDatasetView> attached =
                appDatasetService.attach("app-1", List.of("dataset-1", "dataset-2", "dataset-2"));

        assertThat(attached).extracting(AttachedDatasetView::id).containsExactly("dataset-1", "dataset-2");
        ArgumentCaptor<AppModelConfigRegistration> registration =
                ArgumentCaptor.forClass(AppModelConfigRegistration.class);
        verify(modelConfigService).upsert(registration.capture());
        assertThat(registration.getValue().configuration().getDatasetIdsJson())
                .isEqualTo("[\"dataset-1\",\"dataset-2\"]");
    }

    @Test
    void treatsBlankTenantIdsAsTheDefaultTenant() {
        AppEntity application = application("app-1", null);
        AppModelConfigEntity configuration = configuration("[\"dataset-1\"]");
        when(appMapper.selectOne(any())).thenReturn(application);
        when(modelConfigService.findByAppId(null, "app-1")).thenReturn(configuration);
        when(datasetService.require(null, "dataset-1")).thenReturn(dataset("dataset-1", "default"));

        assertThat(appDatasetService.list("app-1"))
                .extracting(AttachedDatasetView::id)
                .containsExactly("dataset-1");
    }

    private static AppEntity application(String id, String tenantId) {
        AppEntity application = new AppEntity();
        application.setId(id);
        application.setTenantId(tenantId);
        return application;
    }

    private static AppModelConfigEntity configuration(String datasetIdsJson) {
        AppModelConfigEntity configuration = new AppModelConfigEntity();
        configuration.setDatasetIdsJson(datasetIdsJson);
        return configuration;
    }

    private static DatasetEntity dataset(String id, String tenantId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(id);
        dataset.setTenantId(tenantId);
        return dataset;
    }
}
