package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Latest durable state and attempt metadata for one node in one run. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_workflow_run_nodes")
public class WorkflowRunNodeEntity extends BaseEntity {
    private String runId;
    private String nodeId;
    private String nodeType;
    private String status;
    private Integer attempt;
    private String selectedHandle;
    private String outputsJson;
    private String error;
    private String errorCode;
    private Boolean retryable;
    private String idempotencyKey;
    private String executionMode;
    private String resultCachePolicy;
    private Boolean resumable;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String executorInstance;
    private Long tokenCount;
    private String cost;
    private Integer externalStatus;
    private String traceId;
    private String spanId;
    private Long checkpointVersion;
}
