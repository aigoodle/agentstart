package io.aigoodle.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.service.WorkflowDraftChanges;
import io.aigoodle.workflow.service.WorkflowDraftDefinition;
import io.aigoodle.workflow.service.WorkflowPublication;
import io.aigoodle.workflow.service.WorkflowService;
import org.springframework.beans.factory.ObjectProvider;

/** Coordinates an application's mutable draft with its published workflow pointer. */
public final class WorkflowDraftCoordinator {

    private final WorkflowService workflowService;
    private final ObjectProvider<AppService> appServiceProvider;

    public WorkflowDraftCoordinator(WorkflowService workflowService, ObjectProvider<AppService> appServiceProvider) {
        this.workflowService = workflowService;
        this.appServiceProvider = appServiceProvider;
    }

    /** Returns the existing draft or creates it for a legacy workflow-mode application. */
    public WorkflowEntity findOrCreate(String tenantId, String appId) {
        WorkflowEntity draft = workflowService.findDraft(tenantId, appId);
        if (draft != null) {
            return draft;
        }

        AppService appService = appServiceProvider.getIfAvailable();
        AppEntity application = findFlowApplication(appService, tenantId, appId);
        if (application == null) {
            return null;
        }

        WorkflowEntity createdDraft = workflowService.createDraft(new WorkflowDraftDefinition(
                application.getId(), application.getTenantId(), application.getName(), application.getMode(), null));
        bindWorkflowQuietly(appService, tenantId, application.getId(), createdDraft.getId());
        return createdDraft;
    }

    public WorkflowEntity save(String tenantId, String appId, JsonNode graph) {
        findOrCreate(tenantId, appId);
        return workflowService.saveDraft(tenantId, appId, WorkflowDraftChanges.graphOnly(graph));
    }

    public WorkflowEntity publish(String tenantId, String appId, String markedName, String markedComment) {
        findOrCreate(tenantId, appId);
        WorkflowEntity snapshot =
                workflowService.publishDraft(tenantId, appId, new WorkflowPublication(markedName, markedComment));
        AppService appService = appServiceProvider.getIfAvailable();
        if (appService != null) {
            appService.bindPublishedWorkflow(tenantId, appId, snapshot.getId());
        }
        return snapshot;
    }

    private static AppEntity findFlowApplication(AppService appService, String tenantId, String appId) {
        if (appService == null) {
            return null;
        }
        try {
            AppEntity application = appService.require(tenantId, appId);
            return isFlowMode(application.getMode()) ? application : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void bindWorkflowQuietly(AppService appService, String tenantId, String appId, String workflowId) {
        if (appService == null) {
            return;
        }
        try {
            appService.bindWorkflowId(tenantId, appId, workflowId);
        } catch (Exception ignored) {
            // The workflow is durable even if its application disappeared during the operation.
        }
    }

    private static boolean isFlowMode(String mode) {
        return "workflow".equals(mode) || "chatflow".equals(mode);
    }
}
