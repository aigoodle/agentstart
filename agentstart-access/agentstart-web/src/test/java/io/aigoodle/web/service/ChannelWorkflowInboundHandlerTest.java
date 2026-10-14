package io.aigoodle.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelEventLogService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundResult;
import io.aigoodle.channel.ChannelReplyStream;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.dispatch.DispatchResult;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerInvocationRequest;
import io.aigoodle.trigger.service.TriggerService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelWorkflowInboundHandlerTest {
    @Test
    void streamsWorkflowChunksBackThroughLiveChannelReply() {
        TriggerService triggers = mock(TriggerService.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelEventLogService events = mock(ChannelEventLogService.class);
        ChannelReplyStream stream = mock(ChannelReplyStream.class);
        ChannelWorkflowInboundHandler handler = new ChannelWorkflowInboundHandler(triggers, connections, events);
        ChannelInboundEvent event = eventWithStream(stream);
        TriggerEntity trigger = trigger("trigger-stream", "workflow-stream");
        when(connections.ownership("native", null, "wecom", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership("connection-1", "tenant-1", "user-1", null));
        when(connections.get("connection-1", "tenant-1")).thenReturn(connection("wecom"));
        when(triggers.listEnabledByType("tenant-1", TriggerType.CHANNEL_MESSAGE))
                .thenReturn(List.of(trigger));
        when(triggers.config(trigger))
                .thenReturn(Map.of("provider", "native", "channelId", "wecom", "replyMode", "ASYNC"));
        when(triggers.fireAsynchronouslyAs(
                        eq("tenant-1"),
                        eq("user-1"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> chunks = invocation.getArgument(3);
                    java.util.function.Consumer<DispatchResult> completion = invocation.getArgument(4);
                    chunks.accept("你好");
                    completion.accept(DispatchResult.ok("run-1", Map.of("answer", "你好，世界")));
                    return "invocation-1";
                });

        ChannelInboundResult result = handler.tryHandle(event).orElseThrow();

        assertThat(result.code()).isEqualTo("workflow_trigger_accepted");
        verify(stream).start();
        verify(stream).push("你好");
        verify(stream).complete("你好，世界");
        org.mockito.Mockito.verify(events, org.mockito.Mockito.never())
                .workflowReply(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sharedWorkflowRunsAsVerifiedEmployeeResolvedFromPlatformSender() {
        TriggerService triggers = mock(TriggerService.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelWorkflowInboundHandler handler =
                new ChannelWorkflowInboundHandler(triggers, connections, null, identities);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "native",
                "qqbot",
                "runtime-account-1",
                "message-1",
                "qq-openid-employee-2",
                "conversation-1",
                "hello",
                "TEXT",
                List.of(),
                Map.of(),
                Instant.now(),
                false,
                Map.of(),
                "native-default");
        var ownership =
                new ChannelConnectionService.Ownership("connection-1", "tenant-1", "account-owner", "native-default");
        var connection = new ChannelConnectionService.View(
                "connection-1",
                "tenant-1",
                "USER",
                "account-owner",
                "native",
                "qqbot",
                "QQ",
                "ACTIVE",
                "ONLINE",
                "runtime-account-1",
                null,
                null,
                "native-default",
                Map.of(),
                true,
                null,
                null,
                1L);
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId("trigger-1");
        trigger.setTenantId("tenant-1");
        trigger.setType(TriggerType.CHANNEL_MESSAGE);
        trigger.setTargetType("workflow");
        trigger.setTargetId("shared-workflow");
        trigger.setEnabled(true);
        when(connections.ownership("native", "native-default", "qqbot", "runtime-account-1"))
                .thenReturn(ownership);
        when(connections.get("connection-1", "tenant-1")).thenReturn(connection);
        when(triggers.listEnabledByType("tenant-1", TriggerType.CHANNEL_MESSAGE))
                .thenReturn(List.of(trigger));
        when(triggers.config(trigger))
                .thenReturn(Map.of("provider", "native", "channelId", "qqbot", "replyMode", "ASYNC"));
        when(identities.resolve("tenant-1", event))
                .thenReturn(new ChannelIdentityService.Identity(
                        "identity-1",
                        "tenant-1",
                        "native",
                        "qqbot",
                        "runtime-account-1",
                        "qq-openid-employee-2",
                        "employee-2",
                        "VERIFIED",
                        true));
        when(triggers.fireAsynchronouslyAs(eq("tenant-1"), eq("employee-2"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("invocation-1");

        assertThat(handler.tryHandle(event)).isPresent();

        ArgumentCaptor<TriggerInvocationRequest> request = ArgumentCaptor.forClass(TriggerInvocationRequest.class);
        verify(triggers).fireAsynchronouslyAs(eq("tenant-1"), eq("employee-2"), request.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> message =
                (Map<String, Object>) request.getValue().payload().get("message");
        assertThat(message)
                .containsEntry("userId", "employee-2")
                .containsEntry("accountOwnerId", "account-owner")
                .containsEntry("senderId", "qq-openid-employee-2");
    }

    private static ChannelInboundEvent eventWithStream(ChannelReplyStream stream) {
        return new ChannelInboundEvent(
                "native",
                "wecom",
                "account-1",
                "message-1",
                "sender-1",
                "conversation-1",
                "你好",
                "TEXT",
                List.of(),
                Map.of(),
                Instant.now(),
                false,
                Map.of("replyTargetId", "sender-1", ChannelReplyStream.METADATA_KEY, stream));
    }

    private static TriggerEntity trigger(String id, String workflowId) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId(id);
        trigger.setTenantId("tenant-1");
        trigger.setType(TriggerType.CHANNEL_MESSAGE);
        trigger.setTargetType("workflow");
        trigger.setTargetId(workflowId);
        trigger.setEnabled(true);
        return trigger;
    }

    private static ChannelConnectionService.View connection(String channelId) {
        return new ChannelConnectionService.View(
                "connection-1",
                "tenant-1",
                "USER",
                "user-1",
                "native",
                channelId,
                "企业微信",
                "ACTIVE",
                "ONLINE",
                "account-1",
                null,
                null,
                null,
                Map.of(),
                true,
                null,
                null,
                1L);
    }

    @Test
    void asynchronousWorkflowCompletionQueuesReplyToOriginalChannelEvent() {
        TriggerService triggers = mock(TriggerService.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelEventLogService events = mock(ChannelEventLogService.class);
        ChannelWorkflowInboundHandler handler = new ChannelWorkflowInboundHandler(triggers, connections, events);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "native",
                "qqbot",
                "runtime-account-1",
                "message-1",
                "external-user-1",
                "group-1",
                "hello",
                "TEXT",
                List.of(),
                Map.of(),
                Instant.now(),
                true,
                Map.of("replyTargetId", "group-1"),
                "native-default");
        var ownership = new ChannelConnectionService.Ownership("connection-1", "tenant-1", "user-1", "native-default");
        var connection = new ChannelConnectionService.View(
                "connection-1",
                "tenant-1",
                "USER",
                "user-1",
                "native",
                "qqbot",
                "QQ",
                "ACTIVE",
                "ONLINE",
                "runtime-account-1",
                null,
                null,
                "native-default",
                Map.of(),
                true,
                null,
                null,
                1L);
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId("trigger-1");
        trigger.setTenantId("tenant-1");
        trigger.setType(TriggerType.CHANNEL_MESSAGE);
        trigger.setTargetType("workflow");
        trigger.setTargetId("workflow-1");
        trigger.setEnabled(true);
        Map<String, Object> config = Map.of(
                "provider", "native", "channelId", "qqbot", "connectionId", "connection-1", "replyMode", "ASYNC");
        when(connections.ownership("native", "native-default", "qqbot", "runtime-account-1"))
                .thenReturn(ownership);
        when(connections.get("connection-1", "tenant-1")).thenReturn(connection);
        when(triggers.listEnabledByType("tenant-1", TriggerType.CHANNEL_MESSAGE))
                .thenReturn(List.of(trigger));
        when(triggers.config(trigger)).thenReturn(config);
        when(triggers.fireAsynchronouslyAs(
                        eq("tenant-1"),
                        eq("user-1"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<DispatchResult> completion = invocation.getArgument(3);
                    completion.accept(DispatchResult.ok("run-1", Map.of("answer", "workflow reply")));
                    return "invocation-1";
                });

        ChannelInboundResult result = handler.tryHandle(event).orElseThrow();

        assertThat(result.code()).isEqualTo("workflow_trigger_accepted");
        verify(events).workflowReply(event, "workflow reply", "workflow-reply:run-1");
    }

    @Test
    void selectedMessageConnectorExecutesWorkflowWithAccountIdentityTriggerObject() {
        TriggerService triggers = mock(TriggerService.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelWorkflowInboundHandler handler = new ChannelWorkflowInboundHandler(triggers, connections);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "native",
                "qqbot",
                "runtime-account-1",
                "message-1",
                "external-user-1",
                "conversation-1",
                "你好",
                "TEXT",
                List.of(),
                Map.of(),
                Instant.parse("2026-08-21T08:00:00Z"),
                false,
                Map.of("replyTargetId", "external-user-1"),
                "native-default");
        ChannelConnectionService.Ownership ownership =
                new ChannelConnectionService.Ownership("connection-1", "tenant-1", "user-1", "native-default");
        ChannelConnectionService.View connection = new ChannelConnectionService.View(
                "connection-1",
                "tenant-1",
                "USER",
                "user-1",
                "native",
                "qqbot",
                "招生机器人",
                "ACTIVE",
                "ONLINE",
                "runtime-account-1",
                null,
                null,
                "native-default",
                Map.of(),
                true,
                null,
                null,
                1L);
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId("trigger-1");
        trigger.setTenantId("tenant-1");
        trigger.setType(TriggerType.CHANNEL_MESSAGE);
        trigger.setTargetType("workflow");
        trigger.setTargetId("workflow-1");
        trigger.setEnabled(true);
        trigger.setConfigJson(JsonUtils.toJson(Map.of(
                "provider",
                "native",
                "channelId",
                "qqbot",
                "channelName",
                "QQ Bot",
                "connectionId",
                "connection-1",
                "messageTypes",
                List.of("TEXT"))));
        when(connections.ownership("native", "native-default", "qqbot", "runtime-account-1"))
                .thenReturn(ownership);
        when(connections.get("connection-1", "tenant-1")).thenReturn(connection);
        when(triggers.listEnabledByType("tenant-1", TriggerType.CHANNEL_MESSAGE))
                .thenReturn(List.of(trigger));
        when(triggers.config(trigger)).thenReturn(JsonUtils.parseMap(trigger.getConfigJson()));
        when(triggers.fireAsynchronouslyAs(eq("tenant-1"), eq("user-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("invocation-1");

        ChannelInboundResult result = handler.tryHandle(event).orElseThrow();

        assertThat(result.handled()).isTrue();
        assertThat(result.reply()).isNull();
        assertThat(result.metadata())
                .containsEntry("triggerInvocationId", "invocation-1")
                .containsEntry("routeSource", "WORKFLOW_TRIGGER");
        ArgumentCaptor<TriggerInvocationRequest> request = ArgumentCaptor.forClass(TriggerInvocationRequest.class);
        verify(triggers).fireAsynchronouslyAs(eq("tenant-1"), eq("user-1"), request.capture());
        assertThat(request.getValue().conversationId()).isEqualTo("channel:tenant-1:connection-1:conversation-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeTrigger =
                (Map<String, Object>) request.getValue().payload().get("triggers");
        assertThat(runtimeTrigger)
                .containsEntry("channelId", "qqbot")
                .containsEntry("channelName", "QQ Bot")
                .containsEntry("instanceName", "招生机器人")
                .containsEntry("accountId", "runtime-account-1")
                .doesNotContainKeys(
                        "senderId",
                        "replyTargetId",
                        "conversationId",
                        "messageId",
                        "messageType",
                        "group",
                        "tenantId",
                        "userId",
                        "ownerId",
                        "ownerType");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeMessage =
                (Map<String, Object>) request.getValue().payload().get("message");
        assertThat(runtimeMessage)
                .containsEntry("senderId", "external-user-1")
                .containsEntry("replyTargetId", "external-user-1")
                .containsEntry("conversationId", "conversation-1")
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("userId", "user-1")
                .containsEntry("ownerId", "user-1")
                .containsEntry("ownerType", "USER")
                .containsEntry("id", "message-1")
                .containsEntry("type", "TEXT");
        assertThat(request.getValue().payload())
                .containsEntry("_channel_conversation", true)
                .containsEntry("_conversation_sender_id", "external-user-1");
    }
}
