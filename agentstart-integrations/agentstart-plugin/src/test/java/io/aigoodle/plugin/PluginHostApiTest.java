package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.aigoodle.connector.*;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.plugin.host.*;
import io.aigoodle.plugin.runtime.PluginConnectorProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PluginHostApiTest {
    @Test
    void verifiesTokenAndEnabledTenantInstallationBeforeHostAccess() {
        var tokens = new PluginHostTokenService("test-only-key-at-least-32-characters", "http://host/plugin-host/v1");
        AtomicReference<io.aigoodle.connector.execution.ConnectorExecutionContext> captured = new AtomicReference<>();
        var hosts = new PluginHostFactory(
                List.of(PluginRuntimeTest.capability(captured)), Map.of("acme.video", List.of("product.read")));
        var registry = new ConnectorRegistry(
                List.of(new PluginConnectorProvider(List.of(PluginRuntimeTest.local()), request -> null, hosts)));
        var installs = mock(ConnectorInstallationService.class);
        when(installs.list("tenant-a"))
                .thenReturn(List.of(new ConnectorInstallationService.InstallationView(
                        "i1", "tenant-a", "plugin", "acme.video", "1.0.0", "NATIVE", true, "REVIEWED")));
        var api = new PluginHostApi(tokens, hosts, () -> registry, () -> installs);
        String token = tokens.issue(
                        PluginRuntimeTest.manifest(),
                        PluginRuntimeTest.request().context(),
                        Duration.ofSeconds(30))
                .token();
        assertThat(api.call(token, "product.read", Map.of("productId", "1", "tenantId", "forged")))
                .isEqualTo(Map.of("title", "Product 1"));
        assertThat(captured.get().tenantId()).isEqualTo("tenant-a");
        assertThatThrownBy(() -> api.call(token, "model.chat", Map.of())).isInstanceOf(ConnectorException.class);
        when(installs.list("tenant-a")).thenReturn(List.of());
        assertThatThrownBy(() -> api.call(token, "product.read", Map.of())).hasMessageContaining("unavailable");
        verify(installs, never()).list("forged");
    }
}
