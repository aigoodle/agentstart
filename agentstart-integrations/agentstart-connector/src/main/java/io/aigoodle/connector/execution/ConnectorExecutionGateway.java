package io.aigoodle.connector.execution;

@FunctionalInterface
public interface ConnectorExecutionGateway {
    ConnectorResult execute(ConnectorExecutionRequest request);
}
