package io.aigoodle.trigger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import io.aigoodle.trigger.api.InvocationStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One firing of a trigger — kept for history and replay.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_trigger_invocations")
public class TriggerInvocationEntity extends BaseEntity {

    private String triggerId;

    /** {@code webhook}, {@code cron}, {@code event}, {@code manual}, {@code replay}. */
    private String source;

    /** Conversation to resume when a scheduled workflow is dispatched. */
    private String conversationId;

    private InvocationStatus status;

    /** The trigger payload, stored so the invocation can be replayed exactly. */
    private String payloadJson;

    /** Id of the produced workflow run (or other target run). */
    private String runId;

    private String outputsJson;

    private String error;

    /** When this is a replay, the id of the original invocation. */
    private String replayOf;

    public void markRunning() {
        status = InvocationStatus.RUNNING;
    }

    public void markCompleted(String dispatchedRunId, String serializedOutputs) {
        status = InvocationStatus.COMPLETED;
        runId = dispatchedRunId;
        outputsJson = serializedOutputs;
        error = null;
    }

    public void markFailed(String failureMessage) {
        status = InvocationStatus.FAILED;
        error = failureMessage;
    }

    public void markFailed(String dispatchedRunId, String serializedOutputs, String failureMessage) {
        status = InvocationStatus.FAILED;
        runId = dispatchedRunId;
        outputsJson = serializedOutputs;
        error = failureMessage;
    }
}
