package io.aigoodle.plugin.host;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.ConnectorKey;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.plugin.PluginManifest;
import java.util.Map;
import java.util.function.Supplier;

/** Shared MVC/WebFlux API service; callback identity comes exclusively from verified claims. */
public class PluginHostApi {
    private final PluginHostTokenService tokens;
    private final PluginHostFactory hosts;
    private final Supplier<ConnectorRegistry> registry;
    private final Supplier<ConnectorInstallationService> installations;

    public PluginHostApi(
            PluginHostTokenService tokens,
            PluginHostFactory hosts,
            Supplier<ConnectorRegistry> registry,
            Supplier<ConnectorInstallationService> installations) {
        this.tokens = tokens;
        this.hosts = hosts;
        this.registry = registry;
        this.installations = installations;
    }

    public Object call(String token, String capability, Map<String, Object> arguments) {
        var claims = tokens.verify(token);
        if (!claims.capabilities().contains(capability))
            throw new ConnectorException("plugin_capability_denied", "Capability not present in invocation token");
        var definition = registry.get().get(new ConnectorKey("plugin", claims.pluginId()));
        if (!definition.version().equals(claims.pluginVersion()))
            throw new ConnectorException("plugin_version_mismatch", "Plugin version changed during invocation");
        boolean enabled = installations.get().list(claims.identity().tenantId()).stream()
                .anyMatch(item -> item.provider().equals("plugin")
                        && item.connectorId().equals(claims.pluginId())
                        && item.enabled());
        if (!enabled) throw new ConnectorException("plugin_disabled", "Plugin installation is unavailable");
        var manifest = new PluginManifest(
                claims.pluginId(),
                definition.version(),
                definition.name(),
                definition.description(),
                definition.icon(),
                definition.category(),
                definition.configurationSchema(),
                Map.of(),
                definition.actions(),
                claims.capabilities());
        return hosts.forInvocation(manifest, claims.identity()).call(capability, arguments);
    }
}
