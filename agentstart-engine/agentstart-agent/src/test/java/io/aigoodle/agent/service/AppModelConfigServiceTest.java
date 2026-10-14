package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.mapper.AppModelConfigMapper;
import org.junit.jupiter.api.Test;

class AppModelConfigServiceTest {

    @Test
    void insertsAConfigurationUsingTheApplicationIdentity() {
        AppModelConfigMapper configMapper = mock(AppModelConfigMapper.class);
        AppModelConfigService service = new AppModelConfigService(configMapper);
        AppModelConfigEntity configuration = new AppModelConfigEntity();

        AppModelConfigEntity saved = service.upsert(new AppModelConfigRegistration("app-1", "tenant-1", configuration));

        assertThat(saved).isSameAs(configuration);
        assertThat(configuration.getId()).isEqualTo("app-1");
        assertThat(configuration.getAppId()).isEqualTo("app-1");
        assertThat(configuration.getTenantId()).isEqualTo("tenant-1");
        verify(configMapper).insert(configuration);
    }

    @Test
    void patchesTheExistingSidecarInsteadOfReplacingItsIdentity() {
        AppModelConfigMapper configMapper = mock(AppModelConfigMapper.class);
        AppModelConfigService service = new AppModelConfigService(configMapper);
        AppModelConfigEntity existing = new AppModelConfigEntity();
        existing.setId("app-1");
        existing.setModelName("old-model");
        AppModelConfigEntity patch = new AppModelConfigEntity();
        patch.setModelName("new-model");
        when(configMapper.selectOne(any())).thenReturn(existing);

        AppModelConfigEntity saved = service.upsert(new AppModelConfigRegistration("app-1", "tenant-1", patch));

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getId()).isEqualTo("app-1");
        assertThat(existing.getModelName()).isEqualTo("new-model");
        verify(configMapper).update(org.mockito.ArgumentMatchers.eq(existing), any());
    }

    @Test
    void readsAndDeletesOnlyWithinTheRequestedTenant() {
        AppModelConfigMapper configMapper = mock(AppModelConfigMapper.class);
        AppModelConfigService service = new AppModelConfigService(configMapper);

        service.findByAppId("tenant-1", "app-1");
        service.deleteByAppId("tenant-1", "app-1");

        verify(configMapper).selectOne(any());
        verify(configMapper).delete(any());
    }
}
