package io.aigoodle.agent.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import java.util.List;

/**
 * Application-catalog boundary. Agent execution remains in {@link AgentService};
 * callers that only manage or resolve applications should depend on this type.
 */
public class AppService {

    private final AgentService agentRuntime;

    public AppService(AgentService agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    public AppEntity create(SaveAppRequest request) {
        return agentRuntime.create(request);
    }

    public AppEntity update(String appId, SaveAppRequest request) {
        return agentRuntime.update(appId, request);
    }

    public AppEntity update(String tenantId, String appId, SaveAppRequest request) {
        return agentRuntime.update(tenantId, appId, request);
    }

    public void delete(String appId) {
        agentRuntime.delete(appId);
    }

    public void delete(String tenantId, String appId) {
        agentRuntime.delete(tenantId, appId);
    }

    public AppEntity requireWritable(String tenantId, String appId) {
        AppEntity app = require(tenantId, appId);
        agentRuntime.requireWritable(app);
        return app;
    }

    public AppEntity require(String appId) {
        return agentRuntime.require(appId);
    }

    public AppEntity require(String tenantId, String appId) {
        return agentRuntime.require(tenantId, appId);
    }

    public AppEntity requireForChat(String tenantId, String appId) {
        return agentRuntime.requireForChat(tenantId, appId);
    }

    public List<AppEntity> list(String tenantId) {
        return agentRuntime.list(tenantId);
    }

    public List<AppEntity> listPublishedWorkflowApps(String tenantId) {
        return agentRuntime.listPublishedWorkflowApps(tenantId);
    }

    public AppEntity enrich(AppEntity application) {
        return agentRuntime.enrich(application);
    }

    public AppEntity requireVisibleByCode(String appCode, String tenantId, String rootTenantId) {
        return agentRuntime.requireVisibleByCode(appCode, tenantId, rootTenantId);
    }

    public AppModelConfigEntity getModelConfig(String appId) {
        return agentRuntime.getModelConfig(appId);
    }

    public AppModelConfigEntity getModelConfig(String tenantId, String appId) {
        return agentRuntime.getModelConfig(tenantId, appId);
    }

    public AppEntity bindWorkflowId(String appId, String workflowId) {
        return agentRuntime.bindWorkflowId(appId, workflowId);
    }

    public AppEntity bindWorkflowId(String tenantId, String appId, String workflowId) {
        return agentRuntime.bindWorkflowId(tenantId, appId, workflowId);
    }

    /** Binds the immutable workflow snapshot and exposes the app after publication. */
    public AppEntity bindPublishedWorkflow(String appId, String workflowId) {
        return agentRuntime.bindPublishedWorkflow(appId, workflowId);
    }

    public AppEntity bindPublishedWorkflow(String tenantId, String appId, String workflowId) {
        return agentRuntime.bindPublishedWorkflow(tenantId, appId, workflowId);
    }
}
