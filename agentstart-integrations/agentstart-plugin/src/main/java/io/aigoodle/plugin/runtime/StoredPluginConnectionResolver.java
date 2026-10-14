package io.aigoodle.plugin.runtime;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import java.util.Map;

/** Explicitly validates enabled installation, tenant and connection/plugin binding. */
public class StoredPluginConnectionResolver implements PluginConnectionResolver {
    private final ConnectorInstallationService installations;
    private final ConnectorConnectionService connections;

    public StoredPluginConnectionResolver(
            ConnectorInstallationService installations, ConnectorConnectionService connections) {
        this.installations = installations;
        this.connections = connections;
    }

    @Override
    public ResolvedConnection resolve(ConnectorExecutionRequest request) {
        var installation = installations.list(request.context().tenantId()).stream()
                .filter(item -> item.provider().equals(request.connector().provider())
                        && item.connectorId().equals(request.connector().connectorId())
                        && (request.installationId() == null
                                || request.installationId().equals(item.id())))
                .findFirst()
                .orElseThrow(() ->
                        new ConnectorException("plugin_not_installed", "Plugin is not installed for this tenant"));
        if (!installation.enabled()) throw new ConnectorException("plugin_disabled", "Plugin installation is disabled");
        return request.connectionId() == null
                ? new ResolvedConnection(Map.of(), Map.of())
                : connections.resolve(request.connectionId(), request.context().tenantId(), installation.id());
    }
}
