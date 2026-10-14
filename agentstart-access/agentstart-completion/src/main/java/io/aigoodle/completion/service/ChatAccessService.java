package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;

/** Reusable resource-ownership checks for host-provided access policies. */
public class ChatAccessService {

    private final AppService appService;
    private final ObjectProvider<WorkflowService> workflowServices;
    private final String rootTenantId;

    public ChatAccessService(AppService appService, ObjectProvider<WorkflowService> workflowServices) {
        this(appService, workflowServices, "root");
    }

    public ChatAccessService(
            AppService appService, ObjectProvider<WorkflowService> workflowServices, String rootTenantId) {
        this.appService = appService;
        this.workflowServices = workflowServices;
        this.rootTenantId = rootTenantId == null || rootTenantId.isBlank() ? "root" : rootTenantId;
    }

    public AppEntity requireVisibleAppByCode(String appCode, String executionTenantId) {
        return appService.requireVisibleByCode(appCode, executionTenantId, rootTenantId);
    }

    public String requireOwnedApp(String appId, String tenantId) {
        return appService.require(tenantId, appId).getId();
    }

    public String requireOwnedWorkflow(String appId, String tenantId, String workflowId) {
        String resolvedAppId = requireOwnedApp(appId, tenantId);
        if (workflowId == null || workflowId.isBlank()) {
            return resolvedAppId;
        }
        WorkflowService workflowService = workflowServices.getIfAvailable();
        if (workflowService == null) {
            throw new PlatformException("workflow_unavailable", "工作流模块未启用", null);
        }
        WorkflowEntity workflow = workflowService.require(workflowId);
        if (!Objects.equals(resolvedAppId, workflow.getAppId()) || !Objects.equals(workflow.getTenantId(), tenantId)) {
            throw new PlatformException("workflow_not_found", "工作流不存在或不属于当前应用", null);
        }
        return resolvedAppId;
    }

    /** Enforces publication switches after an API key has resolved its app. */
    public String requireExternalApp(String appId) {
        return requireExternalAppEntity(appId).getId();
    }

    /** Resolves both identity and owning tenant for a trusted external token. */
    public AppEntity requireExternalAppEntity(String appId) {
        AppEntity app = appService.require(appId);
        if (!Boolean.TRUE.equals(app.getEnableApi())
                || !Boolean.TRUE.equals(app.getPublished())
                || "disabled".equalsIgnoreCase(app.getStatus())) {
            throw new PlatformException("app_api_unavailable", "应用 API 未启用或尚未发布", null);
        }
        return app;
    }
}
