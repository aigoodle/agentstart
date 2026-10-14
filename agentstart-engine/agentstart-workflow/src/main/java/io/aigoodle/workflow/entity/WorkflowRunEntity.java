package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One execution of a workflow, kept for observability / replay.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_workflow_runs")
public class WorkflowRunEntity extends BaseEntity {

    private String workflowId;

    private String conversationId;

    /** SUCCESS, FAILED, CANCELLED, TIMED_OUT, WAITING, or another lifecycle state. */
    private String status;

    private String inputsJson;

    private String outputsJson;

    private String stepsJson;

    private String error;

    private Long elapsedMillis;
}
