package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppAnnotationSettingEntity;
import io.aigoodle.agent.mapper.AppAnnotationSettingMapper;
import org.junit.jupiter.api.Test;

class AppAnnotationSettingServiceTest {

    @Test
    void returnsUnpersistedDefaultsForAnUnconfiguredApplication() {
        AppAnnotationSettingMapper settingMapper = mock(AppAnnotationSettingMapper.class);
        AppAnnotationSettingService settingService = new AppAnnotationSettingService(settingMapper);

        AppAnnotationSettingEntity setting = settingService.getByApp("app-1");

        assertThat(setting.getAppId()).isEqualTo("app-1");
        assertThat(setting.getScoreThreshold()).isEqualTo(0.8f);
        assertThat(setting.getEnabled()).isFalse();
        verify(settingMapper, never()).insert(any(AppAnnotationSettingEntity.class));
    }

    @Test
    void createsRouteOwnedSettingsWithoutTrustingIdentityFieldsFromBody() {
        AppAnnotationSettingMapper settingMapper = mock(AppAnnotationSettingMapper.class);
        AppAnnotationSettingService settingService = new AppAnnotationSettingService(settingMapper);
        AppAnnotationSettingEntity updates = new AppAnnotationSettingEntity();
        updates.setId("client-id");
        updates.setAppId("other-app");
        updates.setScoreThreshold(0.9f);

        AppAnnotationSettingEntity saved = settingService.save("app-1", updates);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getAppId()).isEqualTo("app-1");
        assertThat(saved.getScoreThreshold()).isEqualTo(0.9f);
        verify(settingMapper).insert(saved);
    }

    @Test
    void enablingNewSettingsUsesOneLookupAndOneInsert() {
        AppAnnotationSettingMapper settingMapper = mock(AppAnnotationSettingMapper.class);
        AppAnnotationSettingService settingService = new AppAnnotationSettingService(settingMapper);

        AppAnnotationSettingEntity enabled = settingService.setEnabled("app-1", true);

        assertThat(enabled.getAppId()).isEqualTo("app-1");
        assertThat(enabled.getEnabled()).isTrue();
        assertThat(enabled.getScoreThreshold()).isEqualTo(0.8f);
        verify(settingMapper).selectOne(any());
        verify(settingMapper).insert(enabled);
    }

    @Test
    void updatesExistingSettingsInPlace() {
        AppAnnotationSettingMapper settingMapper = mock(AppAnnotationSettingMapper.class);
        AppAnnotationSettingEntity existing = new AppAnnotationSettingEntity();
        existing.setId("setting-1");
        existing.setAppId("app-1");
        existing.setScoreThreshold(0.8f);
        when(settingMapper.selectOne(any())).thenReturn(existing);
        AppAnnotationSettingService settingService = new AppAnnotationSettingService(settingMapper);
        AppAnnotationSettingEntity updates = new AppAnnotationSettingEntity();
        updates.setScoreThreshold(0.95f);
        updates.setEnabled(true);

        AppAnnotationSettingEntity saved = settingService.save("app-1", updates);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getScoreThreshold()).isEqualTo(0.95f);
        assertThat(saved.getEnabled()).isTrue();
        verify(settingMapper).update(org.mockito.ArgumentMatchers.eq(existing), any());
        verify(settingMapper, never()).insert(any(AppAnnotationSettingEntity.class));
    }

    @Test
    void scopedCreateOverridesForgedTenantAndApplication() {
        AppAnnotationSettingMapper mapper = mock(AppAnnotationSettingMapper.class);
        AppAnnotationSettingEntity supplied = new AppAnnotationSettingEntity();
        supplied.setId("forged-id");
        supplied.setTenantId("tenant-b");
        supplied.setAppId("app-b");

        AppAnnotationSettingEntity saved = new AppAnnotationSettingService(mapper).save("tenant-a", "app-a", supplied);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getAppId()).isEqualTo("app-a");
        verify(mapper).insert(saved);
    }
}
