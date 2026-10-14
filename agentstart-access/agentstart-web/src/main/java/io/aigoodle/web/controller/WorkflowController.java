package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.api.workflow.WorkflowRunRequest;
import io.aigoodle.trigger.service.TriggerService;
import io.aigoodle.web.dto.WorkflowSaveRequest;
import io.aigoodle.web.service.WorkflowDraftCoordinator;
import io.aigoodle.web.service.WorkflowExampleService;
import io.aigoodle.web.support.WorkflowNodeCatalog;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.entity.WorkflowRunEntity;
import io.aigoodle.workflow.service.WorkflowDraftDefinition;
import io.aigoodle.workflow.service.WorkflowService;
import io.aigoodle.workflow.service.WorkflowSignalResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP facade for workflow definitions, drafts, execution and palette metadata.
 * The SSE streaming run endpoints live in {@link WorkflowStreamController} —
 * they are servlet-only and skipped on reactive hosts.
 */
@RestController
@ConditionalOnBean(WorkflowService.class)
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExampleService workflowExampleService;
    private final WorkflowDraftCoordinator draftCoordinator;
    private final ObjectProvider<AppService> appServiceProvider;
    private final ObjectProvider<TriggerService> triggerServiceProvider;

    public WorkflowController(
            WorkflowService workflowService,
            WorkflowExampleService workflowExampleService,
            ObjectProvider<AppService> appServiceProvider,
            ObjectProvider<TriggerService> triggerServiceProvider) {
        this.workflowService = workflowService;
        this.workflowExampleService = workflowExampleService;
        this.appServiceProvider = appServiceProvider;
        this.draftCoordinator = new WorkflowDraftCoordinator(workflowService, appServiceProvider);
        this.triggerServiceProvider = triggerServiceProvider;
    }

    @GetMapping("/workflows")
    public ApiResponse<List<WorkflowEntity>> list() {
        return ApiResponse.ok(workflowService.list(currentTenantId()));
    }

    @PostMapping("/workflows")
    public ApiResponse<WorkflowEntity> save(@RequestBody WorkflowSaveRequest request) {
        if (request.getAppId() == null || request.getAppId().isBlank()) {
            return ApiResponse.error(
                    "app_id_required", "Field 'appId' is required. Every workflow save is scoped to an app.");
        }
        requireOwnedApp(request.getAppId());
        return ApiResponse.ok(workflowService.save(new WorkflowDraftDefinition(
                request.getAppId(), currentTenantId(), request.getName(), request.getMode(), request.getGraph())));
    }

    @GetMapping("/workflows/{id}")
    public ApiResponse<WorkflowEntity> get(@PathVariable String id) {
        return ApiResponse.ok(workflowService.require(currentTenantId(), id));
    }

    @PutMapping("/workflows/{id}")
    public ApiResponse<WorkflowEntity> update(@PathVariable String id, @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.update(
                currentTenantId(), id, request.getName(), request.getMode(), request.getGraph()));
    }

    @DeleteMapping("/workflows/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        workflowService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/apps/{appId}/workflow/draft")
    public ApiResponse<WorkflowEntity> getDraft(@PathVariable String appId) {
        requireOwnedApp(appId);
        WorkflowEntity draft = draftCoordinator.findOrCreate(currentTenantId(), appId);
        return draft == null
                ? ApiResponse.error("draft_not_found", "No draft workflow for app " + appId)
                : ApiResponse.ok(draft);
    }

    @PutMapping("/apps/{appId}/workflow/draft")
    public ApiResponse<WorkflowEntity> saveDraft(@PathVariable String appId, @RequestBody WorkflowSaveRequest request) {
        requireOwnedApp(appId);
        return ApiResponse.ok(draftCoordinator.save(currentTenantId(), appId, request.getGraph()));
    }

    @PostMapping("/apps/{appId}/workflow/publish")
    @Transactional
    public ApiResponse<WorkflowEntity> publish(
            @PathVariable String appId, @RequestBody(required = false) PublishRequest request) {
        requireOwnedApp(appId);
        String markedName = request == null ? null : request.getMarkedName();
        String markedComment = request == null ? null : request.getMarkedComment();
        WorkflowEntity published = draftCoordinator.publish(currentTenantId(), appId, markedName, markedComment);
        TriggerService triggerService = triggerServiceProvider.getIfAvailable();
        if (triggerService != null) {
            triggerService.syncPublishedWorkflowSchedule(published, workflowService);
        }
        return ApiResponse.ok(published);
    }

    @GetMapping("/apps/{appId}/workflows")
    public ApiResponse<List<WorkflowEntity>> listByApp(@PathVariable String appId) {
        requireOwnedApp(appId);
        return ApiResponse.ok(workflowService.listByApp(currentTenantId(), appId));
    }

    @PostMapping("/apps/{appId}/workflow/restore/{snapshotId}")
    public ApiResponse<WorkflowEntity> restore(@PathVariable String appId, @PathVariable String snapshotId) {
        requireOwnedApp(appId);
        return ApiResponse.ok(workflowService.restorePublishedSnapshot(currentTenantId(), appId, snapshotId));
    }

    @GetMapping("/workflows/{id}/runs")
    public ApiResponse<List<WorkflowRunEntity>> runs(
            @PathVariable String id, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(workflowService.runs(currentTenantId(), id, limit));
    }

    @PostMapping("/workflow-runs/{runId}/cancel")
    public ApiResponse<Boolean> cancelRun(
            @PathVariable String runId, @RequestBody(required = false) Map<String, String> request) {
        String reason = request == null ? null : request.get("reason");
        return ApiResponse.ok(workflowService.cancel(currentTenantId(), runId, reason));
    }

    @PostMapping("/workflow-runs/{runId}/pause")
    public ApiResponse<Boolean> pauseRun(
            @PathVariable String runId, @RequestBody(required = false) Map<String, String> request) {
        String reason = request == null ? null : request.get("reason");
        return ApiResponse.ok(workflowService.pause(currentTenantId(), runId, reason));
    }

    @PostMapping("/workflow-runs/{runId}/resume")
    public ApiResponse<WorkflowRunResult> resumeRun(@PathVariable String runId) {
        return ApiResponse.ok(workflowService.resume(currentTenantId(), runId));
    }

    @PostMapping("/workflow-runs/{runId}/signal")
    public ApiResponse<WorkflowSignalResult> signalRun(
            @PathVariable String runId, @RequestBody Map<String, Object> request) {
        String token = request.get("resumeToken") == null ? null : String.valueOf(request.get("resumeToken"));
        String eventId = request.get("eventId") == null ? null : String.valueOf(request.get("eventId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                request.get("payload") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return ApiResponse.ok(workflowService.signal(currentTenantId(), runId, token, eventId, payload));
    }

    @PostMapping("/workflow-events/{correlationKey}")
    public ApiResponse<WorkflowSignalResult> signalByCorrelation(
            @PathVariable String correlationKey, @RequestBody Map<String, Object> request) {
        String token = request.get("resumeToken") == null ? null : String.valueOf(request.get("resumeToken"));
        String eventId = request.get("eventId") == null ? null : String.valueOf(request.get("eventId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                request.get("payload") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return ApiResponse.ok(
                workflowService.signalByCorrelation(currentTenantId(), correlationKey, token, eventId, payload));
    }

    @PostMapping("/workflows/{id}/run")
    public ApiResponse<WorkflowRunResult> run(@PathVariable String id, @RequestBody WorkflowRunRequest request) {
        return ApiResponse.ok(
                workflowService.runForTenant(id, inputsOf(request), request.getConversationId(), currentTenantId()));
    }

    @PostMapping("/workflows/run-graph")
    public ApiResponse<WorkflowRunResult> runGraph(@RequestBody WorkflowRunRequest request) {
        if (request.getGraph() == null) {
            return ApiResponse.error("graph_required", "Field 'graph' is required for ad-hoc runs");
        }
        return ApiResponse.ok(workflowService.runGraphForTenant(
                request.getGraph(),
                inputsOf(request),
                request.getConversationId(),
                currentTenantId(),
                request.getAppId(),
                null));
    }

    @GetMapping("/node-types")
    public ApiResponse<List<Map<String, Object>>> nodeTypes() {
        return ApiResponse.ok(WorkflowNodeCatalog.entries());
    }

    @GetMapping("/workflow-examples")
    public ApiResponse<List<WorkflowExampleService.WorkflowExample>> workflowExamples() {
        return ApiResponse.ok(workflowExampleService.examples());
    }

    private static Map<String, Object> inputsOf(WorkflowRunRequest request) {
        if (request.getData() != null) return request.getData();
        return request.getInputs() == null ? new HashMap<>() : request.getInputs();
    }

    private void requireOwnedApp(String appId) {
        AppService apps = appServiceProvider.getIfAvailable();
        if (apps != null) apps.require(currentTenantId(), appId);
    }

    @Data
    public static class PublishRequest {
        private String markedName;
        private String markedComment;
    }
}
