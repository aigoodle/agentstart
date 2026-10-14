package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppMode;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.workflow.service.WorkflowService;
import org.springframework.beans.factory.ObjectProvider;

/** Routes application chat to either the agent runtime or workflow engine. */
final class AppChatRuntimeRouter {

    private final AgentChatGenerator agentGenerator;
    private final ObjectProvider<WorkflowService> workflowServiceProvider;
    private final ObjectProvider<MemoryManager> memoryManagerProvider;

    private volatile WorkflowChatGenerator workflowGenerator;

    AppChatRuntimeRouter(
            AgentChatGenerator agentGenerator,
            ObjectProvider<WorkflowService> workflowServiceProvider,
            ObjectProvider<MemoryManager> memoryManagerProvider) {
        this.agentGenerator = agentGenerator;
        this.workflowServiceProvider = workflowServiceProvider;
        this.memoryManagerProvider = memoryManagerProvider;
    }

    OpenAIChatResponse generateBlocking(AppEntity application, OpenAIChatRequest request) {
        return switch (AppMode.from(application.getMode())) {
            case AGENT -> agentGenerator.generateBlocking(application, request);
            case WORKFLOW, CHATFLOW -> workflowGenerator().generateBlocking(application, request);
            case CHAT, COMPLETION -> throw unsupportedDirectMode(application);
        };
    }

    void generateStream(AppEntity application, OpenAIChatRequest request, SseBridge.Emit emitter) {
        switch (AppMode.from(application.getMode())) {
            case AGENT -> agentGenerator.generateStream(application, request, emitter);
            case WORKFLOW, CHATFLOW -> workflowGenerator().generateStream(application, request, emitter);
            case CHAT, COMPLETION -> throw unsupportedDirectMode(application);
        }
    }

    static boolean isFlowApplication(AppEntity application) {
        return AppMode.from(application.getMode()).isFlow();
    }

    private static PlatformException unsupportedDirectMode(AppEntity application) {
        return new PlatformException(
                "app_mode_not_implemented",
                "Application mode '" + AppMode.from(application.getMode()).value()
                        + "' requires a direct LLM runtime and must not use the Agent runtime.",
                null);
    }

    private WorkflowChatGenerator workflowGenerator() {
        WorkflowChatGenerator existingGenerator = workflowGenerator;
        if (existingGenerator != null) {
            return existingGenerator;
        }
        synchronized (this) {
            if (workflowGenerator == null) {
                workflowGenerator = createWorkflowGenerator();
            }
            return workflowGenerator;
        }
    }

    private WorkflowChatGenerator createWorkflowGenerator() {
        WorkflowService workflowService = workflowServiceProvider.getIfAvailable();
        if (workflowService == null) {
            throw new PlatformException(
                    "workflow_unavailable", "A flow-mode app requires agentstart-workflow on the classpath.", null);
        }
        return new WorkflowChatGenerator(workflowService, memoryManagerProvider.getIfAvailable());
    }
}
