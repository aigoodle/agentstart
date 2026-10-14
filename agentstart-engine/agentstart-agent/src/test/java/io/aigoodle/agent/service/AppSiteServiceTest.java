package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppSiteEntity;
import io.aigoodle.agent.mapper.AppSiteMapper;
import org.junit.jupiter.api.Test;

class AppSiteServiceTest {

    @Test
    void initializesStableDefaultsForNewSite() {
        AppSiteMapper siteMapper = mock(AppSiteMapper.class);
        AppSiteService siteService = new AppSiteService(siteMapper);
        AppSiteEntity newSite = new AppSiteEntity();

        AppSiteEntity savedSite = siteService.save("app-1", newSite);

        assertThat(savedSite).isSameAs(newSite);
        assertThat(savedSite.getAppId()).isEqualTo("app-1");
        assertThat(savedSite.getStatus()).isEqualTo("normal");
        assertThat(savedSite.getCode()).hasSize(12).doesNotContain("=");
        verify(siteMapper).insert(newSite);
    }

    @Test
    void patchesOnlyFieldsExplicitlyProvided() {
        AppSiteMapper siteMapper = mock(AppSiteMapper.class);
        AppSiteEntity existingSite = new AppSiteEntity();
        existingSite.setId("site-1");
        existingSite.setAppId("app-1");
        existingSite.setCode("stable-code");
        existingSite.setTitle("Old title");
        existingSite.setDescription("Keep this description");
        when(siteMapper.selectOne(any())).thenReturn(existingSite);
        AppSiteEntity updates = new AppSiteEntity();
        updates.setTitle("New title");

        AppSiteEntity savedSite = new AppSiteService(siteMapper).save("app-1", updates);

        assertThat(savedSite).isSameAs(existingSite);
        assertThat(savedSite.getCode()).isEqualTo("stable-code");
        assertThat(savedSite.getTitle()).isEqualTo("New title");
        assertThat(savedSite.getDescription()).isEqualTo("Keep this description");
        verify(siteMapper).update(org.mockito.ArgumentMatchers.eq(existingSite), any());
    }

    @Test
    void scopedCreateOverridesForgedTenantAndApplication() {
        AppSiteMapper mapper = mock(AppSiteMapper.class);
        AppSiteEntity supplied = new AppSiteEntity();
        supplied.setId("forged-id");
        supplied.setTenantId("tenant-b");
        supplied.setAppId("app-b");

        AppSiteEntity saved = new AppSiteService(mapper).save("tenant-a", "app-a", supplied);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getAppId()).isEqualTo("app-a");
        verify(mapper).insert(supplied);
    }
}
