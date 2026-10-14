package io.aigoodle.web.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppMode;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.agent.service.SaveAppRequest;
import io.aigoodle.workflow.service.WorkflowDraftDefinition;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

/** Coordinates application catalog persistence with optional workflow draft creation. */
public final class AppLifecycleCoordinator {

    private final AppService appService;
    private final ObjectProvider<WorkflowService> workflowServiceProvider;

    public AppLifecycleCoordinator(AppService appService, ObjectProvider<WorkflowService> workflowServiceProvider) {
        this.appService = appService;
        this.workflowServiceProvider = workflowServiceProvider;
    }

    public List<AppEntity> list(String tenantId) {
        List<AppEntity> applications = appService.list(tenantId);
        applications.forEach(appService::enrich);
        return applications;
    }

    public AppEntity get(String tenantId, String appId) {
        return appService.enrich(appService.require(tenantId, appId));
    }

    public AppEntity create(SaveAppRequest request) {
        AppEntity application = appService.create(request);
        createWorkflowDraftIfNeeded(application);
        return appService.enrich(application);
    }

    public AppEntity update(String tenantId, String appId, SaveAppRequest request) {
        return appService.enrich(appService.update(tenantId, appId, request));
    }

    private void createWorkflowDraftIfNeeded(AppEntity application) {
        if (!isFlowMode(application.getMode())) {
            return;
        }
        WorkflowService workflowService = workflowServiceProvider.getIfAvailable();
        if (workflowService == null) {
            return;
        }
        workflowService.createDraft(new WorkflowDraftDefinition(
                application.getId(), application.getTenantId(), application.getName(), application.getMode(), null));
        appService.bindWorkflowId(application.getId(), application.getId());
    }

    private static boolean isFlowMode(String mode) {
        return AppMode.from(mode).isFlow();
    }
}
