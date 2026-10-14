package io.aigoodle.trigger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.trigger.api.InvocationStatus;
import io.aigoodle.trigger.dispatch.DispatchResult;
import io.aigoodle.trigger.dispatch.TriggerDispatcherRegistry;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.entity.TriggerInvocationEntity;
import io.aigoodle.trigger.mapper.TriggerInvocationMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the persistence lifecycle of one trigger invocation. */
public final class TriggerInvocationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TriggerInvocationRunner.class);

    private final TriggerInvocationMapper invocationMapper;
    private final TriggerDispatcherRegistry dispatcherRegistry;

    public TriggerInvocationRunner(
            TriggerInvocationMapper invocationMapper, TriggerDispatcherRegistry dispatcherRegistry) {
        this.invocationMapper = invocationMapper;
        this.dispatcherRegistry = dispatcherRegistry;
    }

    TriggerInvocationEntity open(InvocationDraft invocationDraft, String tenantId) {
        TriggerInvocationEntity invocation = new TriggerInvocationEntity();
        invocation.setTenantId(tenantId);
        invocation.setTriggerId(invocationDraft.triggerId());
        invocation.setSource(invocationDraft.source());
        invocation.setConversationId(invocationDraft.conversationId());
        invocation.setStatus(InvocationStatus.PENDING);
        invocation.setPayloadJson(JsonUtils.toJson(invocationDraft.payload()));
        invocation.setReplayOf(invocationDraft.replayedInvocationId());
        invocationMapper.insert(invocation);
        return invocation;
    }

    DispatchResult execute(TriggerEntity trigger, TriggerInvocationEntity invocation, Map<String, Object> payload) {
        return execute(trigger, invocation, payload, trigger.getUserId());
    }

    DispatchResult execute(
            TriggerEntity trigger,
            TriggerInvocationEntity invocation,
            Map<String, Object> payload,
            String executionUserId) {
        return execute(trigger, invocation, payload, executionUserId, null);
    }

    DispatchResult execute(
            TriggerEntity trigger,
            TriggerInvocationEntity invocation,
            Map<String, Object> payload,
            String executionUserId,
            Consumer<String> textConsumer) {
        invocation.markRunning();
        save(invocation);
        try {
            CurrentUser scheduledUser = CurrentUser.builder()
                    .userId(
                            executionUserId == null || executionUserId.isBlank()
                                    ? trigger.getUserId()
                                    : executionUserId)
                    .tenantId(trigger.getTenantId())
                    .build();
            DispatchResult dispatchResult = UserContextHolder.callAs(scheduledUser, () -> dispatcherRegistry
                    .get(trigger.getTargetType())
                    .dispatch(
                            trigger.getTargetId(),
                            payload,
                            invocation.getConversationId() == null
                                    ? invocation.getId()
                                    : invocation.getConversationId(),
                            textConsumer));
            recordResult(invocation, dispatchResult);
            return dispatchResult;
        } catch (RuntimeException dispatchFailure) {
            logger.error(
                    "Trigger {} dispatch failed: {}", trigger.getId(), dispatchFailure.getMessage(), dispatchFailure);
            recordFailure(invocation, dispatchFailure);
            return DispatchResult.failed(dispatchFailure.getMessage());
        }
    }

    TriggerInvocationEntity find(String invocationId) {
        return find(UserContextHolder.currentTenantId(), invocationId);
    }

    TriggerInvocationEntity find(String tenantId, String invocationId) {
        return invocationMapper.selectOne(new LambdaQueryWrapper<TriggerInvocationEntity>()
                .eq(TriggerInvocationEntity::getTenantId, tenantId)
                .eq(TriggerInvocationEntity::getId, invocationId)
                .last("LIMIT 1"));
    }

    List<TriggerInvocationEntity> listForTrigger(String triggerId) {
        return listForTrigger(UserContextHolder.currentTenantId(), triggerId);
    }

    List<TriggerInvocationEntity> listForTrigger(String tenantId, String triggerId) {
        return invocationMapper.selectList(new LambdaQueryWrapper<TriggerInvocationEntity>()
                .eq(TriggerInvocationEntity::getTenantId, tenantId)
                .eq(TriggerInvocationEntity::getTriggerId, triggerId)
                .orderByDesc(TriggerInvocationEntity::getCreatedAt));
    }

    private void recordResult(TriggerInvocationEntity invocation, DispatchResult dispatchResult) {
        if (dispatchResult.isSuccess()) {
            invocation.markCompleted(dispatchResult.getRunId(), JsonUtils.toJson(dispatchResult.getOutputs()));
        } else {
            invocation.markFailed(
                    dispatchResult.getRunId(),
                    JsonUtils.toJson(dispatchResult.getOutputs()),
                    dispatchResult.getError());
        }
        save(invocation);
    }

    private void recordFailure(TriggerInvocationEntity invocation, RuntimeException dispatchFailure) {
        invocation.markFailed(dispatchFailure.getMessage());
        save(invocation);
    }

    private void save(TriggerInvocationEntity invocation) {
        invocationMapper.update(
                invocation,
                new LambdaUpdateWrapper<TriggerInvocationEntity>()
                        .eq(TriggerInvocationEntity::getTenantId, invocation.getTenantId())
                        .eq(TriggerInvocationEntity::getId, invocation.getId()));
    }
}
