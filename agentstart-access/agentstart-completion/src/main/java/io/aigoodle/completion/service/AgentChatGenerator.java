package io.aigoodle.completion.service;

import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AgentExecutionService;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a non-flow app (mode = {@code agent} / {@code chat} / {@code completion})
 * through the agent runtime and shapes the output back into the same
 * OpenAI-compatible envelope the workflow generator emits.
 */
public class AgentChatGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AgentChatGenerator.class);

    private final AgentExecutionService executions;

    public AgentChatGenerator(AgentExecutionService executions) {
        this.executions = executions;
    }

    public OpenAIChatResponse generateBlocking(AppEntity application, OpenAIChatRequest request) {
        AgentResponse response = executions.runPublished(
                application.getTenantId(), application.getId(), null, toAgentRequest(request), null, null);
        return OpenAIChatResponse.completion(request.getModel(), response.getText());
    }

    public void generateStream(AppEntity application, OpenAIChatRequest request, SseBridge.Emit emitter) {
        String taskId = "task-" + UUID.randomUUID();
        String chunkId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long startedAtMillis = System.currentTimeMillis();

        emitter.event(
                "chat_started",
                Map.of(
                        "task_id",
                        taskId,
                        "app_id",
                        application.getId(),
                        "conversation_id",
                        ensureConversationId(request),
                        "created_at",
                        startedAtMillis / 1000));

        AgentRequest agentRequest = toAgentRequest(request);
        emitter.event("message", OpenAIChatResponse.chunk(chunkId, request.getModel(), "assistant", null, null));
        AtomicBoolean contentWasStreamed = new AtomicBoolean(false);
        AgentResponse response;
        try {
            response = executions.runPublished(
                    application.getTenantId(),
                    application.getId(),
                    null,
                    agentRequest,
                    step -> emitter.event("step", AppStreamEventPayloads.step(taskId, step)),
                    delta -> {
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }
                        contentWasStreamed.set(true);
                        emitter.event(
                                "message", OpenAIChatResponse.chunk(chunkId, request.getModel(), null, delta, null));
                    });
        } catch (RuntimeException generationFailure) {
            logger.warn(
                    "Agent chat run failed for app {} ({}): {}",
                    application.getId(),
                    application.getName(),
                    generationFailure.getMessage());
            emitter.event("error", AppStreamEventPayloads.error(taskId, application, generationFailure));
            emitter.event("message_end", Map.of("task_id", taskId, "status", "failed"));
            return;
        }

        if (!contentWasStreamed.get()
                && response.getText() != null
                && !response.getText().isEmpty()) {
            emitter.event(
                    "message", OpenAIChatResponse.chunk(chunkId, request.getModel(), null, response.getText(), null));
        }

        emitter.event(
                "message",
                OpenAIChatResponse.chunk(
                        chunkId, request.getModel(), null, null, AppStreamEventPayloads.finishReason(response)));
        if (response.getStatus() == AgentResponse.Status.AWAITING_APPROVAL) {
            emitter.event("agent_approval_required", AppStreamEventPayloads.approvalRequired(taskId, response));
        }
        emitter.event("chat_finished", AppStreamEventPayloads.finished(taskId, application, response, startedAtMillis));
        emitter.event(
                "message_end",
                Map.of(
                        "task_id",
                        taskId,
                        "status",
                        response.getStatus() == null
                                ? "unknown"
                                : response.getStatus().name().toLowerCase(Locale.ROOT)));
    }

    private static AgentRequest toAgentRequest(OpenAIChatRequest request) {
        Map<String, Object> variables = new HashMap<>();
        if (request.getData() != null) {
            variables.putAll(request.getData());
        }
        return AgentRequest.builder()
                .query(request.lastUserMessage())
                .conversationId(request.getConversationId())
                .timeoutMillis(request.getTimeoutMillis())
                .variables(variables)
                .build();
    }

    private static String ensureConversationId(OpenAIChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            request.setConversationId(conversationId);
        }
        return conversationId;
    }
}
