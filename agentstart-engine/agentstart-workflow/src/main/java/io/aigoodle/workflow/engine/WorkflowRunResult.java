package io.aigoodle.workflow.engine;

import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * The outcome of a whole workflow run: the END node's outputs, every executed step
 * (for observability) and overall success/error.
 */
@Data
public class WorkflowRunResult {

    private String runId;
    private boolean success;
    private WorkflowRunStatus status;
    private String error;
    private Map<String, Object> outputs;
    private List<StepRecord> steps;
    private String waitingNodeId;
    private WorkflowWaitRequest waitRequest;

    public static WorkflowRunResult forRun(String runId, List<StepRecord> steps) {
        WorkflowRunResult result = new WorkflowRunResult();
        result.runId = runId;
        result.steps = steps;
        result.status = WorkflowRunStatus.RUNNING;
        return result;
    }

    public WorkflowRunResult succeed(Map<String, Object> outputs) {
        this.success = true;
        this.status = WorkflowRunStatus.SUCCEEDED;
        this.error = null;
        this.outputs = outputs;
        return this;
    }

    public WorkflowRunResult terminate(WorkflowRunStatus status, String error, Map<String, Object> outputs) {
        this.success = false;
        this.status = status;
        this.error = error;
        this.outputs = outputs;
        return this;
    }

    public WorkflowRunResult waiting(String nodeId, WorkflowWaitRequest request, Map<String, Object> outputs) {
        this.success = false;
        this.status = WorkflowRunStatus.WAITING;
        this.waitingNodeId = nodeId;
        this.waitRequest = request;
        this.outputs = outputs;
        return this;
    }

    public WorkflowRunResult fail(String error, Map<String, Object> outputs) {
        this.success = false;
        this.status = WorkflowRunStatus.FAILED;
        this.error = error;
        this.outputs = outputs;
        return this;
    }

    /** Convenience accessor for a single named output. */
    public Object output(String key) {
        return outputs == null ? null : outputs.get(key);
    }

    public String text() {
        Object textOutput = output("text");
        if (textOutput == null && outputs != null && outputs.size() == 1) {
            textOutput = outputs.values().iterator().next();
        }
        return textOutput == null ? null : String.valueOf(textOutput);
    }
}
