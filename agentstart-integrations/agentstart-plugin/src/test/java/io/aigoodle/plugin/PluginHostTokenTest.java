package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.host.PluginHostTokenService;
import java.time.*;
import org.junit.jupiter.api.Test;

class PluginHostTokenTest {
    private static final String SECRET = "test-key-32-bytes-long-not-production-value";
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    private PluginHostTokenService service(Instant time) {
        return new PluginHostTokenService(
                SECRET, "http://host:18090/agent-start/plugin-host/v1", Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test
    void tokenBindsPluginVersionIdentityAndDeclaredCapabilities() {
        var tokens = service(NOW);
        var access = tokens.issue(
                PluginRuntimeTest.manifest(), PluginRuntimeTest.request().context(), Duration.ofMinutes(10));
        var claims = tokens.verify(access.token());
        assertThat(claims.identity()).isEqualTo(PluginRuntimeTest.request().context());
        assertThat(claims.pluginId()).isEqualTo("acme.video");
        assertThat(claims.capabilities()).containsExactly("product.read");
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300).getEpochSecond());
        assertThat(access.toString()).doesNotContain(access.token());
    }

    @Test
    void expiredAndTamperedTokensFailClosed() {
        var token = service(NOW)
                .issue(PluginRuntimeTest.manifest(), PluginRuntimeTest.request().context(), Duration.ofSeconds(5))
                .token();
        assertThatThrownBy(() -> service(NOW.plusSeconds(5)).verify(token)).isInstanceOf(ConnectorException.class);
        assertThatThrownBy(() -> service(NOW).verify("x" + token)).isInstanceOf(ConnectorException.class);
        assertThatThrownBy(() -> service(NOW).verify(null)).isInstanceOf(ConnectorException.class);
    }
}
