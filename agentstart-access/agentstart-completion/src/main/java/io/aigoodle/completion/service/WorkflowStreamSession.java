package io.aigoodle.completion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Owns identifiers and SSE event formatting for one streaming workflow run. */
final class WorkflowStreamSession {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppEntity application;
    private final OpenAIChatRequest request;
    private final WorkflowChatContext context;
    private final SseBridge.Emit emitter;
    private final String taskId = "task-" + UUID.randomUUID();
    private final String chunkId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    private final long startedAtMillis = System.currentTimeMillis();
    private final ChatStreamSink streamSink;

    WorkflowStreamSession(
            AppEntity application, OpenAIChatRequest request, WorkflowChatContext context, SseBridge.Emit emitter) {
        this.application = application;
        this.request = request;
        this.context = context;
        this.emitter = emitter;
        this.streamSink = new ChatStreamSink(this::emitToken);
    }

    void start() {
        emitter.event(
                "workflow_started",
                Map.of(
                        "task_id", taskId,
                        "workflow_id", context.workflowId(),
                        "app_id", application.getId(),
                        "conversation_id", context.conversationId(),
                        "created_at", startedAtMillis / 1000));
        emitter.event("message", OpenAIChatResponse.chunk(chunkId, request.getModel(), "assistant", null, null));
    }

    ChatStreamSink sink() {
        return streamSink;
    }

    void nodeFinished(StepRecord stepRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("node_id", stepRecord.getNodeId());
        payload.put(
                "node_type",
                stepRecord.getNodeType() == null
                        ? null
                        : stepRecord.getNodeType().name());
        payload.put("title", stepRecord.getTitle());
        payload.put("outputs", stepRecord.getOutputs());
        payload.put("handle", stepRecord.getHandle());
        payload.put("elapsed_ms", stepRecord.getElapsedMillis());
        payload.put("failed", stepRecord.isFailed());
        payload.put("error", stepRecord.getError());
        emitter.event("node_finished", payload);
    }

    /** Emits terminal events and returns the exact answer that should be persisted. */
    String complete(WorkflowRunResult runResult) {
        if (runResult.getStatus() == WorkflowRunStatus.WAITING && runResult.getWaitRequest() != null) {
            Map<String, Object> reason = humanInputReason(runResult);
            emitHumanInputRequired(runResult, reason);
            emitter.event(
                    "workflow_paused",
                    Map.of(
                            "task_id",
                            taskId,
                            "run_id",
                            runResult.getRunId(),
                            "waiting_node_id",
                            runResult.getWaitingNodeId(),
                            "status",
                            "waiting"));
            emitter.event("message_end", Map.of("task_id", taskId, "status", "waiting"));
            streamSink.close();
            return humanInputHistoryContent(runResult, reason);
        }
        String workflowAnswer = WorkflowAnswerExtractor.extract(runResult);
        String streamedAnswer = streamSink.accumulated();
        if (streamedAnswer.isEmpty() && !workflowAnswer.isEmpty()) {
            emitToken(workflowAnswer);
        }
        emitter.event(
                "message",
                OpenAIChatResponse.chunk(
                        chunkId, request.getModel(), null, null, runResult.isSuccess() ? "stop" : "error"));
        emitter.event("workflow_finished", finishedPayload(runResult));
        emitter.event(
                "message_end", Map.of("task_id", taskId, "status", runResult.isSuccess() ? "succeeded" : "failed"));
        streamSink.close();
        return streamedAnswer.isEmpty() ? workflowAnswer : streamedAnswer;
    }

    private Map<String, Object> humanInputReason(WorkflowRunResult runResult) {
        WorkflowWaitRequest wait = runResult.getWaitRequest();
        String title = runResult.getSteps() == null
                ? null
                : runResult.getSteps().stream()
                        .filter(step -> runResult.getWaitingNodeId().equals(step.getNodeId()))
                        .map(StepRecord::getTitle)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null);
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("type", wait.type().name());
        Object schemaTitle = wait.inputSchema().get("title");
        Object schemaDescription = wait.inputSchema().get("description");
        Object submitText = wait.inputSchema().get("x-submit-button-text");
        reason.put(
                "node_title",
                schemaTitle == null || String.valueOf(schemaTitle).isBlank()
                        ? (title == null ? "人工输入" : title)
                        : schemaTitle);
        if (schemaDescription != null && !String.valueOf(schemaDescription).isBlank()) {
            reason.put("prompt", schemaDescription);
        }
        if (submitText != null && !String.valueOf(submitText).isBlank()) {
            reason.put("submit_button_text", submitText);
        }
        reason.put("form_token", wait.resumeToken());
        reason.put("interaction_id", wait.inputSchema().get("x-interaction-id"));
        reason.put("access_token", wait.inputSchema().get("x-access-token"));
        reason.put("presentation_mode", wait.inputSchema().get("x-presentation-mode"));
        reason.put("run_id", runResult.getRunId());
        reason.put("input_schema", wait.inputSchema());
        if (wait.expiresAt() != null) {
            reason.put("expiration_time", wait.expiresAt().getEpochSecond());
        }
        return reason;
    }

    private void emitHumanInputRequired(WorkflowRunResult runResult, Map<String, Object> reason) {
        emitter.event(
                "human_input_required",
                Map.of(
                        "run_id", runResult.getRunId(),
                        "conversation_id", context.conversationId(),
                        "reasons", java.util.List.of(reason)));
    }

    /**
     * Persist the paused assistant turn as renderable content. This is intentionally
     * the same component contract consumed by the React chat client, so pending forms
     * survive refreshes, other browsers, and local-storage loss.
     */
    private String humanInputHistoryContent(WorkflowRunResult runResult, Map<String, Object> reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", reason.get("form_token"));
        payload.put("interactionId", reason.get("interaction_id"));
        payload.put("accessToken", reason.get("access_token"));
        payload.put("runId", runResult.getRunId());
        payload.put("conversationId", context.conversationId());
        payload.put("title", reason.get("node_title"));
        payload.put("prompt", reason.get("prompt"));
        payload.put("submitButtonText", reason.get("submit_button_text"));
        payload.put("expirationTime", reason.get("expiration_time"));
        payload.put("schema", reason.get("input_schema"));
        payload.values().removeIf(java.util.Objects::isNull);
        try {
            String readableJson = escapeSingleQuotedAttribute(JSON.writeValueAsString(payload));
            return streamSink.accumulated().stripTrailing() + "<human-input value='" + readableJson
                    + "'></human-input>";
        } catch (JsonProcessingException serializationFailure) {
            return streamSink.accumulated();
        }
    }

    private static String escapeSingleQuotedAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    void fail(RuntimeException runFailure) {
        streamSink.close();
        emitter.event(
                "error",
                Map.of(
                        "task_id",
                        taskId,
                        "message",
                        runFailure.getMessage() == null ? "workflow run failed" : runFailure.getMessage()));
        emitter.event("message_end", Map.of("task_id", taskId, "status", "failed"));
    }

    private void emitToken(String token) {
        emitter.event("message", OpenAIChatResponse.chunk(chunkId, request.getModel(), null, token, null));
    }

    private Map<String, Object> finishedPayload(WorkflowRunResult runResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("workflow_id", context.workflowId());
        payload.put("conversation_id", context.conversationId());
        payload.put("status", runResult.isSuccess() ? "succeeded" : "failed");
        payload.put("outputs", runResult.getOutputs());
        payload.put("error", runResult.getError());
        payload.put("elapsed_ms", System.currentTimeMillis() - startedAtMillis);
        payload.put(
                "total_steps",
                runResult.getSteps() == null ? 0 : runResult.getSteps().size());
        return payload;
    }
}
