package io.aigoodle.agent.service;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import java.util.List;
import java.util.function.Consumer;

/** One tenant-safe execution facade shared by Web, channel and completion adapters. */
public final class AgentExecutionService {
    private final AgentService drafts;
    private final AgentVersionService versions;
    private final AgentRuntimeRegistry runtimes;

    public AgentExecutionService(AgentService drafts, AgentVersionService versions, AgentRuntimeRegistry runtimes) {
        this.drafts = drafts;
        this.versions = versions;
        this.runtimes = runtimes;
    }

    /** Production path: always executes an immutable current or explicitly pinned version. */
    public AgentResponse runPublished(
            String tenantId,
            String appId,
            String versionId,
            AgentRequest request,
            Consumer<AgentStep> steps,
            Consumer<String> tokens) {
        drafts.require(tenantId, appId);
        AgentDefinition definition = versions.definition(tenantId, appId, versionId);
        return runtimes.run(definition, request, steps, tokens);
    }

    /** Administrator preview path: executes the mutable tenant-owned draft intentionally. */
    public AgentResponse previewDraft(
            String tenantId, String appId, AgentRequest request, Consumer<AgentStep> steps, Consumer<String> tokens) {
        AppEntity draft = drafts.require(tenantId, appId);
        return runtimes.run(drafts.toDefinition(draft), request, steps, tokens);
    }

    public List<AgentMessage> history(String tenantId, String appId, String conversationId, int max) {
        return drafts.history(tenantId, appId, conversationId, max);
    }
}
