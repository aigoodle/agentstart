package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelRuntimeProvider;
import io.aigoodle.channel.ChannelRuntimeRegistry;
import io.aigoodle.channel.ChannelSendResult;
import io.aigoodle.connector.execution.ConnectorResult;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorNodeExecutorTest {
    @Test
    void dispatchesProviderNeutralConnectorWithResolvedVariables() {
        ConnectorNodeExecutor executor = new ConnectorNodeExecutor(request -> {
            assertThat(request.connector().externalForm()).isEqualTo("openclaw:qq-bot");
            assertThat(request.arguments()).containsEntry("message", "hello Ada");
            return ConnectorResult.success(Map.of("messageId", "m-1"));
        });
        ExecutionContext context = ExecutionContext.start(Map.of("name", "Ada"), "c-1", null);
        context.setTenantId("acme");
        NodeDef node = NodeDef.of("send", NodeType.CONNECTOR)
                .with("provider", "openclaw")
                .with("connectorId", "qq-bot")
                .with("actionId", "send_message")
                .with("inputs", Map.of("message", "hello {{#sys.name#}}"));

        assertThat(executor.execute(node, context).getOutputs().get("result"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("success", true)
                .containsEntry("provider", "openclaw")
                .containsEntry("data", Map.of("messageId", "m-1"));
    }

    @Test
    void mapsGenericTextInputAndResolvesNestedVariables() {
        ConnectorNodeExecutor executor = new ConnectorNodeExecutor(request -> {
            assertThat(request.arguments()).containsEntry("query", "summarize Ada");
            assertThat(request.arguments().get("options"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("owner", "Ada");
            return ConnectorResult.success("ok");
        });
        ExecutionContext context = ExecutionContext.start(Map.of("name", "Ada"), "c-1", null);
        NodeDef node = NodeDef.of("call", NodeType.CONNECTOR)
                .with("provider", "openclaw")
                .with("connectorId", "tool")
                .with("actionId", "run")
                .with("inputField", "query")
                .with("inputText", "summarize {{#sys.name#}}")
                .with("inputs", Map.of("options", Map.of("owner", "{{#sys.name#}}")));
        assertThat(executor.execute(node, context).isFailed()).isFalse();
    }

    @Test
    void sendsThroughTheSameAccountAndSenderSelectedByStartTrigger() {
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelRuntimeProvider runtime = mock(ChannelRuntimeProvider.class);
        when(runtime.type()).thenReturn("openclaw");
        when(runtime.nodeId()).thenReturn("openclaw-default");
        when(runtime.sendWithResult(any())).thenReturn(new ChannelSendResult("qq-message-2", Map.of()));
        when(connections.get("connection-1", "tenant-1"))
                .thenReturn(new ChannelConnectionService.View(
                        "connection-1",
                        "tenant-1",
                        "USER",
                        "owner-1",
                        "openclaw",
                        "qqbot",
                        "招生机器人",
                        "ACTIVE",
                        "ONLINE",
                        "runtime-account-1",
                        null,
                        null,
                        "openclaw-default",
                        Map.of(),
                        true,
                        null,
                        null,
                        1L));
        ConnectorNodeExecutor executor = new ConnectorNodeExecutor(
                request -> null, connections, new ChannelRuntimeRegistry(java.util.List.of(runtime)));
        ExecutionContext context = ExecutionContext.start(Map.of(), "conversation-1", null);
        context.setTenantId("tenant-1");
        context.setUserId("owner-1");
        context.getPool()
                .putAll(
                        "start",
                        Map.of(
                                "triggers", Map.of("type", "connector", "connectionId", "connection-1"),
                                "message",
                                        Map.of(
                                                "senderId",
                                                "qq-user-1",
                                                "replyTargetId",
                                                "qq-user-1",
                                                "conversationId",
                                                "qq-conversation-1")));
        NodeDef node = NodeDef.of("reply", NodeType.CONNECTOR)
                .with("connectorMode", "CHANNEL_MESSAGE")
                .with("channelSource", "REPLY_TRIGGER")
                .with("messageType", "TEXT")
                .with("messageContent", "处理完成");

        assertThat(executor.execute(node, context).getOutputs().get("result"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("messageId", "qq-message-2")
                .containsEntry("targetId", "qq-user-1");
        var outbound = org.mockito.ArgumentCaptor.forClass(io.aigoodle.channel.ChannelOutboundMessage.class);
        verify(runtime).sendWithResult(outbound.capture());
        assertThat(outbound.getValue().accountId()).isEqualTo("runtime-account-1");
        assertThat(outbound.getValue().targetId()).isEqualTo("qq-user-1");
    }

    @Test
    void rejectsDynamicPersonalAccountOwnedByAnotherUser() {
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.get("private-connection", "tenant-1"))
                .thenReturn(new ChannelConnectionService.View(
                        "private-connection",
                        "tenant-1",
                        "USER",
                        "other-user",
                        "openclaw",
                        "qqbot",
                        "私人机器人",
                        "ACTIVE",
                        "ONLINE",
                        "account-1",
                        null,
                        null,
                        "openclaw-default",
                        Map.of(),
                        true,
                        null,
                        null,
                        1L));
        ConnectorNodeExecutor executor = new ConnectorNodeExecutor(
                request -> null, connections, new ChannelRuntimeRegistry(java.util.List.of()));
        ExecutionContext context =
                ExecutionContext.start(Map.of("connection", "private-connection", "target", "u-1"), "c-1", null);
        context.setTenantId("tenant-1");
        context.setUserId("current-user");
        NodeDef node = NodeDef.of("send", NodeType.CONNECTOR)
                .with("connectorMode", "CHANNEL_MESSAGE")
                .with("channelSource", "DYNAMIC")
                .with("channelConnectionId", "{{#sys.connection#}}")
                .with("targetId", "{{#sys.target#}}")
                .with("messageContent", "hello");

        var result = executor.execute(node, context);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).contains("cannot use");
    }
}
