package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.mapper.AppApiTokenMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AppApiTokenServiceTest {

    @Test
    void createsOpaqueTokenWithNormalizedDefaults() {
        AppApiTokenMapper tokenMapper = mock(AppApiTokenMapper.class);
        AtomicReference<String> persistedToken = new AtomicReference<>();
        when(tokenMapper.insert(any(AppApiTokenEntity.class))).thenAnswer(invocation -> {
            persistedToken.set(invocation.<AppApiTokenEntity>getArgument(0).getToken());
            return 1;
        });
        AppApiTokenService tokenService = new AppApiTokenService(tokenMapper);

        AppApiTokenEntity apiToken = tokenService.create("app-1", " ", null, "");

        assertThat(apiToken.getTenantId()).isEqualTo("default");
        assertThat(apiToken.getName()).isEqualTo("default");
        assertThat(apiToken.getType()).isEqualTo("app");
        assertThat(apiToken.getToken()).startsWith("app-").doesNotContain("=");
        assertThat(persistedToken.get())
                .startsWith("sha256$")
                .contains("$" + AppApiTokenService.tokenHint(apiToken.getToken()) + "$")
                .doesNotContain(apiToken.getToken());
    }

    @Test
    void lastUsedUpdateNeverDisruptsRequestPath() {
        AppApiTokenMapper tokenMapper = mock(AppApiTokenMapper.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(tokenMapper)
                .update(any(AppApiTokenEntity.class), any());
        AppApiTokenService tokenService = new AppApiTokenService(tokenMapper);

        assertThatCode(() -> tokenService.touchLastUsed("token-1")).doesNotThrowAnyException();
    }

    @Test
    void tenantScopedRenameAndDeleteCannotTouchAnUnknownForeignToken() {
        AppApiTokenMapper mapper = mock(AppApiTokenMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        AppApiTokenService service = new AppApiTokenService(mapper);

        assertThatThrownBy(() -> service.rename("tenant-b", "app-1", "foreign-token", "stolen"))
                .hasMessageContaining("API token not found");
        assertThatThrownBy(() -> service.delete("tenant-b", "app-1", "foreign-token"))
                .hasMessageContaining("API token not found");
        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).delete(any());
    }
}
