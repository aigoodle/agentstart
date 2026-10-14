package io.aigoodle.connector.provider;

import io.aigoodle.connector.ConnectorDefinition;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.execution.ConnectorResult;
import java.util.List;

/** Adapter boundary for native channels, MCP, n8n and future connector ecosystems. */
public interface ConnectorProvider {
    String type();

    List<ConnectorDefinition> discover();

    ConnectorResult execute(ConnectorExecutionRequest request);

    default long revision() {
        return 0L;
    }
}
