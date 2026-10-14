package io.aigoodle.trigger.dispatch;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Default dispatcher: runs a stored workflow by id with the trigger payload as inputs.
 */
public class WorkflowTriggerDispatcher implements TriggerDispatcher {

    public static final String TARGET_TYPE = "workflow";

    private final WorkflowService workflowService;

    public WorkflowTriggerDispatcher(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
        return dispatch(targetId, inputs, conversationId, null);
    }

    @Override
    public DispatchResult dispatch(
            String targetId, Map<String, Object> inputs, String conversationId, Consumer<String> textConsumer) {
        WorkflowRunResult result = workflowService.runForTenant(
                targetId,
                inputs,
                conversationId,
                UserContextHolder.currentTenantId(),
                textConsumer == null ? null : new ChatStreamSink(textConsumer));
        if (result.isSuccess()) {
            return DispatchResult.ok(result.getRunId(), result.getOutputs());
        }
        DispatchResult failed = DispatchResult.failed(result.getError());
        failed.setRunId(result.getRunId());
        return failed;
    }
}
