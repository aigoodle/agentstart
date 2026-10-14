package io.aigoodle.completion.controller;

import io.aigoodle.api.workflow.WorkflowRunRequest;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Reactive (WebFlux/Netty) counterpart of the servlet
 * {@code io.aigoodle.web.controller.WorkflowStreamController}. The MVC
 * version is skipped on reactive hosts via {@code @ConditionalOnClass(SseEmitter)},
 * so this module owns the streaming workflow-run surface there — matching the
 * way {@link ChatController} owns chat streaming.
 * <p>
 * Event protocol (Dify-style, identical to the MVC controller and the chat
 * stream's {@code WorkflowStreamSession}): {@code workflow_started} → one
 * {@code node_finished} per executed node → {@code workflow_finished}. Each
 * node result is flushed to the stream as soon as the node completes, giving
 * the console debug panel live per-node observability.
 */
@RestController
@RequestMapping("${spring-agent.web.base-path:/agent-start}")
// MVC hosts already expose these routes through the web module's SseEmitter controller.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnBean(WorkflowService.class)
public class WorkflowStreamController {

    private final WorkflowService workflowService;

    public WorkflowStreamController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping(value = "/workflows/run-graph/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> runGraphStream(@RequestBody WorkflowRunRequest request) {
        return SseBridge.stream(emitter -> {
            String runId = newRunId();
            emitter.event("workflow_started", startedPayload(runId, request.getConversationId()));
            WorkflowRunResult result = request.getGraph() != null
                    ? workflowService.runGraphForTenant(
                            request.getGraph(),
                            inputsOf(request),
                            request.getConversationId(),
                            io.aigoodle.common.context.UserContextHolder.currentTenantId(),
                            request.getAppId(),
                            step -> emitter.event("node_finished", nodePayload(runId, step)))
                    : workflowService.run(
                            request.getWorkflowId(),
                            inputsOf(request),
                            request.getConversationId(),
                            step -> emitter.event("node_finished", nodePayload(runId, step)));
            emitter.event("workflow_finished", result);
        });
    }

    @PostMapping(value = "/workflows/{id}/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> runStream(@PathVariable String id, @RequestBody WorkflowRunRequest request) {
        return SseBridge.stream(emitter -> {
            String runId = newRunId();
            emitter.event("workflow_started", startedPayload(runId, request.getConversationId()));
            WorkflowRunResult result = workflowService.run(
                    id,
                    inputsOf(request),
                    request.getConversationId(),
                    step -> emitter.event("node_finished", nodePayload(runId, step)));
            emitter.event("workflow_finished", result);
        });
    }

    private static String newRunId() {
        return "debug-" + UUID.randomUUID();
    }

    private static Map<String, Object> startedPayload(String runId, String conversationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("conversation_id", conversationId == null ? "" : conversationId);
        payload.put("created_at", System.currentTimeMillis() / 1000);
        return payload;
    }

    /**
     * Snake_case node payload matching the Dify {@code node_finished} shape used
     * by {@code WorkflowStreamSession} so chat and debug streams share one
     * client-side parser.
     */
    private static Map<String, Object> nodePayload(String runId, StepRecord step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("node_id", step.getNodeId());
        payload.put(
                "node_type",
                step.getNodeType() == null ? null : step.getNodeType().name());
        payload.put("title", step.getTitle());
        payload.put("outputs", step.getOutputs());
        payload.put("handle", step.getHandle());
        payload.put("elapsed_ms", step.getElapsedMillis());
        payload.put("failed", step.isFailed());
        payload.put("error", step.getError());
        return payload;
    }

    private static Map<String, Object> inputsOf(WorkflowRunRequest request) {
        if (request.getData() != null) return request.getData();
        return request.getInputs() == null ? new HashMap<>() : request.getInputs();
    }
}
