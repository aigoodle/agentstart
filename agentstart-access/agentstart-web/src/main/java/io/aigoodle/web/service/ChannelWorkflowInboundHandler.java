package io.aigoodle.web.service;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundHandler;
import io.aigoodle.channel.ChannelInboundResult;
import io.aigoodle.channel.ChannelReplyStream;
import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerInvocationRequest;
import io.aigoodle.trigger.service.TriggerService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Routes a channel message selected by a published START node into the workflow trigger engine. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnBean({TriggerService.class, ChannelConnectionService.class})
public class ChannelWorkflowInboundHandler implements ChannelInboundHandler {
    private final TriggerService triggers;
    private final ChannelConnectionService connections;
    private final io.aigoodle.channel.ChannelEventLogService events;
    private final ChannelIdentityService identities;

    public ChannelWorkflowInboundHandler(TriggerService triggers, ChannelConnectionService connections) {
        this(triggers, connections, null, null);
    }

    public ChannelWorkflowInboundHandler(
            TriggerService triggers,
            ChannelConnectionService connections,
            io.aigoodle.channel.ChannelEventLogService events) {
        this(triggers, connections, events, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChannelWorkflowInboundHandler(
            TriggerService triggers,
            ChannelConnectionService connections,
            @org.springframework.context.annotation.Lazy io.aigoodle.channel.ChannelEventLogService events,
            ChannelIdentityService identities) {
        this.triggers = triggers;
        this.connections = connections;
        this.events = events;
        this.identities = identities;
    }

    @Override
    public boolean supports(ChannelInboundEvent event) {
        return selection(event).isPresent();
    }

    @Override
    public ChannelInboundResult handle(ChannelInboundEvent event) {
        return selection(event).map(selected -> execute(selected, event)).orElseGet(ChannelInboundResult::unhandled);
    }

    @Override
    public Optional<ChannelInboundResult> tryHandle(ChannelInboundEvent event) {
        return selection(event).map(selected -> execute(selected, event));
    }

    private Optional<Selection> selection(ChannelInboundEvent event) {
        ChannelConnectionService.Ownership ownership =
                connections.ownership(event.provider(), event.runtimeNodeId(), event.channelId(), event.accountId());
        if (ownership == null) return Optional.empty();
        ChannelConnectionService.View connection = connections.get(ownership.connectionId(), ownership.tenantId());
        if (!"ACTIVE".equals(connection.desiredStatus())) return Optional.empty();
        List<TriggerEntity> matching =
                triggers.listEnabledByType(ownership.tenantId(), TriggerType.CHANNEL_MESSAGE).stream()
                        .filter(trigger -> matches(trigger, connection, event))
                        .toList();
        if (matching.isEmpty()) return Optional.empty();
        if (matching.size() > 1) {
            throw new IllegalStateException("Multiple message workflows match connection " + connection.id());
        }
        return Optional.of(new Selection(matching.getFirst(), connection));
    }

    private boolean matches(
            TriggerEntity trigger, ChannelConnectionService.View connection, ChannelInboundEvent event) {
        Map<String, Object> config = triggers.config(trigger);
        if (!same(config.get("provider"), event.provider()) || !same(config.get("channelId"), event.channelId()))
            return false;
        String selectedConnection = text(config.get("connectionId"));
        if (selectedConnection != null && !selectedConnection.equals(connection.id())) return false;
        Object configuredTypes = config.get("messageTypes");
        if (!(configuredTypes instanceof List<?> types) || types.isEmpty()) return true;
        return types.stream().map(String::valueOf).anyMatch(type -> type.equalsIgnoreCase(event.messageType()));
    }

    private ChannelInboundResult execute(Selection selected, ChannelInboundEvent event) {
        ChannelReplyStream availableStream = ChannelReplyStream.from(event);
        String configuredReplyMode = text(triggers.config(selected.trigger).get("replyMode"));
        ChannelReplyStream replyStream = "NONE".equalsIgnoreCase(configuredReplyMode) ? null : availableStream;
        if (replyStream != null) replyStream.start();
        Map<String, Object> payload = payload(selected, event);
        String externalConversationId = text(event.conversationId());
        if (externalConversationId == null) externalConversationId = text(event.senderId());
        if (externalConversationId == null) externalConversationId = "unknown";
        String conversationId = String.join(
                ":", "channel", selected.connection.tenantId(), selected.connection.id(), externalConversationId);
        TriggerInvocationRequest request =
                new TriggerInvocationRequest(selected.trigger.getId(), payload, "channel_message", conversationId);
        String executionUserId = executionUserId(selected.connection, event);
        String invocationId;
        if (replyStream != null) {
            invocationId = triggers.fireAsynchronouslyAs(
                    selected.connection.tenantId(),
                    executionUserId,
                    request,
                    replyStream::push,
                    result -> replyWhenCompleted(selected, event, result, replyStream));
        } else if (events == null) {
            invocationId = triggers.fireAsynchronouslyAs(selected.connection.tenantId(), executionUserId, request);
        } else {
            invocationId = triggers.fireAsynchronouslyAs(
                    selected.connection.tenantId(),
                    executionUserId,
                    request,
                    result -> replyWhenCompleted(selected, event, result, null));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("managed", true);
        metadata.put("routeSource", "WORKFLOW_TRIGGER");
        metadata.put("triggerId", selected.trigger.getId());
        metadata.put("workflowId", selected.trigger.getTargetId());
        metadata.put("connectionId", selected.connection.id());
        if (invocationId != null) metadata.put("triggerInvocationId", invocationId);
        return new ChannelInboundResult(true, null, "workflow_trigger_accepted", metadata);
    }

    private void replyWhenCompleted(
            Selection selected,
            ChannelInboundEvent event,
            io.aigoodle.trigger.dispatch.DispatchResult result,
            ChannelReplyStream replyStream) {
        if (!result.isSuccess()) {
            if (replyStream != null) replyStream.fail(result.getError());
            return;
        }
        Map<String, Object> config = triggers.config(selected.trigger);
        String replyMode = text(config.get("replyMode"));
        if ("NONE".equalsIgnoreCase(replyMode)) return;
        String content = reply(result.getOutputs());
        if (replyStream != null) {
            replyStream.complete(content);
            return;
        }
        if (content == null) return;
        if (events == null) return;
        events.workflowReply(event, content, "workflow-reply:" + result.getRunId());
    }

    private Map<String, Object> payload(Selection selected, ChannelInboundEvent event) {
        ChannelConnectionService.View connection = selected.connection;
        Map<String, Object> config = triggers.config(selected.trigger);
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "connector");
        trigger.put("triggerId", selected.trigger.getId());
        trigger.put("workflowId", selected.trigger.getTargetId());
        trigger.put("provider", event.provider());
        trigger.put("channelId", event.channelId());
        trigger.put("channelName", value(config.get("channelName"), event.channelId()));
        trigger.put("connectionId", connection.id());
        trigger.put("connectionName", connection.name());
        trigger.put("instanceName", connection.name());
        trigger.put("accountId", event.accountId());
        trigger.put("runtimeAccountId", connection.runtimeAccountId());
        trigger.put("runtimeNodeId", connection.runtimeNodeId());
        trigger.put("agentId", connection.agentId());
        trigger.put("agentVersionId", connection.agentVersionId());
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", event.messageId());
        message.put("type", event.messageType());
        message.put("content", event.content());
        message.put("attachments", new ArrayList<>(event.attachments()));
        message.put("payload", event.contentPayload());
        message.put("senderId", event.senderId());
        message.put("replyTargetId", value(event.metadata().get("replyTargetId"), event.senderId()));
        message.put("tenantId", connection.tenantId());
        message.put("userId", executionUserId(connection, event));
        message.put("accountOwnerId", connection.ownerId());
        message.put("ownerId", connection.ownerId());
        message.put("ownerType", connection.ownerType());
        message.put("conversationId", event.conversationId());
        message.put("group", event.group());
        message.put(
                "timestamp",
                event.timestamp() == null ? null : event.timestamp().toString());
        Map<String, Object> durableMetadata = new LinkedHashMap<>(event.metadata());
        durableMetadata.remove(ChannelReplyStream.METADATA_KEY);
        message.put("metadata", Map.copyOf(durableMetadata));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("triggers", trigger);
        payload.put("message", message);
        payload.put("query", event.content());
        // Workflow chat transports persist history themselves. Channel triggers
        // bypass that transport, so mark them for workflow-level conversation
        // creation and LLM exchange persistence.
        payload.put("_channel_conversation", true);
        payload.put("_conversation_sender_id", event.senderId());
        return payload;
    }

    private String executionUserId(ChannelConnectionService.View connection, ChannelInboundEvent event) {
        if (identities == null) return connection.ownerId();
        ChannelIdentityService.Identity identity = identities.resolve(connection.tenantId(), event);
        if (identity == null || !identity.enabled() || !"VERIFIED".equalsIgnoreCase(identity.verificationStatus())) {
            return connection.ownerId();
        }
        return identity.enterpriseUserId();
    }

    private static String reply(Map<String, Object> outputs) {
        if (outputs == null) return null;
        for (String key : List.of("answer", "text", "reply", "content")) {
            String value = text(outputs.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private static boolean same(Object left, String right) {
        return left != null && right != null && String.valueOf(left).equalsIgnoreCase(right);
    }

    private static Object value(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private record Selection(TriggerEntity trigger, ChannelConnectionService.View connection) {}
}
