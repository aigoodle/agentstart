package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.RemoteModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelCatalogPersistenceTest {
    private final ProviderCatalogClient client = mock(ProviderCatalogClient.class);
    private final ProviderDefinitionService definitions = mock(ProviderDefinitionService.class);
    private final ProviderModelSettingsService settings = mock(ProviderModelSettingsService.class);
    private final ModelService service =
            new ModelService(null, null, null, null, null, definitions, settings, null, client);

    @Test
    void persistsNewCatalogEntriesWithoutEnablingOrOverwritingKnownModels() {
        PredefinedModelEntity existing = new PredefinedModelEntity();
        existing.setModel("known");
        existing.setModelType(ModelType.LLM);
        when(definitions.listPredefined("tenant-a", "openai")).thenReturn(List.of(existing));
        RemoteModel added = RemoteModel.builder()
                .modelId("new-model")
                .modelType(ModelType.LLM)
                .label("New model")
                .contextLength(32000)
                .build();
        List<RemoteModel> remote = List.of(
                RemoteModel.builder().modelId("known").modelType(ModelType.LLM).build(), added, added);
        when(client.refresh("tenant-a", "openai")).thenReturn(remote);

        assertThat(service.refreshCatalog("tenant-a", "openai")).isEqualTo(remote);

        ArgumentCaptor<PredefinedModelEntity> row = ArgumentCaptor.forClass(PredefinedModelEntity.class);
        verify(definitions).upsertPredefined(row.capture());
        assertThat(row.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(row.getValue().getModel()).isEqualTo("new-model");
        assertThat(row.getValue().getProviderName()).isEqualTo("openai");
        assertThat(row.getValue().getContextLength()).isEqualTo(32000);
        verifyNoInteractions(settings);
    }

    @Test
    void failedRefreshDoesNotModifyPersistedCatalog() {
        when(client.refresh("default", "openai")).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.refreshCatalog(null, "openai")).hasMessage("offline");
        verifyNoInteractions(definitions, settings);
    }
}
