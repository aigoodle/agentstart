package io.aigoodle.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aigoodle.connector.execution.*;
import io.aigoodle.connector.persistence.ConnectorExecutionEntity;
import io.aigoodle.connector.persistence.ConnectorExecutionMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorExecutionRecorderTest {
    @Test
    void recordsTenantAndWorkflowContextWithoutArgumentsOrSecrets() {
        ConnectorExecutionMapper mapper = mock(ConnectorExecutionMapper.class);
        ConnectorExecutionRecorder recorder = new ConnectorExecutionRecorder(mapper);
        ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                new ConnectorKey("openclaw", "qq"),
                "send",
                "install-1",
                "connection-1",
                Map.of("token", "must-not-be-audited"),
                new ConnectorExecutionContext(
                        "exec-1", "tenant-1", "user-1", "agent-1", "workflow-1", "run-1", "node-1", Map.of()));

        recorder.record(request, 12, ConnectorResult.success(Map.of("ok", true)), null);

        ArgumentCaptor<ConnectorExecutionEntity> captor = ArgumentCaptor.forClass(ConnectorExecutionEntity.class);
        verify(mapper).insert(captor.capture());
        ConnectorExecutionEntity entity = captor.getValue();
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getTenantId()).isEqualTo("tenant-1");
        assertThat(entity.getWorkflowId()).isEqualTo("workflow-1");
        assertThat(entity.getConnectionId()).isEqualTo("connection-1");
        assertThat(entity.toString()).doesNotContain("must-not-be-audited");
    }
}
