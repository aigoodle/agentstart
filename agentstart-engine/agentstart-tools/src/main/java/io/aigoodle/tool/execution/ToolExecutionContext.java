package io.aigoodle.tool.execution;

import java.util.Map;

/** Caller identity and metadata propagated without coupling tools to an agent runtime. */
public record ToolExecutionContext(
        String executionId, String tenantId, String ownerId, String conversationId, Map<String, Object> metadata) {

    public ToolExecutionContext {
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolExecutionContext anonymous() {
        return new ToolExecutionContext(null, "default", null, null, Map.of());
    }
}
