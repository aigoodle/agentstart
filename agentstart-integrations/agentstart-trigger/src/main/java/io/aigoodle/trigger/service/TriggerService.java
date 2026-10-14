package io.aigoodle.trigger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.persistence.TenantSqlScope;
import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.cron.TriggerSchedule;
import io.aigoodle.trigger.dispatch.DispatchResult;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.entity.TriggerInvocationEntity;
import io.aigoodle.trigger.mapper.TriggerMapper;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.service.WorkflowService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages triggers and fires them — synchronously or asynchronously — recording every invocation
 * for history and replay. Decoupled from what runs via {@link TriggerDispatcherRegistry}.
 */
public class TriggerService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerService.class);

    private final TriggerMapper triggerMapper;
    private final TriggerInvocationRunner invocationRunner;
    private final Executor executor;
    private final List<TriggerChangeListener> changeListeners;

    public TriggerService(
            TriggerMapper triggerMapper,
            TriggerInvocationRunner invocationRunner,
            Executor executor,
            List<TriggerChangeListener> changeListeners) {
        this.triggerMapper = triggerMapper;
        this.invocationRunner = invocationRunner;
        this.executor = executor;
        this.changeListeners = changeListeners == null ? List.of() : List.copyOf(changeListeners);
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public TriggerEntity create(CreateTriggerRequest request) {
        TriggerEntity trigger = newTrigger(request);
        triggerMapper.insert(trigger);
        notifySaved(trigger);
        return trigger;
    }

    @Transactional
    public void setEnabled(String triggerId, boolean enabled) {
        TriggerEntity trigger = require(triggerId);
        trigger.setEnabled(enabled);
        if (trigger.getType() == TriggerType.CRON) {
            trigger.setNextFireAt(
                    enabled ? TriggerSchedule.from(config(trigger)).firstFireAt(LocalDateTime.now()) : null);
            trigger.setLockOwner(null);
            trigger.setLockUntil(null);
        }
        updateOwned(trigger);
        if (enabled) {
            notifySaved(trigger);
        } else {
            changeListeners.forEach(listener -> listener.onRemoved(triggerId));
        }
    }

    @Transactional
    public void setEnabled(String tenantId, String triggerId, boolean enabled) {
        TriggerEntity trigger = require(tenantId, triggerId);
        trigger.setEnabled(enabled);
        if (trigger.getType() == TriggerType.CRON) {
            trigger.setNextFireAt(
                    enabled ? TriggerSchedule.from(config(trigger)).firstFireAt(LocalDateTime.now()) : null);
            trigger.setLockOwner(null);
            trigger.setLockUntil(null);
        }
        triggerMapper.update(
                trigger,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TriggerEntity>()
                        .eq(TriggerEntity::getTenantId, trigger.getTenantId())
                        .eq(TriggerEntity::getId, trigger.getId()));
        if (enabled) notifySaved(trigger);
        else changeListeners.forEach(listener -> listener.onRemoved(triggerId));
    }

    @Transactional
    public void delete(String triggerId) {
        TriggerEntity trigger = require(triggerId);
        deleteOwned(trigger);
        changeListeners.forEach(listener -> listener.onRemoved(triggerId));
    }

    @Transactional
    public void delete(String tenantId, String triggerId) {
        TriggerEntity trigger = require(tenantId, triggerId);
        triggerMapper.delete(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, trigger.getTenantId())
                .eq(TriggerEntity::getId, triggerId));
        changeListeners.forEach(listener -> listener.onRemoved(triggerId));
    }

    public TriggerEntity require(String triggerId) {
        return require(UserContextHolder.currentTenantId(), triggerId);
    }

    public TriggerEntity require(String tenantId, String triggerId) {
        TriggerEntity trigger = triggerMapper.selectOne(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(TriggerEntity::getId, triggerId)
                .last("LIMIT 1"));
        if (trigger == null) throw new PlatformException("trigger_not_found", "Trigger not found", null);
        return trigger;
    }

    public List<TriggerEntity> list(String tenantId) {
        return triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId));
    }

    public IPage<TriggerEntity> page(String tenantId, long page, long pageSize, String keyword, String category) {
        LambdaQueryWrapper<TriggerEntity> query = new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .orderByDesc(TriggerEntity::getCreatedAt);
        if ("APPLICATION".equalsIgnoreCase(category)) {
            query.like(TriggerEntity::getConfigJson, "\"sourceWorkflowKey\"");
        } else if ("USER".equalsIgnoreCase(category)) {
            query.and(wrapper -> wrapper.isNull(TriggerEntity::getConfigJson)
                    .or()
                    .notLike(TriggerEntity::getConfigJson, "\"sourceWorkflowKey\""));
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper.like(TriggerEntity::getName, keyword.trim())
                    .or()
                    .like(TriggerEntity::getType, keyword.trim())
                    .or()
                    .like(TriggerEntity::getTargetType, keyword.trim())
                    .or()
                    .like(TriggerEntity::getTargetId, keyword.trim()));
        }
        return triggerMapper.selectPage(new Page<>(page, pageSize), query);
    }

    public List<TriggerEntity> listEnabledByType(TriggerType type) {
        return listEnabledByType(UserContextHolder.currentTenantId(), type);
    }

    public List<TriggerEntity> listEnabledByType(String tenantId, TriggerType type) {
        return triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, tenantId == null || tenantId.isBlank() ? "default" : tenantId)
                .eq(TriggerEntity::getType, type)
                .eq(TriggerEntity::getEnabled, true));
    }

    public Map<String, Object> config(TriggerEntity trigger) {
        Map<String, Object> parsedConfig = JsonUtils.parseMap(trigger.getConfigJson());
        return parsedConfig == null ? Map.of() : parsedConfig;
    }

    /** Find an enabled webhook trigger by its configured {@code path}. */
    public java.util.Optional<TriggerEntity> findWebhook(String path) {
        return TenantSqlScope.bypass(() -> triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                        .eq(TriggerEntity::getType, TriggerType.WEBHOOK)
                        .eq(TriggerEntity::getEnabled, true)))
                .stream()
                .filter(trigger -> path != null
                        && path.equals(String.valueOf(config(trigger).get("path"))))
                .findFirst();
    }

    // --------------------------------------------------------------- firing

    /** Fire synchronously and return the dispatch result. */
    public DispatchResult fireSynchronously(TriggerInvocationRequest request) {
        TriggerEntity trigger = requireEnabled(request.triggerId());
        TriggerInvocationEntity invocation =
                invocationRunner.open(InvocationDraft.initial(request), trigger.getTenantId());
        return invocationRunner.execute(trigger, invocation, request.payload());
    }

    public DispatchResult fireSynchronously(String tenantId, TriggerInvocationRequest request) {
        TriggerEntity trigger = requireEnabled(tenantId, request.triggerId());
        TriggerInvocationEntity invocation =
                invocationRunner.open(InvocationDraft.initial(request), trigger.getTenantId());
        return invocationRunner.execute(trigger, invocation, request.payload());
    }

    /** Fires a trusted channel trigger as the owner of the configured channel account. */
    public DispatchResult fireSynchronouslyAs(
            String tenantId, String executionUserId, TriggerInvocationRequest request) {
        TriggerEntity trigger = requireEnabled(tenantId, request.triggerId());
        TriggerInvocationEntity invocation =
                invocationRunner.open(InvocationDraft.initial(request), trigger.getTenantId());
        return invocationRunner.execute(trigger, invocation, request.payload(), executionUserId);
    }

    /** All workflow schedules owned by one authenticated user, used as LLM deletion candidates. */
    public List<TriggerEntity> listUserSchedules(String tenantId, String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(TriggerEntity::getUserId, userId)
                .eq(TriggerEntity::getType, TriggerType.CRON)
                .eq(TriggerEntity::getTargetType, "workflow")
                .orderByDesc(TriggerEntity::getCreatedAt));
    }

    /** Deletes only ids that are still owned by the supplied tenant/user. */
    @Transactional
    public List<TriggerEntity> deleteOwnedSchedules(String tenantId, String userId, List<String> triggerIds) {
        if (userId == null || userId.isBlank() || triggerIds == null || triggerIds.isEmpty()) {
            return List.of();
        }
        List<String> distinctIds = triggerIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) return List.of();
        List<TriggerEntity> owned = triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                .in(TriggerEntity::getId, distinctIds)
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(TriggerEntity::getUserId, userId)
                .eq(TriggerEntity::getType, TriggerType.CRON)
                .eq(TriggerEntity::getTargetType, "workflow"));
        List<TriggerEntity> deleted = new ArrayList<>();
        owned.forEach(trigger -> {
            if (deleteOwned(trigger) == 1) {
                deleted.add(trigger);
                changeListeners.forEach(listener -> listener.onRemoved(trigger.getId()));
            }
        });
        return List.copyOf(deleted);
    }

    /** Enables or pauses only workflow schedules owned by the supplied tenant/user. */
    @Transactional
    public List<TriggerEntity> setOwnedSchedulesEnabled(
            String tenantId, String userId, List<String> triggerIds, boolean enabled) {
        List<TriggerEntity> owned = ownedSchedules(tenantId, userId, triggerIds);
        owned.forEach(trigger -> {
            trigger.setEnabled(enabled);
            trigger.setNextFireAt(
                    enabled ? TriggerSchedule.from(config(trigger)).firstFireAt(LocalDateTime.now()) : null);
            trigger.setLockOwner(null);
            trigger.setLockUntil(null);
            updateOwned(trigger);
            if (enabled) notifySaved(trigger);
            else changeListeners.forEach(listener -> listener.onRemoved(trigger.getId()));
        });
        return List.copyOf(owned);
    }

    /** Starts an immediate asynchronous invocation for owned, enabled workflow schedules. */
    public Map<TriggerEntity, String> runOwnedSchedulesNow(String tenantId, String userId, List<String> triggerIds) {
        Map<TriggerEntity, String> started = new java.util.LinkedHashMap<>();
        ownedSchedules(tenantId, userId, triggerIds).stream()
                .filter(trigger -> Boolean.TRUE.equals(trigger.getEnabled()))
                .forEach(trigger -> {
                    Map<String, Object> triggerConfig = config(trigger);
                    Object rawData = triggerConfig.get("data");
                    Map<String, Object> data = rawData instanceof Map<?, ?> map
                            ? map.entrySet().stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
                            : Map.of();
                    String conversationId = text(triggerConfig.get("conversationId"));
                    String invocationId = fireAsynchronously(
                            trigger.getTenantId(),
                            TriggerInvocationRequest.scheduled(trigger.getId(), data, conversationId));
                    started.put(trigger, invocationId);
                });
        return Map.copyOf(started);
    }

    private List<TriggerEntity> ownedSchedules(String tenantId, String userId, List<String> triggerIds) {
        if (userId == null || userId.isBlank() || triggerIds == null || triggerIds.isEmpty()) {
            return List.of();
        }
        List<String> distinctIds = triggerIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) return List.of();
        return triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                .in(TriggerEntity::getId, distinctIds)
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(TriggerEntity::getUserId, userId)
                .eq(TriggerEntity::getType, TriggerType.CRON)
                .eq(TriggerEntity::getTargetType, "workflow"));
    }

    /** Updates one workflow schedule only when it is still owned by the supplied tenant/user. */
    @Transactional
    public TriggerEntity updateOwnedSchedule(
            String tenantId,
            String userId,
            String triggerId,
            String name,
            String targetWorkflowId,
            Map<String, Object> scheduleConfig) {
        if (userId == null || userId.isBlank() || triggerId == null || triggerId.isBlank()) {
            throw new PlatformException(
                    "schedule_update_forbidden", "Scheduled task is not available for update", null);
        }
        TriggerEntity trigger = triggerMapper.selectOne(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getId, triggerId)
                .eq(TriggerEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(TriggerEntity::getUserId, userId)
                .eq(TriggerEntity::getType, TriggerType.CRON)
                .eq(TriggerEntity::getTargetType, "workflow")
                .last("LIMIT 1"));
        if (trigger == null) {
            throw new PlatformException(
                    "schedule_update_forbidden", "Scheduled task is not available for update", null);
        }
        Map<String, Object> normalizedConfig =
                scheduleConfig == null ? Map.of() : new java.util.LinkedHashMap<>(scheduleConfig);
        trigger.setName(name == null || name.isBlank() ? trigger.getName() : name.trim());
        if (targetWorkflowId != null && !targetWorkflowId.isBlank()) {
            trigger.setTargetId(targetWorkflowId);
        }
        trigger.setConfigJson(JsonUtils.toJson(normalizedConfig));
        trigger.setEnabled(true);
        trigger.setNextFireAt(TriggerSchedule.from(normalizedConfig).firstFireAt(LocalDateTime.now()));
        trigger.setLockOwner(null);
        trigger.setLockUntil(null);
        updateOwned(trigger);
        notifySaved(trigger);
        return trigger;
    }

    /**
     * Reconciles the published workflow's START-node schedule or channel selector into one durable
     * trigger.
     */
    @Transactional
    public TriggerEntity syncPublishedWorkflowSchedule(WorkflowEntity workflow, WorkflowService workflowService) {
        String sourceKey = workflow.getAppId() == null ? workflow.getId() : workflow.getAppId();
        list(workflow.getTenantId()).stream()
                .filter(existing -> sourceKey.equals(config(existing).get("sourceWorkflowKey")))
                .forEach(existing -> delete(existing.getTenantId(), existing.getId()));
        NodeDef start;
        try {
            start = workflowService.graphOf(workflow).getNodes().stream()
                    .filter(node -> node.getType() == io.aigoodle.workflow.graph.NodeType.START)
                    .findFirst()
                    .orElse(null);
        } catch (PlatformException invalidGraph) {
            // Publishing an empty/legacy draft must not fail merely because it has no schedule.
            return null;
        }
        if (start == null) return null;
        Map<String, Object> data = start.getData() == null ? Map.of() : start.getData();
        Object rawTrigger = data.get("triggers");
        Map<String, Object> designer = rawTrigger instanceof Map<?, ?> map
                ? map.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
                : Map.of();
        if (!Boolean.TRUE.equals(data.get("triggersEnabled"))) {
            return null;
        }
        String triggerType = String.valueOf(designer.get("type"));
        if ("connector".equalsIgnoreCase(triggerType)) {
            String provider = text(designer.get("provider"));
            String channelId = text(designer.get("channelId"));
            if (provider == null || channelId == null) {
                throw new PlatformException(
                        "channel_trigger_invalid", "Message connector trigger requires provider and channelId", null);
            }
            Map<String, Object> config = new java.util.LinkedHashMap<>();
            // An empty connectionId subscribes to every active account of this channel; when present,
            // the inbound handler limits the workflow to that exact configured account.
            copy(
                    designer,
                    config,
                    "provider",
                    "channelId",
                    "channelName",
                    "connectionId",
                    "connectionName",
                    "messageTypes",
                    "replyMode");
            config.put("sourceWorkflowKey", sourceKey);
            return create(CreateTriggerRequest.builder()
                    .tenantId(workflow.getTenantId())
                    .userId(UserContextHolder.currentUserId())
                    .name(
                            text(designer.get("name")) == null
                                    ? workflow.getName() + " message connector"
                                    : text(designer.get("name")))
                    .type(TriggerType.CHANNEL_MESSAGE)
                    .targetType("workflow")
                    .targetId(workflow.getId())
                    .config(config)
                    .enabled(true)
                    .build());
        }
        if (!"schedule".equalsIgnoreCase(triggerType)) return null;
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        copy(designer, config, "scheduleType", "expression", "runAt", "timeZone", "conversationId");
        config.put("sourceWorkflowKey", sourceKey);
        Object payloadJson = designer.get("payloadJson");
        config.put("data", payloadJson == null ? Map.of() : JsonUtils.parseMap(String.valueOf(payloadJson)));
        String selectedTarget = text(designer.get("targetWorkflowId"));
        return create(CreateTriggerRequest.builder()
                .tenantId(workflow.getTenantId())
                .userId(UserContextHolder.currentUserId())
                .name(
                        text(designer.get("name")) == null
                                ? workflow.getName() + " schedule"
                                : text(designer.get("name")))
                .type(TriggerType.CRON)
                .targetType("workflow")
                .targetId(selectedTarget == null ? workflow.getId() : selectedTarget)
                .config(config)
                .enabled(true)
                .build());
    }

    /** Atomically leases due rows; safe when every cluster node calls it concurrently. */
    @Transactional
    public List<TriggerEntity> claimDueSchedules(String owner, Duration lease, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<TriggerEntity> due =
                TenantSqlScope.bypass(() -> triggerMapper.selectList(new LambdaQueryWrapper<TriggerEntity>()
                        .eq(TriggerEntity::getType, TriggerType.CRON)
                        .eq(TriggerEntity::getEnabled, true)
                        .isNotNull(TriggerEntity::getNextFireAt)
                        .le(TriggerEntity::getNextFireAt, now)
                        .orderByAsc(TriggerEntity::getNextFireAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200)))));
        List<TriggerEntity> claimed = new ArrayList<>();
        for (TriggerEntity candidate : due) {
            if (triggerMapper.tryClaim(candidate.getTenantId(), candidate.getId(), owner, now, now.plus(lease)) != 1)
                continue;
            TriggerEntity trigger = require(candidate.getTenantId(), candidate.getId());
            TriggerSchedule schedule = TriggerSchedule.from(config(trigger));
            trigger.setLastFireAt(trigger.getNextFireAt());
            trigger.setFireCount((trigger.getFireCount() == null ? 0L : trigger.getFireCount()) + 1L);
            trigger.setNextFireAt(schedule.nextAfter(trigger.getNextFireAt()));
            if (schedule.oneTime()) trigger.setEnabled(false);
            // The cursor is advanced before dispatch, so an expired lease cannot duplicate this
            // occurrence.
            trigger.setLockOwner(null);
            trigger.setLockUntil(null);
            updateOwned(trigger);
            claimed.add(trigger);
        }
        return claimed;
    }

    /** Fire asynchronously; returns the invocation id immediately. */
    public String fireAsynchronously(TriggerInvocationRequest request) {
        return fireAsynchronously(UserContextHolder.currentTenantId(), request);
    }

    public String fireAsynchronously(String tenantId, TriggerInvocationRequest request) {
        return fireAsynchronouslyAs(tenantId, null, request);
    }

    /**
     * Fire asynchronously under an explicit execution identity. Channel triggers use the employee
     * resolved from the signed platform message (falling back to the employee account owner), never
     * the user who originally published the shared workflow. The invocation is persisted before this
     * method returns.
     */
    public String fireAsynchronouslyAs(String tenantId, String executionUserId, TriggerInvocationRequest request) {
        return fireAsynchronouslyAs(tenantId, executionUserId, request, ignored -> {});
    }

    /**
     * Fires asynchronously and invokes the completion callback after the persisted invocation
     * finishes.
     */
    public String fireAsynchronouslyAs(
            String tenantId,
            String executionUserId,
            TriggerInvocationRequest request,
            java.util.function.Consumer<DispatchResult> completion) {
        return fireAsynchronouslyAs(tenantId, executionUserId, request, null, completion);
    }

    /** Fires asynchronously while forwarding workflow text chunks to a live channel reply. */
    public String fireAsynchronouslyAs(
            String tenantId,
            String executionUserId,
            TriggerInvocationRequest request,
            java.util.function.Consumer<String> textConsumer,
            java.util.function.Consumer<DispatchResult> completion) {
        TriggerEntity trigger = requireEnabled(tenantId, request.triggerId());
        TriggerInvocationEntity invocation =
                invocationRunner.open(InvocationDraft.initial(request), trigger.getTenantId());
        executor.execute(() -> {
            try {
                DispatchResult result = invocationRunner.execute(
                        trigger,
                        invocation,
                        request.payload(),
                        executionUserId == null || executionUserId.isBlank() ? trigger.getUserId() : executionUserId,
                        textConsumer);
                completion.accept(result);
            } catch (Exception exception) {
                // Persistence infrastructure failures can still escape the runner.
                logger.error("Async trigger {} failed: {}", request.triggerId(), exception.getMessage(), exception);
                try {
                    completion.accept(DispatchResult.failed(exception.getMessage()));
                } catch (RuntimeException callbackFailure) {
                    logger.debug("Async trigger failure callback failed: {}", callbackFailure.getMessage());
                }
            }
        });
        return invocation.getId();
    }

    /**
     * Dispatches a row already claimed by the database scheduler (including disabled one-shot rows).
     */
    public String fireClaimedAsynchronously(TriggerEntity trigger) {
        Map<String, Object> config = config(trigger);
        Object configuredData = config.get("data") == null ? config.get("payload") : config.get("data");
        Map<String, Object> payload = configuredData instanceof Map<?, ?> configured
                ? configured.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
                : Map.of();
        String conversationId =
                config.get("conversationId") == null ? null : String.valueOf(config.get("conversationId"));
        TriggerInvocationRequest request = TriggerInvocationRequest.scheduled(trigger.getId(), payload, conversationId);
        TriggerInvocationEntity invocation =
                invocationRunner.open(InvocationDraft.initial(request), trigger.getTenantId());
        executor.execute(() -> invocationRunner.execute(trigger, invocation, payload));
        return invocation.getId();
    }

    /**
     * @deprecated Use {@link #fireSynchronously(TriggerInvocationRequest)}.
     */
    @Deprecated(forRemoval = false)
    public DispatchResult fireSync(String triggerId, Map<String, Object> payload, String source) {
        return fireSynchronously(new TriggerInvocationRequest(triggerId, payload, source, null));
    }

    /**
     * @deprecated Use {@link #fireAsynchronously(TriggerInvocationRequest)}.
     */
    @Deprecated(forRemoval = false)
    public String fire(String triggerId, Map<String, Object> payload, String source) {
        return fireAsynchronously(new TriggerInvocationRequest(triggerId, payload, source, null));
    }

    /** Re-run a past invocation with its original payload (new invocation, linked via replayOf). */
    public TriggerInvocationEntity replay(String invocationId) {
        TriggerInvocationEntity original = invocationRunner.find(invocationId);
        if (original == null) {
            throw new PlatformException("invocation_not_found", "Invocation not found: " + invocationId, null);
        }
        TriggerEntity trigger = require(original.getTriggerId());
        Map<String, Object> payload = JsonUtils.parseMap(original.getPayloadJson());
        TriggerInvocationEntity replay =
                invocationRunner.open(InvocationDraft.replay(original, payload), trigger.getTenantId());
        invocationRunner.execute(trigger, replay, payload);
        return invocationRunner.find(trigger.getTenantId(), replay.getId());
    }

    public TriggerInvocationEntity replay(String tenantId, String invocationId) {
        TriggerInvocationEntity original = invocation(tenantId, invocationId);
        TriggerEntity trigger = require(tenantId, original.getTriggerId());
        Map<String, Object> payload = JsonUtils.parseMap(original.getPayloadJson());
        TriggerInvocationEntity replay =
                invocationRunner.open(InvocationDraft.replay(original, payload), trigger.getTenantId());
        invocationRunner.execute(trigger, replay, payload);
        return invocationRunner.find(tenantId == null ? "default" : tenantId, replay.getId());
    }

    public TriggerInvocationEntity invocation(String id) {
        return invocationRunner.find(id);
    }

    public TriggerInvocationEntity invocation(String tenantId, String id) {
        TriggerInvocationEntity invocation = invocationRunner.find(tenantId == null ? "default" : tenantId, id);
        if (invocation == null) {
            throw new PlatformException("invocation_not_found", "Invocation not found", null);
        }
        return invocation;
    }

    public List<TriggerInvocationEntity> invocations(String triggerId) {
        return invocationRunner.listForTrigger(triggerId);
    }

    public List<TriggerInvocationEntity> invocations(String tenantId, String triggerId) {
        require(tenantId, triggerId);
        return invocationRunner.listForTrigger(tenantId == null ? "default" : tenantId, triggerId);
    }

    private TriggerEntity requireEnabled(String triggerId) {
        TriggerEntity trigger = require(triggerId);
        if (!Boolean.TRUE.equals(trigger.getEnabled())) {
            throw new PlatformException("trigger_disabled", "Trigger is disabled: " + triggerId, null);
        }
        return trigger;
    }

    private TriggerEntity requireEnabled(String tenantId, String triggerId) {
        TriggerEntity trigger = require(tenantId, triggerId);
        if (!Boolean.TRUE.equals(trigger.getEnabled())) {
            throw new PlatformException("trigger_disabled", "Trigger is disabled", null);
        }
        return trigger;
    }

    private void notifySaved(TriggerEntity trigger) {
        if (Boolean.TRUE.equals(trigger.getEnabled())) {
            changeListeners.forEach(listener -> listener.onSaved(trigger));
        }
    }

    private TriggerEntity newTrigger(CreateTriggerRequest request) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setTenantId(
                request.getTenantId() == null || request.getTenantId().isBlank()
                        ? UserContextHolder.currentTenantId()
                        : request.getTenantId());
        trigger.setUserId(request.getUserId() == null ? UserContextHolder.currentUserId() : request.getUserId());
        trigger.setName(request.getName());
        trigger.setType(request.getType());
        trigger.setEnabled(request.isEnabled());
        trigger.setTargetType(request.getTargetType());
        trigger.setTargetId(request.getTargetId());
        trigger.setConfigJson(JsonUtils.toJson(request.getConfig()));
        trigger.setFireCount(0L);
        if (trigger.getType() == TriggerType.CRON && trigger.getEnabled()) {
            trigger.setNextFireAt(TriggerSchedule.from(request.getConfig()).firstFireAt(LocalDateTime.now()));
        }
        return trigger;
    }

    private static void copy(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) if (source.get(key) != null) target.put(key, source.get(key));
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private void updateOwned(TriggerEntity trigger) {
        triggerMapper.update(
                trigger,
                new LambdaUpdateWrapper<TriggerEntity>()
                        .eq(TriggerEntity::getTenantId, trigger.getTenantId())
                        .eq(TriggerEntity::getId, trigger.getId()));
    }

    private int deleteOwned(TriggerEntity trigger) {
        return triggerMapper.delete(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getTenantId, trigger.getTenantId())
                .eq(TriggerEntity::getId, trigger.getId()));
    }
}
