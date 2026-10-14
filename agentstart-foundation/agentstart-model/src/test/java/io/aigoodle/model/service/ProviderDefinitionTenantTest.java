package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.mapper.PredefinedModelMapper;
import io.aigoodle.model.mapper.ProviderDefinitionMapper;
import org.junit.jupiter.api.Test;

class ProviderDefinitionTenantTest {

    @Test
    void predefinedUpsertLookupIncludesOwningTenant() {
        ProviderDefinitionMapper providers = mock(ProviderDefinitionMapper.class);
        PredefinedModelMapper predefined = mock(PredefinedModelMapper.class);
        when(predefined.selectOne(any())).thenReturn(null);
        ProviderDefinitionService service = new ProviderDefinitionService(providers, predefined);
        PredefinedModelEntity row = new PredefinedModelEntity();
        row.setTenantId("tenant-a");
        row.setProviderName("openai");
        row.setModel("gpt-custom");
        row.setModelType(ModelType.LLM);

        service.upsertPredefined(row);

        verify(predefined).selectOne(any());
        assertThat(row.getTenantId()).isEqualTo("tenant-a");
        verify(predefined).insert(row);
    }
}
