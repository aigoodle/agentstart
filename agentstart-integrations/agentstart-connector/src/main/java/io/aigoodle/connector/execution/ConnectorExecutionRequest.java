package io.aigoodle.connector.execution;

import io.aigoodle.connector.ConnectorKey;
import java.util.Map;

public record ConnectorExecutionRequest(
        ConnectorKey connector,
        String actionId,
        String installationId,
        String connectionId,
        Map<String, Object> arguments,
        ConnectorExecutionContext context) {

    public ConnectorExecutionRequest {
        if (connector == null) throw new IllegalArgumentException("connector is required");
        if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("actionId is required");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        context = context == null ? ConnectorExecutionContext.anonymous() : context;
    }
}
