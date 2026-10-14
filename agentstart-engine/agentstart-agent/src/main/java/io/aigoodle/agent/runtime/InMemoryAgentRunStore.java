package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight store for tests and applications that deliberately opt out of JDBC. */
public class InMemoryAgentRunStore implements AgentRunStore {
    private record RunKey(String tenantId, String runId) {
        private RunKey {
            tenantId = normalizeTenant(tenantId);
            if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is required");
        }
    }

    private final ConcurrentHashMap<RunKey, AgentRunSnapshot> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RunKey, List<AgentRunEvent>> eventStreams = new ConcurrentHashMap<>();

    @Override
    public AgentRunSnapshot create(
            String runId, AgentDefinition definition, AgentRequest request, String conversationId) {
        LocalDateTime now = LocalDateTime.now();
        AgentRunSnapshot created = new AgentRunSnapshot(
                runId,
                definition.getTenantId() == null || definition.getTenantId().isBlank()
                        ? "default"
                        : definition.getTenantId(),
                definition.getId(),
                conversationId,
                AgentRunStatus.CREATED,
                JsonUtils.toJson(definition),
                JsonUtils.toJson(request),
                null,
                null,
                0,
                null,
                null,
                now,
                now);
        if (runs.putIfAbsent(new RunKey(created.tenantId(), runId), created) != null) {
            throw new PlatformException("agent_run_exists", "Agent run already exists: " + runId, null);
        }
        append(created, "RUN_CREATED", created.requestJson());
        return created;
    }

    @Override
    public AgentRunSnapshot transition(String runId, AgentRunStatus target, AgentResponse response, String error) {
        return transitionOwned(null, runId, target, response, error);
    }

    @Override
    public AgentRunSnapshot transition(
            String tenantId, String runId, AgentRunStatus target, AgentResponse response, String error) {
        return transitionOwned(normalizeTenant(tenantId), runId, target, response, error);
    }

    private AgentRunSnapshot transitionOwned(
            String tenantId, String runId, AgentRunStatus target, AgentResponse response, String error) {
        RunKey key = resolveKey(tenantId, runId);
        AgentRunSnapshot updated = runs.compute(key, (id, current) -> {
            if (current == null) {
                throw new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null);
            }
            if (!current.status().canTransitionTo(target)) {
                throw new PlatformException(
                        "invalid_run_transition",
                        "Agent run " + runId + " cannot transition from " + current.status() + " to " + target,
                        null);
            }
            LocalDateTime now = LocalDateTime.now();
            return new AgentRunSnapshot(
                    current.runId(),
                    current.tenantId(),
                    current.agentId(),
                    current.conversationId(),
                    target,
                    current.definitionJson(),
                    current.requestJson(),
                    response == null ? current.responseJson() : JsonUtils.toJson(response),
                    error == null ? current.error() : error,
                    current.version() + 1,
                    target == AgentRunStatus.RUNNING && current.startedAt() == null ? now : current.startedAt(),
                    target.isTerminal() ? now : current.finishedAt(),
                    current.createdAt(),
                    now);
        });
        append(updated, "RUN_" + target.name(), response == null ? error : JsonUtils.toJson(response));
        return updated;
    }

    @Override
    public Optional<AgentRunSnapshot> find(String runId) {
        return uniqueRun(runId).map(entry -> entry.getValue());
    }

    @Override
    public Optional<AgentRunSnapshot> find(String tenantId, String runId) {
        return Optional.ofNullable(runs.get(new RunKey(tenantId, runId)));
    }

    @Override
    public List<AgentRunEvent> events(String runId, long afterSequence, int limit) {
        RunKey key = resolveKey(null, runId);
        return events(key, afterSequence, limit);
    }

    private List<AgentRunEvent> events(RunKey key, long afterSequence, int limit) {
        int pageSize = Math.max(1, limit);
        synchronized (eventStreams.computeIfAbsent(key, ignored -> new ArrayList<>())) {
            return eventStreams.get(key).stream()
                    .filter(event -> event.sequence() > afterSequence)
                    .limit(pageSize)
                    .toList();
        }
    }

    @Override
    public List<AgentRunEvent> events(String tenantId, String runId, long afterSequence, int limit) {
        if (find(tenantId, runId).isEmpty()) return List.of();
        return events(new RunKey(tenantId, runId), afterSequence, limit);
    }

    @Override
    public void appendEvent(String runId, String type, String payloadJson) {
        AgentRunSnapshot run = find(runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
        append(run, type, payloadJson);
    }

    @Override
    public void appendEvent(String tenantId, String runId, String type, String payloadJson) {
        AgentRunSnapshot run = find(tenantId, runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
        append(run, type, payloadJson);
    }

    private void append(AgentRunSnapshot run, String type, String payload) {
        RunKey key = new RunKey(run.tenantId(), run.runId());
        List<AgentRunEvent> events = eventStreams.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (events) {
            events.add(new AgentRunEvent(
                    UUID.randomUUID().toString(), run.runId(), events.size() + 1L, type, payload, LocalDateTime.now()));
        }
    }

    private RunKey resolveKey(String tenantId, String runId) {
        if (tenantId != null) return new RunKey(tenantId, runId);
        return uniqueRun(runId).map(java.util.Map.Entry::getKey).orElse(new RunKey("default", runId));
    }

    private Optional<java.util.Map.Entry<RunKey, AgentRunSnapshot>> uniqueRun(String runId) {
        List<java.util.Map.Entry<RunKey, AgentRunSnapshot>> matches = runs.entrySet().stream()
                .filter(entry -> entry.getKey().runId().equals(runId))
                .limit(2)
                .toList();
        if (matches.size() > 1) {
            throw new PlatformException(
                    "agent_run_tenant_required",
                    "Tenant id is required because run id is not globally unique: " + runId,
                    null);
        }
        return matches.stream().findFirst();
    }

    private static String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }
}
