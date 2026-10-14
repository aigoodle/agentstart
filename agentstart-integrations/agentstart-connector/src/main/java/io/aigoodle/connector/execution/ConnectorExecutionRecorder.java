package io.aigoodle.connector.execution;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.persistence.ConnectorExecutionEntity;
import io.aigoodle.connector.persistence.ConnectorExecutionMapper;
import java.time.LocalDateTime;

/** Best-effort execution audit writer; business results never depend on audit storage availability. */
public class ConnectorExecutionRecorder {
    private final ConnectorExecutionMapper mapper;

    public ConnectorExecutionRecorder(ConnectorExecutionMapper mapper) {
        this.mapper = mapper;
    }

    public void record(ConnectorExecutionRequest request, long durationMs, ConnectorResult result, Throwable failure) {
        try {
            ConnectorExecutionEntity entity = new ConnectorExecutionEntity();
            entity.setTenantId(request.context().tenantId());
            entity.setProvider(request.connector().provider());
            entity.setConnectorId(request.connector().connectorId());
            entity.setActionId(request.actionId());
            entity.setInstallationId(request.installationId());
            entity.setConnectionId(request.connectionId());
            entity.setAgentId(request.context().agentId());
            entity.setWorkflowId(request.context().workflowId());
            entity.setRunId(request.context().runId());
            entity.setNodeId(request.context().nodeId());
            entity.setDurationMs(durationMs);
            entity.setAttempts(1);
            entity.setCreatedAt(LocalDateTime.now());
            boolean success = failure == null && result != null && result.success();
            entity.setStatus(success ? "SUCCESS" : "FAILED");
            if (result != null && result.error() != null) {
                entity.setErrorCode(result.error().code());
                entity.setErrorMessage(result.error().message());
            } else if (failure != null) {
                entity.setErrorCode(
                        failure instanceof ConnectorException connector ? connector.code() : "connector_exception");
                entity.setErrorMessage(failure.getMessage());
            }
            mapper.insert(entity);
        } catch (RuntimeException ignored) {
            // Auditing must not turn a successful external side effect into an apparent failure.
        }
    }
}
