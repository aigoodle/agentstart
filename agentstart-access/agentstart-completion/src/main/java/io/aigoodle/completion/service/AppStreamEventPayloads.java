package io.aigoodle.completion.service;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.common.exception.PlatformException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the protocol payloads emitted while an agent chat is running. */
final class AppStreamEventPayloads {

    private AppStreamEventPayloads() {}

    static Map<String, Object> step(String taskId, AgentStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("kind", step.getKind() == null ? null : step.getKind().name());
        payload.put("thought", step.getThought());
        payload.put("action", step.getAction());
        payload.put("action_input", step.getActionInput());
        payload.put("observation", step.getObservation());
        payload.put("content", step.getKind() == AgentStep.Kind.FINAL ? null : step.getContent());
        return payload;
    }

    static Map<String, Object> error(String taskId, AppEntity application, Exception exception) {
        String errorCode = exception instanceof PlatformException agentException && agentException.getCode() != null
                ? agentException.getCode()
                : "agent_run_failed";
        String errorMessage =
                exception.getMessage() == null || exception.getMessage().isBlank()
                        ? "agent run failed"
                        : exception.getMessage();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("code", errorCode);
        payload.put("message", errorMessage);
        payload.put("app_id", application.getId());
        payload.put("agent", application.getName());
        return payload;
    }

    static String finishReason(AgentResponse response) {
        return switch (response.getStatus()) {
            case COMPLETED -> "stop";
            case AWAITING_APPROVAL -> "tool_calls";
            case MAX_ITERATIONS -> "length";
            case FAILED -> "error";
        };
    }

    static Map<String, Object> finished(String taskId, AppEntity application, AgentResponse response, long startedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("run_id", response.getRunId());
        payload.put("app_id", application.getId());
        payload.put("conversation_id", response.getConversationId());
        payload.put(
                "status",
                response.getStatus() == null ? null : response.getStatus().name());
        payload.put("iterations", response.getIterations());
        payload.put("elapsed_ms", System.currentTimeMillis() - startedAt);
        payload.put(
                "total_steps",
                response.getSteps() == null ? 0 : response.getSteps().size());
        payload.put("pending_approval", response.getPendingApproval());
        payload.put("error", response.getError());
        return payload;
    }

    static Map<String, Object> approvalRequired(String taskId, AgentResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("run_id", response.getRunId());
        payload.put("conversation_id", response.getConversationId());
        payload.put("approval", response.getPendingApproval());
        return payload;
    }
}
