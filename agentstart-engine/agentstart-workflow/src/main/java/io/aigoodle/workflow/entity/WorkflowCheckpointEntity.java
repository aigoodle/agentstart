package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Recoverable execution state for one workflow run. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_workflow_checkpoints")
public class WorkflowCheckpointEntity extends BaseEntity {
    private String runId;
    private String workflowId;
    private String graphVersion;
    private String graphJson;
    private String inputsJson;
    private String conversationId;
    private String status;
    private String nodeStatesJson;
    private String variablePoolJson;
    private String branchResultsJson;
    private String iterationCursorsJson;
    private String pendingNodesJson;
    private String resumeNodeId;
    private String interruptReason;
    private Long checkpointVersion;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private String waitType;
    private String correlationKey;
    private String resumeTokenHash;
    private String inputSchemaJson;
    private LocalDateTime waitExpiresAt;
    private LocalDateTime wakeAt;
    private String resumedBy;
    private LocalDateTime resumedAt;
}
