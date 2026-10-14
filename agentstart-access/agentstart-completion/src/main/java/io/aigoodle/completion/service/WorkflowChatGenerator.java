package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.service.WorkflowService;
import io.aigoodle.workflow.service.WorkflowSignalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs workflow applications through blocking or streaming chat transports. */
public class WorkflowChatGenerator {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowChatGenerator.class);

    private final WorkflowService workflowService;
    private final MemoryManager memoryManager;

    public WorkflowChatGenerator(WorkflowService workflowService, MemoryManager memoryManager) {
        this.workflowService = workflowService;
        this.memoryManager = memoryManager;
    }

    public OpenAIChatResponse generateBlocking(AppEntity application, OpenAIChatRequest request) {
        WorkflowChatContext chatContext = WorkflowChatContext.resolve(application, request, logger);
        WorkflowRunResult runResult = resumeOrRun(application, request, chatContext);
        requireSuccess(runResult);

        String answer = WorkflowAnswerExtractor.extract(runResult);
        appendHistory(
                UserContextHolder.currentTenantId(),
                application.getId(),
                chatContext.conversationId(),
                request.lastUserMessage(),
                answer);
        return OpenAIChatResponse.completion(request.getModel(), answer);
    }

    public void generateStream(AppEntity application, OpenAIChatRequest request, SseBridge.Emit emitter) {
        WorkflowChatContext chatContext = WorkflowChatContext.resolve(application, request, logger);
        WorkflowStreamSession streamSession = new WorkflowStreamSession(application, request, chatContext, emitter);
        streamSession.start();

        WorkflowRunResult runResult;
        try {
            if (request.getHumanInput() != null) {
                runResult = requireAccepted(workflowService.signal(
                                UserContextHolder.currentTenantId(),
                                request.getHumanInput().getRunId(),
                                request.getHumanInput().getResumeToken(),
                                request.getHumanInput().getEventId(),
                                request.getHumanInput().getPayload()))
                        .runResult();
            } else {
                runResult = application.getTenantId().equals(UserContextHolder.currentTenantId())
                        ? workflowService.run(
                                chatContext.workflowId(),
                                chatContext.inputs(),
                                chatContext.conversationId(),
                                streamSession::nodeFinished,
                                streamSession.sink())
                        : workflowService.runSharedPublished(
                                application.getId(),
                                application.getTenantId(),
                                chatContext.inputs(),
                                chatContext.conversationId(),
                                streamSession::nodeFinished,
                                streamSession.sink());
            }
        } catch (RuntimeException runFailure) {
            logger.warn("Workflow chat run failed for app {}: {}", application.getId(), runFailure.getMessage());
            streamSession.fail(runFailure);
            return;
        }

        String persistedAnswer = streamSession.complete(runResult);
        // WAITING is a completed chat turn too: persist the original question,
        // any direct-output text, and the renderable human-input form. Previously
        // only SUCCESS was stored, so history started at the later resume request.
        if (runResult.isSuccess() || runResult.getStatus() == WorkflowRunStatus.WAITING) {
            appendHistory(
                    UserContextHolder.currentTenantId(),
                    application.getId(),
                    chatContext.conversationId(),
                    request.lastUserMessage(),
                    persistedAnswer);
        }
    }

    private WorkflowRunResult resumeOrRun(
            AppEntity application, OpenAIChatRequest request, WorkflowChatContext context) {
        if (request.getHumanInput() == null) {
            return application.getTenantId().equals(UserContextHolder.currentTenantId())
                    ? workflowService.run(context.workflowId(), context.inputs(), context.conversationId())
                    : workflowService.runSharedPublished(
                            application.getId(),
                            application.getTenantId(),
                            context.inputs(),
                            context.conversationId(),
                            null,
                            null);
        }
        return requireAccepted(workflowService.signal(
                        UserContextHolder.currentTenantId(),
                        request.getHumanInput().getRunId(),
                        request.getHumanInput().getResumeToken(),
                        request.getHumanInput().getEventId(),
                        request.getHumanInput().getPayload()))
                .runResult();
    }

    private static WorkflowSignalResult requireAccepted(WorkflowSignalResult signal) {
        if (signal == null || !signal.accepted() || signal.runResult() == null) {
            throw new PlatformException(
                    "human_input_not_accepted",
                    signal != null && signal.duplicate()
                            ? "This human input was already submitted"
                            : "Workflow did not accept the human input",
                    null);
        }
        return signal;
    }

    private void appendHistory(String tenantId, String appId, String conversationId, String userQuery, String answer) {
        if (memoryManager == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            memoryManager.rememberExchange(tenantId, appId, conversationId, userQuery, answer);
        } catch (RuntimeException historyFailure) {
            logger.debug("Workflow chat history write skipped: {}", historyFailure.getMessage());
        }
    }

    private static void requireSuccess(WorkflowRunResult runResult) {
        if (!runResult.isSuccess()) {
            throw new PlatformException(
                    "workflow_failed", runResult.getError() == null ? "Workflow failed" : runResult.getError(), null);
        }
    }
}
