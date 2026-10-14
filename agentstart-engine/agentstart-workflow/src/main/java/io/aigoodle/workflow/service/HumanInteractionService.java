package io.aigoodle.workflow.service;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.workflow.engine.WorkflowRunOptions;
import io.aigoodle.workflow.entity.HumanInteractionEntity;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Query and idempotent submission facade for chat, public forms and messaging hooks. */
public class HumanInteractionService {
    private final HumanInteractionStore store;
    private final PersistentWorkflowRunner runner;
    private final MemoryManager memory;

    public HumanInteractionService(HumanInteractionStore store, PersistentWorkflowRunner runner, MemoryManager memory) {
        this.store = store;
        this.runner = runner;
        this.memory = memory;
    }

    public Map<String, Object> requirePublic(String accessToken) {
        HumanInteractionEntity value = store.byAccessToken(accessToken);
        if (value == null)
            throw new PlatformException("interaction_not_found", "Human interaction was not found", null);
        return view(value);
    }

    public List<Map<String, Object>> conversation(String tenantId, String conversationId) {
        return store.byConversation(tenantId, conversationId).stream()
                .map(HumanInteractionService::view)
                .toList();
    }

    public Submission submitPublic(String accessToken, Map<String, Object> values, String submittedText) {
        HumanInteractionEntity value = store.byAccessToken(accessToken);
        if (value == null)
            throw new PlatformException("interaction_not_found", "Human interaction was not found", null);
        return submit(value, values, submittedText, "human:public");
    }

    public HumanInteractionEntity matchChannel(
            String tenantId, String connectionId, String senderId, String shortCode) {
        if (shortCode != null) return store.pendingChannel(tenantId, connectionId, senderId, shortCode);
        List<HumanInteractionEntity> pending = store.pendingChannel(tenantId, connectionId, senderId);
        return pending.size() == 1 ? pending.getFirst() : null;
    }

    public Submission submitChannel(HumanInteractionEntity value, String messageId, String text, String senderId) {
        if (value == null)
            throw new PlatformException("interaction_not_found", "Human interaction was not found", null);
        return submit(
                value,
                channelValues(value, text),
                text,
                "channel:" + (senderId == null ? "unknown" : senderId),
                messageId);
    }

    private static Map<String, Object> channelValues(HumanInteractionEntity value, String text) {
        Map<String, Object> schema =
                value.getInputSchemaJson() == null ? Map.of() : JsonUtils.parseMap(value.getInputSchemaJson());
        if (schema.get("properties") instanceof Map<?, ?> properties && properties.size() == 1) {
            return Map.of(String.valueOf(properties.keySet().iterator().next()), text == null ? "" : text);
        }
        return Map.of("text", text == null ? "" : text);
    }

    private Submission submit(
            HumanInteractionEntity value, Map<String, Object> values, String submittedText, String actor) {
        return submit(value, values, submittedText, actor, null);
    }

    private Submission submit(
            HumanInteractionEntity value,
            Map<String, Object> values,
            String submittedText,
            String actor,
            String externalEventId) {
        if ("SUBMITTED".equals(value.getStatus())) return new Submission(false, true, view(value), null);
        if (value.getExpiresAt() != null && !value.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new PlatformException("interaction_expired", "Human interaction has expired", null);
        if (!store.beginSubmit(value, values == null ? Map.of() : values, submittedText, actor)) {
            HumanInteractionEntity latest = store.byId(value.getId());
            return new Submission(
                    false,
                    latest != null && "SUBMITTED".equals(latest.getStatus()),
                    latest == null ? Map.of() : view(latest),
                    null);
        }
        try {
            WorkflowSignalResult result = runner.signalTrusted(
                    value.getTenantId(),
                    value.getRunId(),
                    externalEventId == null
                            ? "human-interaction:" + value.getId()
                            : "channel-human:" + value.getId() + ":" + externalEventId,
                    values,
                    actor,
                    WorkflowRunOptions.defaults());
            store.finishSubmit(value.getId(), result.accepted() || result.duplicate());
            if (result.accepted()) remember(value, submittedText, result.runResult());
            return new Submission(
                    result.accepted(), result.duplicate(), view(store.byId(value.getId())), result.runResult());
        } catch (RuntimeException failure) {
            store.finishSubmit(value.getId(), false);
            throw failure;
        }
    }

    private void remember(HumanInteractionEntity interaction, String submittedText, Object runResult) {
        if (memory == null || interaction.getAppId() == null || interaction.getConversationId() == null) return;
        String answer = "";
        if (runResult instanceof io.aigoodle.workflow.engine.WorkflowRunResult run && run.getOutputs() != null) {
            for (String key : List.of("answer", "text", "output", "result")) {
                if (run.getOutputs().get(key) != null) {
                    answer = String.valueOf(run.getOutputs().get(key));
                    break;
                }
            }
            if (answer.isBlank() && run.getOutputs().size() == 1)
                answer = String.valueOf(run.getOutputs().values().iterator().next());
        }
        memory.rememberExchange(
                interaction.getTenantId(),
                interaction.getAppId(),
                interaction.getConversationId(),
                submittedText,
                answer);
    }

    public record Submission(boolean accepted, boolean duplicate, Map<String, Object> interaction, Object runResult) {}

    private static Map<String, Object> view(HumanInteractionEntity value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interactionId", value.getId());
        result.put("runId", value.getRunId());
        result.put("nodeId", value.getNodeId());
        result.put("conversationId", value.getConversationId());
        result.put("status", value.getStatus().toLowerCase());
        result.put("title", value.getTitle());
        result.put("prompt", value.getDescription());
        result.put("formMode", value.getFormMode());
        result.put("presentationMode", value.getPresentationMode());
        result.put(
                "schema",
                value.getInputSchemaJson() == null ? Map.of() : JsonUtils.parseMap(value.getInputSchemaJson()));
        result.put(
                "delivery",
                value.getDeliveryConfigJson() == null ? Map.of() : JsonUtils.parseMap(value.getDeliveryConfigJson()));
        result.put(
                "submittedValues",
                value.getSubmittedValuesJson() == null ? Map.of() : JsonUtils.parseMap(value.getSubmittedValuesJson()));
        result.put("submittedText", value.getSubmittedText());
        result.put("expiresAt", value.getExpiresAt());
        result.put("shortCode", value.getShortCode());
        result.put("submittedAt", value.getSubmittedAt());
        result.put("createdAt", value.getCreatedAt());
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }
}
