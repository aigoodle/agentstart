package io.aigoodle.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.engine.NodeExecutionStatus;
import io.aigoodle.workflow.engine.WorkflowExecutionEventType;
import io.aigoodle.workflow.entity.WorkflowCheckpointEntity;
import io.aigoodle.workflow.entity.WorkflowExecutionEventEntity;
import io.aigoodle.workflow.entity.WorkflowResumeSignalEntity;
import io.aigoodle.workflow.entity.WorkflowRunNodeEntity;
import io.aigoodle.workflow.mapper.WorkflowCheckpointMapper;
import io.aigoodle.workflow.mapper.WorkflowExecutionEventMapper;
import io.aigoodle.workflow.mapper.WorkflowResumeSignalMapper;
import io.aigoodle.workflow.mapper.WorkflowRunNodeMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for recoverable workflow state, node state, and audit events. */
public class WorkflowCheckpointStore {

    private final WorkflowCheckpointMapper checkpointMapper;
    private final WorkflowRunNodeMapper nodeMapper;
    private final WorkflowExecutionEventMapper eventMapper;
    private final WorkflowResumeSignalMapper signalMapper;

    public WorkflowCheckpointStore(
            WorkflowCheckpointMapper checkpointMapper,
            WorkflowRunNodeMapper nodeMapper,
            WorkflowExecutionEventMapper eventMapper,
            WorkflowResumeSignalMapper signalMapper) {
        this.checkpointMapper = checkpointMapper;
        this.nodeMapper = nodeMapper;
        this.eventMapper = eventMapper;
        this.signalMapper = signalMapper;
    }

    @Transactional
    public WorkflowCheckpointEntity create(WorkflowCheckpointEntity checkpoint, List<WorkflowRunNodeEntity> nodes) {
        if (checkpoint.getId() == null) checkpoint.setId(UUID.randomUUID().toString());
        if (checkpoint.getCheckpointVersion() == null) checkpoint.setCheckpointVersion(0L);
        checkpointMapper.insert(checkpoint);
        if (nodes != null)
            nodes.forEach(node -> {
                prepareNode(checkpoint, node);
                nodeMapper.insert(node);
            });
        append(
                checkpoint.getTenantId(),
                checkpoint.getRunId(),
                null,
                WorkflowExecutionEventType.RUN_STARTED,
                null,
                checkpoint.getId(),
                null);
        return checkpoint;
    }

    public WorkflowCheckpointEntity require(String tenantId, String runId) {
        WorkflowCheckpointEntity value = checkpointMapper.selectOne(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getTenantId, tenantId)
                .eq(WorkflowCheckpointEntity::getRunId, runId)
                .last("LIMIT 1"));
        if (value == null)
            throw new PlatformException("checkpoint_not_found", "Checkpoint not found for run " + runId, null);
        return value;
    }

    public List<WorkflowRunNodeEntity> nodes(String tenantId, String runId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<WorkflowRunNodeEntity>()
                .eq(WorkflowRunNodeEntity::getTenantId, tenantId)
                .eq(WorkflowRunNodeEntity::getRunId, runId));
    }

    public List<WorkflowCheckpointEntity> dueSleeps(LocalDateTime now, int limit) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getStatus, io.aigoodle.workflow.engine.WorkflowRunStatus.WAITING.name())
                .eq(WorkflowCheckpointEntity::getWaitType, io.aigoodle.workflow.graph.NodeType.SLEEP_UNTIL.name())
                .isNotNull(WorkflowCheckpointEntity::getWakeAt)
                .le(WorkflowCheckpointEntity::getWakeAt, now)
                .orderByAsc(WorkflowCheckpointEntity::getWakeAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    public List<WorkflowCheckpointEntity> expiredWaits(LocalDateTime now, int limit) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getStatus, io.aigoodle.workflow.engine.WorkflowRunStatus.WAITING.name())
                .isNotNull(WorkflowCheckpointEntity::getWaitExpiresAt)
                .le(WorkflowCheckpointEntity::getWaitExpiresAt, now)
                .orderByAsc(WorkflowCheckpointEntity::getWaitExpiresAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    public List<WorkflowCheckpointEntity> waitingByCorrelation(String tenantId, String correlationKey) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getTenantId, tenantId)
                .eq(WorkflowCheckpointEntity::getStatus, io.aigoodle.workflow.engine.WorkflowRunStatus.WAITING.name())
                .eq(WorkflowCheckpointEntity::getCorrelationKey, correlationKey)
                .orderByAsc(WorkflowCheckpointEntity::getCreatedAt));
    }

    public List<WorkflowCheckpointEntity> cancelling(int limit) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(
                        WorkflowCheckpointEntity::getStatus,
                        io.aigoodle.workflow.engine.WorkflowRunStatus.CANCELLING.name())
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    public List<WorkflowCheckpointEntity> pausing(int limit) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getStatus, io.aigoodle.workflow.engine.WorkflowRunStatus.PAUSING.name())
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    /**
     * Finds executions abandoned by a crashed process. A null lease is only
     * considered stale after the grace cutoff so a freshly-created run cannot
     * be stolen between checkpoint creation and its first lease acquisition.
     */
    public List<WorkflowCheckpointEntity> staleRunning(LocalDateTime now, LocalDateTime nullLeaseCutoff, int limit) {
        return checkpointMapper.selectList(new LambdaQueryWrapper<WorkflowCheckpointEntity>()
                .eq(WorkflowCheckpointEntity::getStatus, io.aigoodle.workflow.engine.WorkflowRunStatus.RUNNING.name())
                .and(query -> query.le(WorkflowCheckpointEntity::getLeaseExpiresAt, now)
                        .or(nested -> nested.isNull(WorkflowCheckpointEntity::getLeaseExpiresAt)
                                .le(WorkflowCheckpointEntity::getUpdatedAt, nullLeaseCutoff)))
                .orderByAsc(WorkflowCheckpointEntity::getUpdatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    /** Marks a run that cannot be safely replayed as requiring operator action. */
    @Transactional
    public boolean failRecovery(WorkflowCheckpointEntity checkpoint, String reason) {
        checkpoint.setStatus(io.aigoodle.workflow.engine.WorkflowRunStatus.FAILED.name());
        checkpoint.setInterruptReason(reason);
        try {
            transition(checkpoint, checkpoint.getCheckpointVersion(), WorkflowExecutionEventType.RUN_FAILED);
            return true;
        } catch (PlatformException conflict) {
            if ("checkpoint_conflict".equals(conflict.getCode())) return false;
            throw conflict;
        }
    }

    @Transactional
    public boolean requestCancellation(String tenantId, String runId, String reason) {
        if (checkpointMapper.requestCancellation(tenantId, runId, reason, LocalDateTime.now()) != 1) return false;
        WorkflowCheckpointEntity state = require(tenantId, runId);
        WorkflowExecutionEventType type =
                io.aigoodle.workflow.engine.WorkflowRunStatus.CANCELLED.name().equals(state.getStatus())
                        ? WorkflowExecutionEventType.RUN_CANCELLED
                        : WorkflowExecutionEventType.RUN_CANCELLING;
        append(
                tenantId,
                runId,
                state.getResumeNodeId(),
                type,
                null,
                state.getId(),
                Map.of("status", state.getStatus(), "reason", reason));
        return true;
    }

    @Transactional
    public boolean finalizeAbandonedCancellation(WorkflowCheckpointEntity checkpoint) {
        if (checkpointMapper.finalizeAbandonedCancellation(
                        checkpoint.getTenantId(), checkpoint.getRunId(), LocalDateTime.now())
                != 1) return false;
        append(
                checkpoint.getTenantId(),
                checkpoint.getRunId(),
                checkpoint.getResumeNodeId(),
                WorkflowExecutionEventType.RUN_CANCELLED,
                null,
                checkpoint.getId(),
                Map.of("status", "CANCELLED", "reason", String.valueOf(checkpoint.getInterruptReason())));
        return true;
    }

    @Transactional
    public boolean requestPause(String tenantId, String runId, String reason) {
        if (checkpointMapper.requestPause(tenantId, runId, reason, LocalDateTime.now()) != 1) return false;
        WorkflowCheckpointEntity state = require(tenantId, runId);
        append(
                tenantId,
                runId,
                state.getResumeNodeId(),
                WorkflowExecutionEventType.RUN_PAUSING,
                null,
                state.getId(),
                Map.of("status", "PAUSING", "reason", reason));
        return true;
    }

    @Transactional
    public boolean finalizeAbandonedPause(WorkflowCheckpointEntity checkpoint) {
        if (checkpointMapper.finalizeAbandonedPause(
                        checkpoint.getTenantId(), checkpoint.getRunId(), LocalDateTime.now())
                != 1) return false;
        append(
                checkpoint.getTenantId(),
                checkpoint.getRunId(),
                checkpoint.getResumeNodeId(),
                WorkflowExecutionEventType.RUN_PAUSED,
                null,
                checkpoint.getId(),
                Map.of("status", "PAUSED", "reason", String.valueOf(checkpoint.getInterruptReason())));
        return true;
    }

    /** Acquires or renews a lease. Expired leases may be taken over by another instance. */
    public boolean acquireLease(String tenantId, String runId, String owner, Duration duration) {
        LocalDateTime now = LocalDateTime.now();
        return checkpointMapper.acquireLease(tenantId, runId, owner, now.plus(duration), now) == 1;
    }

    public boolean releaseLease(String tenantId, String runId, String owner) {
        return checkpointMapper.releaseLease(tenantId, runId, owner, LocalDateTime.now()) == 1;
    }

    public boolean claimSignal(
            String tenantId,
            String runId,
            String eventId,
            String correlationKey,
            String payloadHash,
            String resumedBy) {
        WorkflowResumeSignalEntity signal = new WorkflowResumeSignalEntity();
        signal.setId(UUID.randomUUID().toString());
        signal.setTenantId(tenantId);
        signal.setRunId(runId);
        signal.setEventId(eventId);
        signal.setCorrelationKey(correlationKey);
        signal.setPayloadHash(payloadHash);
        signal.setResumedBy(resumedBy);
        try {
            return signalMapper.insert(signal) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Transactional
    public boolean acceptSignal(
            WorkflowCheckpointEntity checkpoint,
            long expectedVersion,
            String eventId,
            String payloadHash,
            String resumedBy) {
        if (!claimSignal(
                checkpoint.getTenantId(),
                checkpoint.getRunId(),
                eventId,
                checkpoint.getCorrelationKey(),
                payloadHash,
                resumedBy)) return false;
        if (checkpointMapper.compareAndSet(checkpoint.getTenantId(), checkpoint, expectedVersion, LocalDateTime.now())
                != 1) {
            throw new PlatformException(
                    "checkpoint_conflict", "Checkpoint changed while accepting resume signal", null);
        }
        append(
                checkpoint.getTenantId(),
                checkpoint.getRunId(),
                checkpoint.getResumeNodeId(),
                WorkflowExecutionEventType.RUN_RESUMED,
                null,
                checkpoint.getId(),
                Map.of("eventId", eventId, "resumedBy", resumedBy));
        checkpoint.setCheckpointVersion(expectedVersion + 1);
        return true;
    }

    /**
     * Atomically records the node and advances the checkpoint with optimistic CAS.
     * A stale executor loses the transaction, including its node/event writes.
     */
    @Transactional
    public long commitNode(
            WorkflowCheckpointEntity next,
            long expectedVersion,
            WorkflowRunNodeEntity node,
            WorkflowExecutionEventType eventType) {
        prepareNode(next, node);
        node.setCheckpointVersion(expectedVersion + 1);
        int updated = nodeMapper.update(
                node,
                new LambdaUpdateWrapper<WorkflowRunNodeEntity>()
                        .eq(WorkflowRunNodeEntity::getTenantId, next.getTenantId())
                        .eq(WorkflowRunNodeEntity::getRunId, next.getRunId())
                        .eq(WorkflowRunNodeEntity::getNodeId, node.getNodeId()));
        if (updated == 0) nodeMapper.insert(node);
        if (checkpointMapper.compareAndSet(next.getTenantId(), next, expectedVersion, LocalDateTime.now()) != 1) {
            throw new PlatformException(
                    "checkpoint_conflict", "Checkpoint version changed for run " + next.getRunId(), null);
        }
        long committedVersion = expectedVersion + 1;
        if (node.getOutputsJson() != null
                && !node.getOutputsJson().equals("{}")
                && eventType != WorkflowExecutionEventType.NODE_OUTPUT) {
            append(
                    next.getTenantId(),
                    next.getRunId(),
                    node.getNodeId(),
                    WorkflowExecutionEventType.NODE_OUTPUT,
                    node.getAttempt(),
                    next.getId(),
                    Map.of("outputSummary", WorkflowDataSanitizer.summary(JsonUtils.parseMap(node.getOutputsJson()))));
        }
        appendNodeEvent(next, node, eventType);
        next.setCheckpointVersion(committedVersion);
        return committedVersion;
    }

    @Transactional
    public long transition(WorkflowCheckpointEntity next, long expectedVersion, WorkflowExecutionEventType eventType) {
        if (checkpointMapper.compareAndSet(next.getTenantId(), next, expectedVersion, LocalDateTime.now()) != 1) {
            throw new PlatformException(
                    "checkpoint_conflict", "Checkpoint version changed for run " + next.getRunId(), null);
        }
        append(
                next.getTenantId(),
                next.getRunId(),
                null,
                eventType,
                null,
                next.getId(),
                Map.of("status", next.getStatus(), "version", expectedVersion + 1));
        next.setCheckpointVersion(expectedVersion + 1);
        return expectedVersion + 1;
    }

    private void append(
            String tenantId,
            String runId,
            String nodeId,
            WorkflowExecutionEventType type,
            Integer attempt,
            String checkpointId,
            Map<String, Object> safePayload) {
        WorkflowExecutionEventEntity event = new WorkflowExecutionEventEntity();
        event.setId(UUID.randomUUID().toString());
        event.setTenantId(tenantId);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setEventType(type.name());
        event.setAttempt(attempt);
        event.setCheckpointId(checkpointId);
        event.setPayloadJson(safePayload == null ? null : JsonUtils.toJson(safePayload));
        eventMapper.insert(event);
    }

    private static Map<String, Object> summary(WorkflowRunNodeEntity node) {
        return Map.of(
                "status",
                node.getStatus(),
                "hasOutputs",
                node.getOutputsJson() != null,
                "failed",
                node.getStatus().equals(NodeExecutionStatus.FAILED.name()));
    }

    private void appendNodeEvent(
            WorkflowCheckpointEntity checkpoint, WorkflowRunNodeEntity node, WorkflowExecutionEventType type) {
        WorkflowExecutionEventEntity event = new WorkflowExecutionEventEntity();
        event.setId(UUID.randomUUID().toString());
        event.setTenantId(checkpoint.getTenantId());
        event.setRunId(checkpoint.getRunId());
        event.setNodeId(node.getNodeId());
        event.setEventType(type.name());
        event.setAttempt(node.getAttempt());
        event.setCheckpointId(checkpoint.getId());
        event.setExecutorInstance(node.getExecutorInstance());
        event.setTokenCount(node.getTokenCount());
        event.setCost(node.getCost());
        event.setExternalStatus(node.getExternalStatus());
        event.setTraceId(node.getTraceId());
        event.setSpanId(node.getSpanId());
        event.setOutputSummary(WorkflowDataSanitizer.summary(
                node.getOutputsJson() == null ? Map.of() : JsonUtils.parseMap(node.getOutputsJson())));
        event.setPayloadJson(JsonUtils.toJson(summary(node)));
        eventMapper.insert(event);
    }

    private static void prepareNode(WorkflowCheckpointEntity checkpoint, WorkflowRunNodeEntity node) {
        if (node.getId() == null) node.setId(checkpoint.getRunId() + ":" + node.getNodeId());
        node.setTenantId(checkpoint.getTenantId());
        node.setRunId(checkpoint.getRunId());
    }
}
