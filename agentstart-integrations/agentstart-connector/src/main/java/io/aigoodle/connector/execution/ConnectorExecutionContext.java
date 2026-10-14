package io.aigoodle.connector.execution;

import java.util.Map;

public record ConnectorExecutionContext(
        String executionId,
        String tenantId,
        String userId,
        String agentId,
        String workflowId,
        String runId,
        String nodeId,
        Map<String, Object> attributes) {

    public ConnectorExecutionContext {
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ConnectorExecutionContext anonymous() {
        return new ConnectorExecutionContext(null, "default", null, null, null, null, null, Map.of());
    }
}
