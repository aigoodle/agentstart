package io.aigoodle.agent.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AgentRunEntity;
import io.aigoodle.agent.entity.AgentRunEventEntity;
import io.aigoodle.agent.mapper.AgentRunEventMapper;
import io.aigoodle.agent.mapper.AgentRunMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis-backed run store with optimistic status/version transitions. */
public class JdbcAgentRunStore implements AgentRunStore {
    private static final String DEFAULT_TENANT = "default";
    private static final int MAX_EVENT_PAGE = 1000;

    private final AgentRunMapper runMapper;
    private final AgentRunEventMapper eventMapper;

    public JdbcAgentRunStore(AgentRunMapper runMapper, AgentRunEventMapper eventMapper) {
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    @Transactional
    public AgentRunSnapshot create(
            String runId, AgentDefinition definition, AgentRequest request, String conversationId) {
        LocalDateTime now = LocalDateTime.now();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setTenantId(blankToDefault(definition.getTenantId()));
        run.setAgentId(definition.getId());
        run.setConversationId(conversationId);
        run.setStatus(AgentRunStatus.CREATED.name());
        run.setDefinitionJson(JsonUtils.toJson(definition));
        run.setRequestJson(JsonUtils.toJson(request));
        run.setVersion(0L);
        run.setEventSequence(0L);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        append(run, "RUN_CREATED", run.getRequestJson());
        return snapshot(run);
    }

    @Override
    @Transactional
    public AgentRunSnapshot transition(String runId, AgentRunStatus target, AgentResponse response, String error) {
        return transition(UserContextHolder.currentTenantId(), runId, target, response, error);
    }

    @Override
    @Transactional
    public AgentRunSnapshot transition(
            String tenantId, String runId, AgentRunStatus target, AgentResponse response, String error) {
        String tenant = blankToDefault(tenantId);
        AgentRunEntity current = require(tenant, runId);
        AgentRunStatus source = AgentRunStatus.valueOf(current.getStatus());
        if (!source.canTransitionTo(target)) {
            throw new PlatformException(
                    "invalid_run_transition",
                    "Agent run " + runId + " cannot transition from " + source + " to " + target,
                    null);
        }
        long version = current.getVersion() == null ? 0L : current.getVersion();
        LocalDateTime now = LocalDateTime.now();
        String responseJson = response == null ? null : JsonUtils.toJson(response);
        LambdaUpdateWrapper<AgentRunEntity> update = new LambdaUpdateWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getTenantId, tenant)
                .eq(AgentRunEntity::getId, runId)
                .eq(AgentRunEntity::getStatus, source.name())
                .eq(AgentRunEntity::getVersion, version)
                .set(AgentRunEntity::getStatus, target.name())
                .set(AgentRunEntity::getVersion, version + 1)
                .set(AgentRunEntity::getUpdatedAt, now);
        if (target == AgentRunStatus.RUNNING && current.getStartedAt() == null) {
            update.set(AgentRunEntity::getStartedAt, now);
        }
        if (target.isTerminal()) {
            update.set(AgentRunEntity::getFinishedAt, now);
        }
        if (responseJson != null) {
            update.set(AgentRunEntity::getResponseJson, responseJson);
        }
        if (error != null) {
            update.set(AgentRunEntity::getError, error);
        }
        if (runMapper.update(null, update) != 1) {
            throw new PlatformException(
                    "run_concurrent_update", "Agent run " + runId + " was modified concurrently", null);
        }
        AgentRunEntity updated = require(tenant, runId);
        append(updated, "RUN_" + target.name(), responseJson != null ? responseJson : error);
        return snapshot(updated);
    }

    @Override
    public Optional<AgentRunSnapshot> find(String runId) {
        return find(UserContextHolder.currentTenantId(), runId);
    }

    @Override
    public Optional<AgentRunSnapshot> find(String tenantId, String runId) {
        return Optional.ofNullable(runMapper.selectOne(new LambdaQueryWrapper<AgentRunEntity>()
                        .eq(AgentRunEntity::getTenantId, blankToDefault(tenantId))
                        .eq(AgentRunEntity::getId, runId)
                        .last("LIMIT 1")))
                .map(JdbcAgentRunStore::snapshot);
    }

    @Override
    public List<AgentRunEvent> events(String runId, long afterSequence, int limit) {
        return events(UserContextHolder.currentTenantId(), runId, afterSequence, limit);
    }

    @Override
    public List<AgentRunEvent> events(String tenantId, String runId, long afterSequence, int limit) {
        int pageSize = Math.min(MAX_EVENT_PAGE, Math.max(1, limit));
        return eventMapper
                .selectList(new LambdaQueryWrapper<AgentRunEventEntity>()
                        .eq(AgentRunEventEntity::getTenantId, blankToDefault(tenantId))
                        .eq(AgentRunEventEntity::getRunId, runId)
                        .gt(AgentRunEventEntity::getSequenceNo, Math.max(0, afterSequence))
                        .orderByAsc(AgentRunEventEntity::getSequenceNo)
                        .last("LIMIT " + pageSize))
                .stream()
                .map(JdbcAgentRunStore::event)
                .toList();
    }

    @Override
    @Transactional
    public void appendEvent(String runId, String type, String payloadJson) {
        appendEvent(UserContextHolder.currentTenantId(), runId, type, payloadJson);
    }

    @Override
    @Transactional
    public void appendEvent(String tenantId, String runId, String type, String payloadJson) {
        append(require(blankToDefault(tenantId), runId), type, payloadJson);
    }

    private AgentRunEntity require(String tenantId, String runId) {
        AgentRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getTenantId, tenantId)
                .eq(AgentRunEntity::getId, runId)
                .last("LIMIT 1"));
        if (run == null) {
            throw new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null);
        }
        return run;
    }

    private void append(AgentRunEntity run, String type, String payload) {
        long sequence = reserveEventSequence(run.getTenantId(), run.getId());
        AgentRunEventEntity event = new AgentRunEventEntity();
        event.setTenantId(run.getTenantId());
        event.setRunId(run.getId());
        event.setSequenceNo(sequence);
        event.setEventType(type);
        event.setPayloadJson(payload);
        eventMapper.insert(event);
    }

    private long reserveEventSequence(String tenantId, String runId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AgentRunEntity current = require(tenantId, runId);
            long sequence = current.getEventSequence() == null ? 0L : current.getEventSequence();
            int updated = runMapper.update(
                    null,
                    new LambdaUpdateWrapper<AgentRunEntity>()
                            .eq(AgentRunEntity::getTenantId, tenantId)
                            .eq(AgentRunEntity::getId, runId)
                            .eq(AgentRunEntity::getEventSequence, sequence)
                            .set(AgentRunEntity::getEventSequence, sequence + 1));
            if (updated == 1) return sequence + 1;
        }
        throw new PlatformException(
                "run_event_concurrent_update", "Could not reserve event sequence for agent run " + runId, null);
    }

    private static AgentRunSnapshot snapshot(AgentRunEntity run) {
        return new AgentRunSnapshot(
                run.getId(),
                run.getTenantId(),
                run.getAgentId(),
                run.getConversationId(),
                AgentRunStatus.valueOf(run.getStatus()),
                run.getDefinitionJson(),
                run.getRequestJson(),
                run.getResponseJson(),
                run.getError(),
                run.getVersion() == null ? 0 : run.getVersion(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private static AgentRunEvent event(AgentRunEventEntity event) {
        return new AgentRunEvent(
                event.getId(),
                event.getRunId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getPayloadJson(),
                event.getCreatedAt());
    }

    private static String blankToDefault(String value) {
        return value == null || value.isBlank() ? DEFAULT_TENANT : value;
    }
}
