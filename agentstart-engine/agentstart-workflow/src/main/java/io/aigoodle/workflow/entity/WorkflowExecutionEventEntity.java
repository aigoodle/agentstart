package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Append-only, privacy-aware workflow execution event. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_workflow_execution_events")
public class WorkflowExecutionEventEntity extends BaseEntity {
    private String runId;
    private String nodeId;
    private String eventType;
    private Integer attempt;
    private String payloadJson;
    private String inputSummary;
    private String outputSummary;
    private Long tokenCount;
    private String cost;
    private Integer externalStatus;
    private String checkpointId;
    private String traceId;
    private String spanId;
    private String executorInstance;
}
