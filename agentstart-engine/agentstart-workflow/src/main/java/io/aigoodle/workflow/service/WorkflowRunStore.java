package io.aigoodle.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.entity.WorkflowRunEntity;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Isolates the best-effort observability persistence from workflow execution. */
final class WorkflowRunStore {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunStore.class);
    private static final int MAX_HISTORY_SIZE = 200;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final WorkflowRunMapper workflowRunMapper;

    WorkflowRunStore(WorkflowRunMapper workflowRunMapper) {
        this.workflowRunMapper = workflowRunMapper;
    }

    List<WorkflowRunEntity> findRecent(String workflowId, int requestedLimit) {
        return findRecent(UserContextHolder.currentTenantId(), workflowId, requestedLimit);
    }

    List<WorkflowRunEntity> findRecent(String tenantId, String workflowId, int requestedLimit) {
        int historySize = Math.max(1, Math.min(MAX_HISTORY_SIZE, requestedLimit));
        return workflowRunMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
                .eq(WorkflowRunEntity::getTenantId, tenantId)
                .eq(WorkflowRunEntity::getWorkflowId, workflowId)
                .orderByDesc(WorkflowRunEntity::getCreatedAt)
                .last("limit " + historySize));
    }

    void recordStoredRun(
            String tenantId,
            String workflowId,
            String conversationId,
            Map<String, Object> inputs,
            WorkflowRunResult result) {
        record(new RunRecord(tenantId, workflowId, conversationId, inputs, result));
    }

    void recordAdHocRun(String tenantId, String conversationId, Map<String, Object> inputs, WorkflowRunResult result) {
        record(new RunRecord(tenantId, null, conversationId, inputs, result));
    }

    private void record(RunRecord record) {
        try {
            WorkflowRunEntity entity = toEntity(record);
            if (workflowRunMapper.update(
                            entity,
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowRunEntity>()
                                    .eq(WorkflowRunEntity::getTenantId, entity.getTenantId())
                                    .eq(WorkflowRunEntity::getId, entity.getId()))
                    == 0) {
                workflowRunMapper.insert(entity);
            }
        } catch (Exception exception) {
            // A failed observability write must not turn a completed workflow into a failed run.
            log.warn("Could not persist workflow run {}: {}", record.result().getRunId(), exception.getMessage());
        }
    }

    private static WorkflowRunEntity toEntity(RunRecord record) {
        WorkflowRunResult result = record.result();
        WorkflowRunEntity entity = new WorkflowRunEntity();
        entity.setId(result.getRunId());
        entity.setTenantId(record.tenantId());
        entity.setWorkflowId(record.workflowId());
        entity.setConversationId(record.conversationId());
        entity.setStatus(
                result.isSuccess()
                        ? STATUS_SUCCESS
                        : result.getStatus() == null
                                ? STATUS_FAILED
                                : result.getStatus().name());
        entity.setInputsJson(WorkflowDataSanitizer.summary(record.inputs()));
        entity.setOutputsJson(WorkflowDataSanitizer.summary(result.getOutputs()));
        entity.setStepsJson(JsonUtils.toJson(
                result.getSteps().stream().map(WorkflowRunStore::stepSummary).toList()));
        entity.setError(result.getError());
        return entity;
    }

    private static Map<String, Object> stepSummary(io.aigoodle.workflow.node.StepRecord step) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("nodeId", step.getNodeId());
        value.put("nodeType", step.getNodeType());
        value.put("attempt", step.getAttempt());
        value.put("startedAt", step.getStartedAt());
        value.put("finishedAt", step.getFinishedAt());
        value.put("elapsedMillis", step.getElapsedMillis());
        value.put("failed", step.isFailed());
        value.put("tokenCount", step.getTokenCount());
        value.put("cost", step.getCost());
        value.put("externalStatus", step.getExternalStatus());
        value.put("traceId", step.getTraceId());
        value.put("spanId", step.getSpanId());
        value.put("outputSummary", WorkflowDataSanitizer.summary(step.getOutputs()));
        value.put("error", step.getError());
        return value;
    }

    private record RunRecord(
            String tenantId,
            String workflowId,
            String conversationId,
            Map<String, Object> inputs,
            WorkflowRunResult result) {}
}
