package io.aigoodle.plugin.host;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.plugin.PluginHost;
import io.aigoodle.plugin.PluginManifest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Requested permissions are not grants. Only deployment-owned grants authorize calls. */
public class PluginHostFactory {
    private final Map<String, PluginHostCapability> capabilities = new LinkedHashMap<>();
    private final Map<String, List<String>> grants;

    public PluginHostFactory(List<PluginHostCapability> capabilities, Map<String, List<String>> grants) {
        capabilities.forEach(capability -> {
            if (this.capabilities.putIfAbsent(capability.name(), capability) != null)
                throw new IllegalArgumentException("Duplicate host capability: " + capability.name());
        });
        this.grants = Map.copyOf(grants);
    }

    public PluginHost forInvocation(PluginManifest manifest, ConnectorExecutionContext identity) {
        return (name, arguments) -> {
            if (!manifest.requestedCapabilities().contains(name)
                    || !grants.getOrDefault(manifest.id(), List.of()).contains(name))
                throw new ConnectorException("plugin_capability_denied", "Plugin capability is not granted: " + name);
            PluginHostCapability capability = capabilities.get(name);
            if (capability == null)
                throw new ConnectorException("plugin_capability_missing", "Host capability unavailable: " + name);
            return capability.execute(identity, arguments == null ? Map.of() : Map.copyOf(arguments));
        };
    }
}
