package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.agent.service.SaveAppRequest;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.web.dto.WorkflowAppOption;
import io.aigoodle.web.service.AppLifecycleCoordinator;
import io.aigoodle.web.support.AppToolViewMapper;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Application metadata, configuration and tool-discovery endpoints. */
@RestController
@ConditionalOnBean(AppService.class)
@RequestMapping({"/apps", "/agents"})
public class AppController {

    private final AppService appService;
    private final AppLifecycleCoordinator applicationCoordinator;
    private final AppToolViewMapper toolViewMapper;
    private final ObjectProvider<WorkflowService> workflowServiceProvider;

    public AppController(
            AppService appService,
            ObjectProvider<ToolRegistry> toolRegistryProvider,
            ObjectProvider<WorkflowService> workflowServiceProvider) {
        this.appService = appService;
        this.workflowServiceProvider = workflowServiceProvider;
        this.applicationCoordinator = new AppLifecycleCoordinator(appService, workflowServiceProvider);
        this.toolViewMapper = new AppToolViewMapper(appService, toolRegistryProvider);
    }

    @GetMapping
    public ApiResponse<List<AppEntity>> list() {
        return ApiResponse.ok(applicationCoordinator.list(currentTenantId()));
    }

    /** Tenant-scoped options used by workflow target selectors. */
    @GetMapping("/selectors/workflows")
    public ApiResponse<List<WorkflowAppOption>> workflowOptions() {
        List<WorkflowAppOption> options = appService.listPublishedWorkflowApps(currentTenantId()).stream()
                .map(this::workflowOption)
                .toList();
        return ApiResponse.ok(options);
    }

    @SuppressWarnings("unchecked")
    private WorkflowAppOption workflowOption(AppEntity app) {
        WorkflowService workflowService = workflowServiceProvider.getIfAvailable();
        if (workflowService == null || app.getWorkflowId() == null) return WorkflowAppOption.from(app);
        try {
            Map<String, Object> contract = JsonUtils.parseMap(
                    workflowService.require(app.getWorkflowId()).getOutput());
            Object inputs = contract.get("inputs");
            return WorkflowAppOption.from(
                    app, inputs instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of());
        } catch (RuntimeException ignored) {
            return WorkflowAppOption.from(app);
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<AppEntity> get(@PathVariable String id) {
        return ApiResponse.ok(applicationCoordinator.get(currentTenantId(), id));
    }

    @GetMapping("/{id}/model-config")
    public ApiResponse<AppModelConfigEntity> modelConfig(@PathVariable String id) {
        return ApiResponse.ok(appService.getModelConfig(currentTenantId(), id));
    }

    @PostMapping
    public ApiResponse<AppEntity> create(@RequestBody SaveAppRequest request) {
        request.setTenantId(currentTenantId());
        return ApiResponse.ok(applicationCoordinator.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppEntity> update(@PathVariable String id, @RequestBody SaveAppRequest request) {
        request.setTenantId(currentTenantId());
        return ApiResponse.ok(applicationCoordinator.update(currentTenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        appService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/tools")
    public ApiResponse<List<Map<String, Object>>> tools(@PathVariable String id) {
        return ApiResponse.ok(toolViewMapper.toolsOf(currentTenantId(), id));
    }
}
