package io.aigoodle.plugin.runtime;

import io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;

@FunctionalInterface
public interface PluginConnectionResolver {
    ResolvedConnection resolve(ConnectorExecutionRequest request);
}
