package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.WorkflowCheckpointEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkflowCheckpointMapper extends BaseMapper<WorkflowCheckpointEntity> {

    @Update("UPDATE goodle_workflow_checkpoints SET status=#{state.status}, node_states_json=#{state.nodeStatesJson}, "
            + "variable_pool_json=#{state.variablePoolJson}, branch_results_json=#{state.branchResultsJson}, "
            + "iteration_cursors_json=#{state.iterationCursorsJson}, pending_nodes_json=#{state.pendingNodesJson}, "
            + "resume_node_id=#{state.resumeNodeId}, interrupt_reason=#{state.interruptReason}, "
            + "wait_type=#{state.waitType}, correlation_key=#{state.correlationKey}, "
            + "resume_token_hash=#{state.resumeTokenHash}, input_schema_json=#{state.inputSchemaJson}, "
            + "wait_expires_at=#{state.waitExpiresAt}, wake_at=#{state.wakeAt}, "
            + "resumed_by=#{state.resumedBy}, resumed_at=#{state.resumedAt}, "
            + "checkpoint_version=checkpoint_version+1, updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND run_id=#{state.runId} AND checkpoint_version=#{expectedVersion}")
    int compareAndSet(
            @Param("tenantId") String tenantId,
            @Param("state") WorkflowCheckpointEntity state,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE goodle_workflow_checkpoints SET lease_owner=#{owner}, lease_expires_at=#{expiresAt}, updated_at=#{now} "
                    + "WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND "
                    + "(lease_owner IS NULL OR lease_owner=#{owner} OR lease_expires_at IS NULL OR lease_expires_at < #{now})")
    int acquireLease(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("owner") String owner,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Update("UPDATE goodle_workflow_checkpoints SET lease_owner=NULL, lease_expires_at=NULL, updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND lease_owner=#{owner}")
    int releaseLease(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);

    @Update("UPDATE goodle_workflow_checkpoints SET "
            + "status=CASE WHEN status='WAITING' THEN 'CANCELLED' ELSE 'CANCELLING' END, "
            + "interrupt_reason=#{reason}, "
            + "resume_token_hash=CASE WHEN status='WAITING' THEN NULL ELSE resume_token_hash END, "
            + "updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND run_id=#{runId} "
            + "AND status IN ('RUNNING','WAITING')")
    int requestCancellation(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE goodle_workflow_checkpoints SET status='CANCELLED', lease_owner=NULL, "
                    + "lease_expires_at=NULL, updated_at=#{now} WHERE tenant_id=#{tenantId} AND run_id=#{runId} "
                    + "AND status='CANCELLING' AND (lease_owner IS NULL OR lease_expires_at IS NULL OR lease_expires_at < #{now})")
    int finalizeAbandonedCancellation(
            @Param("tenantId") String tenantId, @Param("runId") String runId, @Param("now") LocalDateTime now);

    @Update("UPDATE goodle_workflow_checkpoints SET status='PAUSING', interrupt_reason=#{reason}, updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND status='RUNNING'")
    int requestPause(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now);

    @Update("UPDATE goodle_workflow_checkpoints SET status='PAUSED', lease_owner=NULL, lease_expires_at=NULL, "
            + "updated_at=#{now} WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND status='PAUSING' "
            + "AND (lease_owner IS NULL OR lease_expires_at IS NULL OR lease_expires_at < #{now})")
    int finalizeAbandonedPause(
            @Param("tenantId") String tenantId, @Param("runId") String runId, @Param("now") LocalDateTime now);
}
