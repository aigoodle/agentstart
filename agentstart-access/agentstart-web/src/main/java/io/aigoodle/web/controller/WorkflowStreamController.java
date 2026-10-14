package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.workflow.WorkflowRunRequest;
import io.aigoodle.web.common.SseEmitterBridge;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE streaming run endpoints for workflows (servlet/MVC hosts).
 * <p>
 * The event protocol mirrors the Dify workflow stream so the console debug panel
 * can observe a run node-by-node: {@code workflow_started} → one
 * {@code node_finished} per executed node → {@code workflow_finished}. On
 * reactive hosts (WebFlux/Netty, e.g. {@code agentstart-server}) this
 * controller is skipped via {@code @ConditionalOnClass(SseEmitter)} and the
 * equivalent endpoints are served by the {@code agentstart-completion} module.
 * <p>
 * MVC-only by design: the handler signatures reference {@link SseEmitter}, a
 * spring-webmvc type. Keeping these methods out of {@link WorkflowController}
 * matters — WebFlux's handler mapping introspects every registered controller's
 * method signatures at startup and would fail on the missing servlet class.
 */
@RestController
@ConditionalOnClass(SseEmitter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(WorkflowService.class)
public class WorkflowStreamController {

    private final WorkflowService workflowService;

    public WorkflowStreamController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping(value = "/workflows/run-graph/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runGraphStream(@RequestBody WorkflowRunRequest request) {
        return SseEmitterBridge.stream(emitter -> {
            String runId = newRunId();
            emitter.event("workflow_started", startedPayload(runId, request.getConversationId()));
            WorkflowRunResult result = request.getGraph() != null
                    ? workflowService.runGraphForTenant(
                            request.getGraph(),
                            inputsOf(request),
                            request.getConversationId(),
                            currentTenantId(),
                            step -> emitter.event("node_finished", nodePayload(runId, step)))
                    : workflowService.runForTenant(
                            request.getWorkflowId(),
                            inputsOf(request),
                            request.getConversationId(),
                            currentTenantId(),
                            step -> emitter.event("node_finished", nodePayload(runId, step)));
            emitter.event("workflow_finished", result);
        });
    }

    @PostMapping(value = "/workflows/{id}/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@PathVariable String id, @RequestBody WorkflowRunRequest request) {
        return SseEmitterBridge.stream(emitter -> {
            String runId = newRunId();
            emitter.event("workflow_started", startedPayload(runId, request.getConversationId()));
            WorkflowRunResult result = workflowService.runForTenant(
                    id,
                    inputsOf(request),
                    request.getConversationId(),
                    currentTenantId(),
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
     * by the chat stream ({@code WorkflowStreamSession}) so both surfaces stay
     * observable with one client parser.
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
