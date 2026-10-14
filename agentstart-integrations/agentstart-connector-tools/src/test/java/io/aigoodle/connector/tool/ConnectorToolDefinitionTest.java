package io.aigoodle.connector.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.connector.*;
import io.aigoodle.connector.execution.*;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectorToolDefinitionTest {
    @Test
    void propagatesCallerContextAndAllowsMissingConversationId() {
        AtomicReference<ConnectorExecutionRequest> captured = new AtomicReference<>();
        ConnectorExecutionGateway gateway = request -> {
            captured.set(request);
            return ConnectorResult.success(Map.of("ok", true));
        };
        ConnectorActionDefinition action = new ConnectorActionDefinition(
                "send", "Send", "Send", null, null, false, null, ConnectorRiskLevel.WRITE, Map.of());
        ConnectorDefinition connector = new ConnectorDefinition(
                new ConnectorKey("openclaw", "qq"),
                "QQ",
                "QQ",
                "1",
                ConnectorSource.OPENCLAW,
                null,
                "chat",
                null,
                List.of(action),
                ConnectorTrustLevel.REVIEWED,
                "MIT",
                Map.of());
        ConnectorToolDefinition tool = new ConnectorToolDefinition(connector, action, gateway);

        Object result = tool.execute(
                Map.of("text", "hello"), new ToolExecutionContext("exec-1", "tenant-1", "user-1", null, Map.of()));

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(captured.get().context().tenantId()).isEqualTo("tenant-1");
        assertThat(captured.get().context().attributes()).containsEntry("tool", tool.name());
    }
}
