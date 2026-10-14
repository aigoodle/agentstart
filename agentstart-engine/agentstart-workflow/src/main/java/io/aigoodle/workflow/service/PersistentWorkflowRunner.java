package io.aigoodle.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.engine.NodeExecutionStatus;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.engine.WorkflowExecutionEventType;
import io.aigoodle.workflow.engine.WorkflowExecutionObserver;
import io.aigoodle.workflow.engine.WorkflowResumeState;
import io.aigoodle.workflow.engine.WorkflowRunOptions;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.entity.WorkflowCheckpointEntity;
import io.aigoodle.workflow.entity.WorkflowRunNodeEntity;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionPolicy;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.StepRecord;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Starts and resumes executions whose state is committed after every terminal node. */
public class PersistentWorkflowRunner {

    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);
    private static final ScheduledExecutorService LEASE_HEARTBEATS =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "workflow-lease-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
    private final WorkflowEngine engine;
    private final WorkflowCheckpointStore store;
    private final WorkflowGraphCodec graphCodec = new WorkflowGraphCodec();
    private final String instanceId;
    private final Duration leaseDuration;
    private final HumanInteractionStore humanInteractions;
    private final WorkflowRuntimeMetrics metrics;
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public PersistentWorkflowRunner(WorkflowEngine engine, WorkflowCheckpointStore store) {
        this(engine, store, "workflow-" + UUID.randomUUID(), DEFAULT_LEASE, null, WorkflowRuntimeMetrics.NOOP);
    }

    public PersistentWorkflowRunner(
            WorkflowEngine engine, WorkflowCheckpointStore store, HumanInteractionStore humanInteractions) {
        this(
                engine,
                store,
                "workflow-" + UUID.randomUUID(),
                DEFAULT_LEASE,
                humanInteractions,
                WorkflowRuntimeMetrics.NOOP);
    }

    PersistentWorkflowRunner(WorkflowEngine engine, WorkflowCheckpointStore store, String instanceId) {
        this(engine, store, instanceId, DEFAULT_LEASE, null, WorkflowRuntimeMetrics.NOOP);
    }

    public PersistentWorkflowRunner(
            WorkflowEngine engine, WorkflowCheckpointStore store, String instanceId, Duration leaseDuration) {
        this(engine, store, instanceId, leaseDuration, null, WorkflowRuntimeMetrics.NOOP);
    }

    public PersistentWorkflowRunner(
            WorkflowEngine engine,
            WorkflowCheckpointStore store,
            String instanceId,
            Duration leaseDuration,
            HumanInteractionStore humanInteractions) {
        this(engine, store, instanceId, leaseDuration, humanInteractions, WorkflowRuntimeMetrics.NOOP);
    }

    public PersistentWorkflowRunner(
            WorkflowEngine engine,
            WorkflowCheckpointStore store,
            String instanceId,
            Duration leaseDuration,
            HumanInteractionStore humanInteractions,
            WorkflowRuntimeMetrics metrics) {
        this.engine = engine;
        this.store = store;
        this.instanceId = instanceId;
        this.leaseDuration = leaseDuration;
        this.humanInteractions = humanInteractions;
        this.metrics = metrics == null ? WorkflowRuntimeMetrics.NOOP : metrics;
    }

    public WorkflowRunResult start(
            String tenantId,
            String workflowId,
            String graphVersion,
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            WorkflowRunOptions options) {
        return start(tenantId, workflowId, graphVersion, graph, inputs, conversationId, options, null, null);
    }

    public WorkflowRunResult start(
            String tenantId,
            String workflowId,
            String graphVersion,
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            WorkflowRunOptions options,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink) {
        String runId = options.runId() == null ? UUID.randomUUID().toString() : options.runId();
        WorkflowCheckpointEntity checkpoint =
                initialCheckpoint(tenantId, workflowId, graphVersion, graph, inputs, conversationId, runId);
        // This metadata is written from trusted Java options, never from request inputs.
        Map<String, Object> durableGraph = JsonUtils.parseMap(checkpoint.getGraphJson());
        durableGraph.put("_resourceTenantId", options.resourceTenantId());
        checkpoint.setGraphJson(JsonUtils.toJson(durableGraph));
        ExecutionContext policyContext = ExecutionContext.start(inputs, conversationId, null, runId);
        List<WorkflowRunNodeEntity> nodes = graph.getNodes().stream()
                .map(node -> initialNode(runId, node, engine.executionPolicy(node, policyContext)))
                .toList();
        store.create(checkpoint, nodes);
        WorkflowRunOptions identified = options.withRunId(runId);
        activeRuns.put(runId, new ActiveRun(checkpoint.getTenantId(), identified.cancellationToken()));
        try {
            return advance(checkpoint, graph, inputs, WorkflowResumeState.empty(), identified, stepListener, chatSink);
        } finally {
            activeRuns.remove(runId);
        }
    }

    public WorkflowRunResult resume(String tenantId, String runId, WorkflowRunOptions options) {
        WorkflowCheckpointEntity checkpoint = store.require(tenantId, runId);
        WorkflowRunStatus durableStatus = WorkflowRunStatus.valueOf(checkpoint.getStatus());
        if (durableStatus == WorkflowRunStatus.WAITING) {
            throw new PlatformException(
                    "resume_signal_required", "Waiting run must be resumed with its token or correlated event", null);
        }
        if (durableStatus == WorkflowRunStatus.SUCCEEDED
                || durableStatus == WorkflowRunStatus.CANCELLED
                || durableStatus == WorkflowRunStatus.TIMED_OUT
                || durableStatus == WorkflowRunStatus.CANCELLING
                || durableStatus == WorkflowRunStatus.PAUSING) {
            throw new PlatformException(
                    "run_not_resumable", "Run " + runId + " cannot resume from " + durableStatus, null);
        }
        WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
        List<WorkflowRunNodeEntity> nodes = store.nodes(tenantId, runId);
        List<String> unsafe = nodes.stream()
                .filter(node -> (NodeExecutionStatus.RUNNING.name().equals(node.getStatus())
                                || NodeExecutionStatus.FAILED.name().equals(node.getStatus()))
                        && Boolean.FALSE.equals(node.getResumable()))
                .map(WorkflowRunNodeEntity::getNodeId)
                .toList();
        if (!unsafe.isEmpty()) {
            throw new PlatformException(
                    "unsafe_resume", "Non-resumable side-effect nodes require reconciliation: " + unsafe, null);
        }
        WorkflowResumeState resume = resumeState(checkpoint, nodes);
        Map<String, Object> inputs = JsonUtils.parseMap(checkpoint.getInputsJson());
        WorkflowRunOptions identified = options.withRunId(runId);
        activeRuns.put(runId, new ActiveRun(tenantId, identified.cancellationToken()));
        try {
            return advance(checkpoint, graph, inputs, resume, identified, null, null);
        } finally {
            activeRuns.remove(runId);
        }
    }

    public boolean cancel(String tenantId, String runId, String reason) {
        String cancellationReason = reason == null ? "Cancelled by user" : reason;
        boolean persisted = store.requestCancellation(tenantId, runId, cancellationReason);
        ActiveRun active = activeRuns.get(runId);
        if (active != null && active.tenantId().equals(tenantId)) active.token().cancel(cancellationReason);
        if (persisted) {
            var cancelled = store.require(tenantId, runId);
            if (WorkflowRunStatus.CANCELLED.name().equals(cancelled.getStatus())) cleanupWait(cancelled);
        }
        return persisted;
    }

    public boolean cancelLocal(String tenantId, String runId, String reason) {
        ActiveRun active = activeRuns.get(runId);
        return active != null
                && active.tenantId().equals(tenantId)
                && active.token().cancel(reason);
    }

    public boolean pause(String tenantId, String runId, String reason) {
        String pauseReason = reason == null ? "Paused by user" : reason;
        boolean persisted = store.requestPause(tenantId, runId, pauseReason);
        ActiveRun active = activeRuns.get(runId);
        if (active != null && active.tenantId().equals(tenantId)) active.token().pause(pauseReason);
        return persisted;
    }

    public boolean pauseLocal(String tenantId, String runId, String reason) {
        ActiveRun active = activeRuns.get(runId);
        return active != null
                && active.tenantId().equals(tenantId)
                && active.token().pause(reason);
    }

    public WorkflowSignalResult signal(
            String tenantId,
            String runId,
            String resumeToken,
            String eventId,
            Map<String, Object> payload,
            WorkflowRunOptions options) {
        WorkflowCheckpointEntity checkpoint = store.require(tenantId, runId);
        if (!WorkflowRunStatus.WAITING.name().equals(checkpoint.getStatus())) {
            return new WorkflowSignalResult(false, true, null);
        }
        CurrentUser user = UserContextHolder.get();
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new PlatformException("resume_forbidden", "Authenticated tenant user is required", null);
        }
        verifyResumeToken(checkpoint, resumeToken);
        if (checkpoint.getWaitExpiresAt() != null
                && checkpoint.getWaitExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PlatformException("resume_expired", "Resume token has expired", null);
        }
        WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
        NodeDef waitingNode = graph.node(checkpoint.getResumeNodeId());
        authorize(
                waitingNode,
                user,
                checkpoint.getInterruptReason() != null
                        && checkpoint.getInterruptReason().startsWith("Escalated approval"));
        validatePayload(checkpoint.getInputSchemaJson(), payload);
        String stableEventId = hasText(eventId) ? eventId : UUID.randomUUID().toString();
        if (!prepareResume(checkpoint, waitingNode, payload, stableEventId, user.getUserId())) {
            return new WorkflowSignalResult(false, true, null);
        }
        WorkflowRunResult result = resume(tenantId, runId, options);
        return new WorkflowSignalResult(true, false, result);
    }

    /** Trusted resume used after a bearer interaction token has been verified by HumanInteractionService. */
    public WorkflowSignalResult signalTrusted(
            String tenantId,
            String runId,
            String eventId,
            Map<String, Object> payload,
            String actor,
            WorkflowRunOptions options) {
        WorkflowCheckpointEntity checkpoint = store.require(tenantId, runId);
        if (!WorkflowRunStatus.WAITING.name().equals(checkpoint.getStatus())) {
            return new WorkflowSignalResult(false, true, null);
        }
        if (checkpoint.getWaitExpiresAt() != null
                && checkpoint.getWaitExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PlatformException("resume_expired", "Human interaction has expired", null);
        }
        WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
        NodeDef waitingNode = graph.node(checkpoint.getResumeNodeId());
        validatePayload(checkpoint.getInputSchemaJson(), payload);
        String stableEventId = hasText(eventId) ? eventId : UUID.randomUUID().toString();
        if (!prepareResume(checkpoint, waitingNode, payload, stableEventId, actor)) {
            return new WorkflowSignalResult(false, true, null);
        }
        return new WorkflowSignalResult(true, false, resume(tenantId, runId, options));
    }

    public WorkflowSignalResult signalByCorrelation(
            String tenantId,
            String correlationKey,
            String resumeToken,
            String eventId,
            Map<String, Object> payload,
            WorkflowRunOptions options) {
        List<WorkflowCheckpointEntity> matches = store.waitingByCorrelation(tenantId, correlationKey);
        if (matches.isEmpty()) return new WorkflowSignalResult(false, false, null);
        if (matches.size() > 1)
            throw new PlatformException(
                    "ambiguous_correlation_key", "Correlation key matches more than one waiting run", null);
        return signal(tenantId, matches.getFirst().getRunId(), resumeToken, eventId, payload, options);
    }

    /** Called by the recovery scanner; lease/CAS make this safe across instances. */
    public WorkflowRunResult wakeSleep(WorkflowCheckpointEntity checkpoint) {
        if (!WorkflowRunStatus.WAITING.name().equals(checkpoint.getStatus())
                || !io.aigoodle.workflow.graph.NodeType.SLEEP_UNTIL.name().equals(checkpoint.getWaitType())) {
            return null;
        }
        WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
        NodeDef node = graph.node(checkpoint.getResumeNodeId());
        String eventId = "wake:" + checkpoint.getRunId() + ":" + checkpoint.getCheckpointVersion();
        if (!prepareResume(
                checkpoint, node, Map.of("wokeAt", java.time.Instant.now().toString()), eventId, "system:scheduler"))
            return null;
        return resume(checkpoint.getTenantId(), checkpoint.getRunId(), WorkflowRunOptions.defaults());
    }

    public boolean timeoutWait(WorkflowCheckpointEntity checkpoint) {
        if (!WorkflowRunStatus.WAITING.name().equals(checkpoint.getStatus())) return false;
        WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
        NodeDef waitingNode = graph.node(checkpoint.getResumeNodeId());
        if (waitingNode.getType() == io.aigoodle.workflow.graph.NodeType.APPROVAL
                && "ESCALATE".equalsIgnoreCase(waitingNode.getString("timeoutStrategy", "TIMEOUT"))) {
            checkpoint.setCorrelationKey(waitingNode.getString("escalationCorrelationKey"));
            checkpoint.setWaitExpiresAt(null);
            checkpoint.setInterruptReason("Escalated approval at node " + waitingNode.getId());
            try {
                store.transition(checkpoint, checkpoint.getCheckpointVersion(), WorkflowExecutionEventType.RUN_WAITING);
                return true;
            } catch (PlatformException conflict) {
                if ("checkpoint_conflict".equals(conflict.getCode())) return false;
                throw conflict;
            }
        }
        checkpoint.setStatus(WorkflowRunStatus.TIMED_OUT.name());
        checkpoint.setInterruptReason("Wait expired at node " + checkpoint.getResumeNodeId());
        try {
            store.transition(checkpoint, checkpoint.getCheckpointVersion(), WorkflowExecutionEventType.RUN_TIMED_OUT);
            cleanupWait(checkpoint);
            return true;
        } catch (PlatformException conflict) {
            if ("checkpoint_conflict".equals(conflict.getCode())) return false;
            throw conflict;
        }
    }

    private void cleanupWait(WorkflowCheckpointEntity checkpoint) {
        if (checkpoint.getResumeNodeId() == null) return;
        try {
            WorkflowGraph graph = JsonUtils.parse(checkpoint.getGraphJson(), WorkflowGraph.class);
            Map<String, Map<String, Object>> pool =
                    JsonUtils.parse(checkpoint.getVariablePoolJson(), new TypeReference<>() {});
            for (var entry : pool.entrySet()) {
                if (entry.getValue().get("_pluginTask") == null) continue;
                try {
                    engine.cancelWaiting(graph.node(entry.getKey()), entry.getValue());
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(PersistentWorkflowRunner.class)
                            .warn(
                                    "External task cancellation pending for workflow {} node {}",
                                    checkpoint.getRunId(),
                                    entry.getKey());
                }
            }
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(PersistentWorkflowRunner.class)
                    .warn(
                            "External wait cleanup failed for workflow {} ({}); external task may still be running",
                            checkpoint.getRunId(),
                            failure.getClass().getSimpleName());
        }
    }

    private boolean prepareResume(
            WorkflowCheckpointEntity checkpoint,
            NodeDef waitingNode,
            Map<String, Object> payload,
            String eventId,
            String resumedBy) {
        Map<String, Map<String, Object>> pool = JsonUtils.parse(
                checkpoint.getVariablePoolJson(), new TypeReference<Map<String, Map<String, Object>>>() {});
        Map<String, Map<String, Object>> mutablePool = new LinkedHashMap<>(pool);
        Map<String, Object> nodeValues = new LinkedHashMap<>(mutablePool.getOrDefault(waitingNode.getId(), Map.of()));
        nodeValues.put("_resume", payload == null ? Map.of() : payload);
        mutablePool.put(waitingNode.getId(), nodeValues);
        checkpoint.setVariablePoolJson(JsonUtils.toJson(mutablePool));
        checkpoint.setStatus(WorkflowRunStatus.RUNNING.name());
        checkpoint.setResumedBy(resumedBy);
        checkpoint.setResumedAt(LocalDateTime.now());
        checkpoint.setInterruptReason(null);
        String payloadHash = sha256(JsonUtils.toJson(payload == null ? Map.of() : payload));
        return store.acceptSignal(checkpoint, checkpoint.getCheckpointVersion(), eventId, payloadHash, resumedBy);
    }

    private record ActiveRun(String tenantId, io.aigoodle.workflow.engine.RunCancellationToken token) {}

    private static void verifyResumeToken(WorkflowCheckpointEntity checkpoint, String token) {
        if (!hasText(token)
                || !java.security.MessageDigest.isEqual(
                        sha256(token).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        checkpoint.getResumeTokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new PlatformException("resume_token_invalid", "Invalid resume token", null);
        }
    }

    private static void authorize(NodeDef node, CurrentUser user, boolean escalated) {
        Object users = node.get("allowedUserIds");
        if (users instanceof List<?> list && !list.isEmpty() && !list.contains(user.getUserId())) {
            throw new PlatformException("resume_forbidden", "User is not allowed to resume this node", null);
        }
        Object roles = escalated && node.get("escalationAllowedRoles") != null
                ? node.get("escalationAllowedRoles")
                : node.get("allowedRoles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            Set<String> actual = user.getRoles() == null ? Set.of() : user.getRoles();
            boolean matched = list.stream().map(String::valueOf).anyMatch(actual::contains);
            if (!matched) throw new PlatformException("resume_forbidden", "Required approval role is missing", null);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validatePayload(String schemaJson, Map<String, Object> payload) {
        Map<String, Object> schema = JsonUtils.parseMap(schemaJson);
        Map<String, Object> actual = payload == null ? Map.of() : payload;
        Object required = schema.get("required");
        if (required instanceof List<?> fields) {
            for (Object field : fields)
                if (!actual.containsKey(String.valueOf(field))) {
                    throw new PlatformException("resume_payload_invalid", "Missing required field: " + field, null);
                }
        }
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> definitions) {
            definitions.forEach((key, definition) -> {
                Object value = actual.get(String.valueOf(key));
                if (value == null || !(definition instanceof Map<?, ?> rule)) return;
                String type = String.valueOf(rule.get("type"));
                boolean valid =
                        switch (type) {
                            case "string" -> value instanceof String;
                            case "boolean" -> value instanceof Boolean;
                            case "number", "integer" -> value instanceof Number;
                            case "object" -> value instanceof Map<?, ?>;
                            case "array" -> value instanceof List<?>;
                            default -> true;
                        };
                if (!valid)
                    throw new PlatformException("resume_payload_invalid", "Field " + key + " must be " + type, null);
            });
        }
    }

    private WorkflowRunResult advance(
            WorkflowCheckpointEntity checkpoint,
            WorkflowGraph graph,
            Map<String, Object> inputs,
            WorkflowResumeState resume,
            WorkflowRunOptions options,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink) {
        if (!store.acquireLease(checkpoint.getTenantId(), checkpoint.getRunId(), instanceId, leaseDuration)) {
            throw new PlatformException(
                    "run_lease_conflict", "Run " + checkpoint.getRunId() + " is owned by another executor", null);
        }
        CheckpointObserver observer = new CheckpointObserver(checkpoint);
        long runStartedNanos = System.nanoTime();
        metrics.runStarted();
        ScheduledFuture<?> heartbeat = LEASE_HEARTBEATS.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!store.acquireLease(
                                checkpoint.getTenantId(), checkpoint.getRunId(), instanceId, leaseDuration)) {
                            metrics.leaseLost();
                            options.cancellationToken().cancel("Workflow execution lease was lost");
                        }
                    } catch (RuntimeException exception) {
                        options.cancellationToken().cancel("Workflow lease renewal failed: " + exception.getMessage());
                    }
                },
                heartbeatMillis(),
                heartbeatMillis(),
                TimeUnit.MILLISECONDS);
        try {
            if (!WorkflowRunStatus.RUNNING.name().equals(checkpoint.getStatus())) {
                checkpoint.setStatus(WorkflowRunStatus.RUNNING.name());
                store.transition(checkpoint, checkpoint.getCheckpointVersion(), WorkflowExecutionEventType.RUN_RESUMED);
            }
            WorkflowRunResult result = engine.run(
                    graph,
                    inputs,
                    checkpoint.getConversationId(),
                    stepListener,
                    chatSink,
                    checkpoint.getTenantId(),
                    options.withResourceTenantId((String)
                            JsonUtils.parseMap(checkpoint.getGraphJson()).get("_resourceTenantId")),
                    resume,
                    observer);
            observer.finish(result);
            metrics.runFinished(result.getStatus(), System.nanoTime() - runStartedNanos);
            return result;
        } finally {
            heartbeat.cancel(false);
            store.releaseLease(checkpoint.getTenantId(), checkpoint.getRunId(), instanceId);
        }
    }

    private long heartbeatMillis() {
        return Math.max(50, leaseDuration.toMillis() / 3);
    }

    private WorkflowCheckpointEntity initialCheckpoint(
            String tenantId,
            String workflowId,
            String graphVersion,
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            String runId) {
        WorkflowCheckpointEntity value = new WorkflowCheckpointEntity();
        value.setTenantId(hasText(tenantId) ? tenantId : UserContextHolder.currentTenantId());
        value.setRunId(runId);
        value.setWorkflowId(workflowId);
        value.setGraphVersion(graphVersion);
        value.setGraphJson(JsonUtils.toJson(graph));
        value.setInputsJson(JsonUtils.toJson(inputs == null ? Map.of() : inputs));
        value.setConversationId(conversationId);
        value.setStatus(WorkflowRunStatus.RUNNING.name());
        Map<String, String> states = new LinkedHashMap<>();
        graph.getNodes().forEach(node -> states.put(node.getId(), NodeExecutionStatus.PENDING.name()));
        value.setNodeStatesJson(JsonUtils.toJson(states));
        value.setVariablePoolJson(JsonUtils.toJson(Map.of("sys", inputs == null ? Map.of() : inputs)));
        value.setBranchResultsJson("{}");
        value.setIterationCursorsJson("{}");
        value.setPendingNodesJson(
                JsonUtils.toJson(graph.getNodes().stream().map(NodeDef::getId).toList()));
        value.setCheckpointVersion(0L);
        return value;
    }

    private static WorkflowRunNodeEntity initialNode(String runId, NodeDef node, NodeExecutionPolicy policy) {
        WorkflowRunNodeEntity value = new WorkflowRunNodeEntity();
        value.setId(runId + ":" + node.getId());
        value.setRunId(runId);
        value.setNodeId(node.getId());
        value.setNodeType(node.getType().name());
        value.setStatus(NodeExecutionStatus.PENDING.name());
        value.setAttempt(0);
        value.setExecutionMode(policy.executionMode().name());
        value.setIdempotencyKey(policy.idempotencyKey());
        value.setResultCachePolicy(policy.resultCachePolicy().name());
        value.setResumable(policy.resumable());
        return value;
    }

    private static WorkflowResumeState resumeState(
            WorkflowCheckpointEntity checkpoint, List<WorkflowRunNodeEntity> nodes) {
        Map<String, WorkflowResumeState.ResumedNode> terminal = new LinkedHashMap<>();
        Map<String, Integer> attempts = new LinkedHashMap<>();
        for (WorkflowRunNodeEntity node : nodes) {
            attempts.put(node.getNodeId(), node.getAttempt() == null ? 0 : node.getAttempt());
            if (NodeExecutionStatus.COMPLETED.name().equals(node.getStatus())) {
                terminal.put(node.getNodeId(), new WorkflowResumeState.ResumedNode(true, node.getSelectedHandle()));
            } else if (NodeExecutionStatus.SKIPPED.name().equals(node.getStatus())) {
                terminal.put(node.getNodeId(), new WorkflowResumeState.ResumedNode(false, null));
            }
        }
        Map<String, Map<String, Object>> pool = JsonUtils.parse(
                checkpoint.getVariablePoolJson(), new TypeReference<Map<String, Map<String, Object>>>() {});
        Map<String, Object> cursors =
                JsonUtils.parse(checkpoint.getIterationCursorsJson(), new TypeReference<Map<String, Object>>() {});
        return new WorkflowResumeState(terminal, pool, attempts, cursors);
    }

    private final class CheckpointObserver implements WorkflowExecutionObserver {
        private final WorkflowCheckpointEntity checkpoint;
        private final Map<String, String> states;
        private final Map<String, String> branches;
        private final Set<String> pending;
        private final Map<String, Object> iterationCursors;

        private CheckpointObserver(WorkflowCheckpointEntity checkpoint) {
            this.checkpoint = checkpoint;
            this.states = new LinkedHashMap<>(
                    JsonUtils.parse(checkpoint.getNodeStatesJson(), new TypeReference<Map<String, String>>() {}));
            this.branches = new LinkedHashMap<>(
                    JsonUtils.parse(checkpoint.getBranchResultsJson(), new TypeReference<Map<String, String>>() {}));
            this.pending = new LinkedHashSet<>(JsonUtils.parseList(checkpoint.getPendingNodesJson(), String.class));
            this.iterationCursors = new LinkedHashMap<>(
                    JsonUtils.parse(checkpoint.getIterationCursorsJson(), new TypeReference<Map<String, Object>>() {}));
        }

        @Override
        public synchronized void iterationProgress(String nodeId, Object cursor, ExecutionContext context) {
            iterationCursors.put(nodeId, cursor);
            checkpoint.setIterationCursorsJson(JsonUtils.toJson(iterationCursors));
            checkpoint.setVariablePoolJson(JsonUtils.toJson(context.getPool().snapshot()));
            store.transition(checkpoint, checkpoint.getCheckpointVersion(), WorkflowExecutionEventType.NODE_OUTPUT);
        }

        @Override
        public synchronized void nodeScheduled(NodeDef node, int attempt, ExecutionContext context) {
            states.put(node.getId(), NodeExecutionStatus.SCHEDULED.name());
            checkpoint.setNodeStatesJson(JsonUtils.toJson(states));
            WorkflowRunNodeEntity durableNode =
                    durableNode(node, NodeExecutionStatus.SCHEDULED, NodeResult.empty(), attempt, context);
            store.commitNode(
                    checkpoint,
                    checkpoint.getCheckpointVersion(),
                    durableNode,
                    WorkflowExecutionEventType.NODE_SCHEDULED);
        }

        @Override
        public synchronized void nodeStarted(NodeDef node, int attempt, ExecutionContext context) {
            states.put(node.getId(), NodeExecutionStatus.RUNNING.name());
            checkpoint.setNodeStatesJson(JsonUtils.toJson(states));
            WorkflowRunNodeEntity durableNode =
                    durableNode(node, NodeExecutionStatus.RUNNING, NodeResult.empty(), attempt, context);
            durableNode.setStartedAt(LocalDateTime.now());
            store.commitNode(
                    checkpoint,
                    checkpoint.getCheckpointVersion(),
                    durableNode,
                    WorkflowExecutionEventType.NODE_STARTED);
        }

        @Override
        public synchronized void nodeFinished(
                NodeDef node, NodeExecutionStatus status, NodeResult result, int attempt, ExecutionContext context) {
            states.put(node.getId(), status.name());
            if (status == NodeExecutionStatus.COMPLETED || status == NodeExecutionStatus.SKIPPED) {
                pending.remove(node.getId());
            }
            if (result.getHandle() != null) branches.put(node.getId(), result.getHandle());
            checkpoint.setNodeStatesJson(JsonUtils.toJson(states));
            checkpoint.setVariablePoolJson(JsonUtils.toJson(context.getPool().snapshot()));
            checkpoint.setBranchResultsJson(JsonUtils.toJson(branches));
            checkpoint.setPendingNodesJson(JsonUtils.toJson(new ArrayList<>(pending)));
            if (!WorkflowRunStatus.WAITING.name().equals(checkpoint.getStatus()))
                checkpoint.setResumeNodeId(pending.stream().findFirst().orElse(null));
            if (status == NodeExecutionStatus.WAITING && result.getWaitRequest() != null) {
                io.aigoodle.workflow.node.WorkflowWaitRequest wait = result.getWaitRequest();
                checkpoint.setStatus(WorkflowRunStatus.WAITING.name());
                checkpoint.setResumeNodeId(node.getId());
                checkpoint.setWaitType(wait.type().name());
                checkpoint.setCorrelationKey(wait.correlationKey());
                checkpoint.setResumeTokenHash(sha256(wait.resumeToken()));
                checkpoint.setInputSchemaJson(JsonUtils.toJson(wait.inputSchema()));
                checkpoint.setWaitExpiresAt(local(wait.expiresAt()));
                checkpoint.setWakeAt(local(wait.wakeAt()));
                checkpoint.setInterruptReason("Waiting at node " + node.getId());
                if (humanInteractions != null) humanInteractions.ensureWaiting(checkpoint, node, wait);
            }

            WorkflowRunNodeEntity durableNode = durableNode(node, status, result, attempt, context);
            durableNode.setFinishedAt(LocalDateTime.now());
            WorkflowExecutionEventType event =
                    switch (status) {
                        case FAILED -> WorkflowExecutionEventType.NODE_FAILED;
                        case RETRYING -> WorkflowExecutionEventType.NODE_RETRYING;
                        default -> WorkflowExecutionEventType.NODE_COMPLETED;
                    };
            store.commitNode(checkpoint, checkpoint.getCheckpointVersion(), durableNode, event);
            metrics.nodeFinished(node.getType(), status, result);
        }

        private WorkflowRunNodeEntity durableNode(
                NodeDef node, NodeExecutionStatus status, NodeResult result, int attempt, ExecutionContext context) {
            WorkflowRunNodeEntity durableNode = new WorkflowRunNodeEntity();
            durableNode.setNodeId(node.getId());
            durableNode.setNodeType(node.getType().name());
            durableNode.setStatus(status.name());
            durableNode.setAttempt(attempt);
            durableNode.setSelectedHandle(result.getHandle());
            durableNode.setOutputsJson(JsonUtils.toJson(result.getOutputs()));
            durableNode.setError(result.getError());
            durableNode.setErrorCode(result.getErrorCode());
            durableNode.setRetryable(result.getRetryable());
            durableNode.setExecutorInstance(instanceId);
            durableNode.setTokenCount(result.getTokenCount());
            durableNode.setCost(result.getCost());
            durableNode.setExternalStatus(result.getExternalStatus());
            durableNode.setTraceId(org.slf4j.MDC.get("traceId"));
            durableNode.setSpanId(org.slf4j.MDC.get("spanId"));
            NodeExecutionPolicy policy = engine.executionPolicy(node, context);
            durableNode.setExecutionMode(policy.executionMode().name());
            durableNode.setIdempotencyKey(policy.idempotencyKey());
            durableNode.setResultCachePolicy(policy.resultCachePolicy().name());
            durableNode.setResumable(policy.resumable());
            return durableNode;
        }

        private synchronized void finish(WorkflowRunResult result) {
            checkpoint.setStatus(result.getStatus().name());
            checkpoint.setInterruptReason(result.getError());
            if (result.getStatus() != WorkflowRunStatus.WAITING) checkpoint.setResumeNodeId(null);
            else {
                var wait = result.getWaitRequest();
                checkpoint.setResumeNodeId(result.getWaitingNodeId());
                checkpoint.setWaitType(wait.type().name());
                checkpoint.setCorrelationKey(wait.correlationKey());
                checkpoint.setResumeTokenHash(sha256(wait.resumeToken()));
                checkpoint.setInputSchemaJson(JsonUtils.toJson(wait.inputSchema()));
                checkpoint.setWaitExpiresAt(local(wait.expiresAt()));
                checkpoint.setWakeAt(local(wait.wakeAt()));
            }
            WorkflowExecutionEventType event =
                    switch (result.getStatus()) {
                        case SUCCEEDED -> WorkflowExecutionEventType.RUN_COMPLETED;
                        case WAITING -> WorkflowExecutionEventType.RUN_WAITING;
                        case PAUSED -> WorkflowExecutionEventType.RUN_PAUSED;
                        case CANCELLED -> WorkflowExecutionEventType.RUN_CANCELLED;
                        case TIMED_OUT -> WorkflowExecutionEventType.RUN_TIMED_OUT;
                        default -> WorkflowExecutionEventType.RUN_FAILED;
                    };
            store.transition(checkpoint, checkpoint.getCheckpointVersion(), event);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDateTime local(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
