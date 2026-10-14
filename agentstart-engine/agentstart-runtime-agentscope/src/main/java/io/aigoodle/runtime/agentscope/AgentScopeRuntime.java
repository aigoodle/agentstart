package io.aigoodle.runtime.agentscope;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.runtime.AgentRunEvent;
import io.aigoodle.agent.runtime.AgentRunSnapshot;
import io.aigoodle.agent.runtime.AgentRunStatus;
import io.aigoodle.agent.runtime.AgentRunStore;
import io.aigoodle.agent.runtime.AgentRuntimeExtension;
import io.aigoodle.agent.runtime.InMemoryAgentRunStore;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A factory-owned AgentScope execution boundary; core Agent APIs never expose SDK types. */
public final class AgentScopeRuntime implements AgentRuntimeExtension {
    private final Map<String, AgentScopeAgentFactory> factories;
    private final Supplier<AgentRunStore> store;

    private record RunKey(String tenant, String runId) {}

    private static final class ActiveRun {
        final Thread thread = Thread.currentThread();
        volatile boolean cancelled;
    }

    private final java.util.concurrent.ConcurrentMap<RunKey, ActiveRun> activeRuns =
            new java.util.concurrent.ConcurrentHashMap<>();

    public AgentScopeRuntime(Map<String, AgentScopeAgentFactory> factories) {
        this(factories, new InMemoryAgentRunStore());
    }

    public AgentScopeRuntime(Map<String, AgentScopeAgentFactory> factories, AgentRunStore store) {
        this(factories, () -> store);
    }

    AgentScopeRuntime(Map<String, AgentScopeAgentFactory> factories, Supplier<AgentRunStore> store) {
        this.factories = Map.copyOf(factories);
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<AgentRunSnapshot> findRun(String runId) {
        return store.get().find(UserContextHolder.currentTenantId(), runId);
    }

    @Override
    public List<AgentRunEvent> runEvents(String runId, long afterSequence, int limit) {
        return store.get().events(UserContextHolder.currentTenantId(), runId, afterSequence, limit);
    }

    @Override
    public AgentRunSnapshot cancel(String runId) {
        String tenant = UserContextHolder.currentTenantId();
        AgentRunStore runStore = store.get();
        var key = new RunKey(tenant, runId);
        var active = activeRuns.get(key);
        if (active != null) {
            synchronized (active) {
                var snapshot = requireRun(runStore, tenant, runId);
                if (snapshot.status().isTerminal()) return snapshot;
                var cancelled =
                        runStore.transition(tenant, runId, AgentRunStatus.CANCELLED, null, "Cancelled by caller");
                active.cancelled = true;
                active.thread.interrupt();
                return cancelled;
            }
        }
        var snapshot = requireRun(runStore, tenant, runId);
        return snapshot.status().isTerminal()
                ? snapshot
                : runStore.transition(tenant, runId, AgentRunStatus.CANCELLED, null, "Cancelled by caller");
    }

    private static AgentRunSnapshot requireRun(AgentRunStore runStore, String tenant, String runId) {
        var snapshot = runStore.find(tenant, runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found", null));
        var definition = JsonUtils.parse(snapshot.definitionJson(), AgentDefinition.class);
        if (!"AGENTSCOPE".equalsIgnoreCase(definition.getRuntimeType())) {
            throw new PlatformException("agent_runtime_mismatch", "Run belongs to another runtime", null);
        }
        return snapshot;
    }

    @Override
    public String runtimeType() {
        return "AGENTSCOPE";
    }

    @Override
    public AgentResponse run(
            AgentDefinition definition, AgentRequest request, Consumer<AgentStep> steps, Consumer<String> tokens) {
        return execute(definition, request, steps, tokens, null, null);
    }

    private record ApprovalOwner(String userId, int modelCalls, int toolCalls) {}

    @Override
    public AgentResponse resume(String runId, io.aigoodle.agent.runtime.AgentResumeCommand command) {
        String tenant = UserContextHolder.currentTenantId();
        var snapshot = requireRun(store.get(), tenant, runId);
        if (snapshot.status() != AgentRunStatus.WAITING_APPROVAL) {
            throw new PlatformException("agent_run_not_paused", "Run is not waiting for approval", null);
        }
        var paused = JsonUtils.parse(snapshot.responseJson(), AgentResponse.class);
        if (paused.getCheckpoint() == null
                || !"AGENTSCOPE".equals(paused.getCheckpoint().strategy())) {
            throw new PlatformException("agent_checkpoint_invalid", "SDK approval checkpoint is missing", null);
        }
        var owner = JsonUtils.parse(paused.getCheckpoint().stateJson(), ApprovalOwner.class);
        if (!Objects.equals(owner.userId(), UserContextHolder.currentUserId())) {
            throw new SecurityException("Only the original caller can resume this run");
        }
        var message = AgentScopeApprovalBridge.confirmation(paused.getPendingApprovals(), command);
        return execute(
                JsonUtils.parse(snapshot.definitionJson(), AgentDefinition.class),
                JsonUtils.parse(snapshot.requestJson(), AgentRequest.class),
                null,
                null,
                snapshot,
                message);
    }

    private AgentResponse execute(
            AgentDefinition definition,
            AgentRequest request,
            Consumer<AgentStep> steps,
            Consumer<String> tokens,
            AgentRunSnapshot resumed,
            io.agentscope.core.message.Msg confirmation) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(request, "request");
        String tenant =
                definition.getTenantId() == null || definition.getTenantId().isBlank()
                        ? UserContextHolder.currentTenantId()
                        : definition.getTenantId();
        if (UserContextHolder.isAuthenticated() && !tenant.equals(UserContextHolder.currentTenantId())) {
            throw new SecurityException("Requested tenant does not match the authenticated tenant");
        }
        if (!definition.getApprovalRequiredTools().isEmpty() && !definition.isMemoryEnabled()) {
            throw new PlatformException("agentscope_approval_unavailable", "Approval requires shared SDK state", null);
        }
        String reference = definition.getRuntimeRef();
        AgentScopeAgentFactory factory = factories.get(reference == null ? "default" : reference);
        if (factory == null && reference == null && factories.size() == 1) {
            factory = factories.values().iterator().next();
        }
        if (factory == null) {
            throw new PlatformException("agent_runtime_unavailable", "AgentScope factory is not installed", null);
        }
        String conversation = resumed != null
                ? resumed.conversationId()
                : request.getConversationId() == null
                                || request.getConversationId().isBlank()
                        ? UUID.randomUUID().toString()
                        : request.getConversationId();
        AgentResponse response = AgentResponse.forConversation(conversation);
        response.setRunId(resumed == null ? UUID.randomUUID().toString() : resumed.runId());
        if (resumed != null) {
            var previous = JsonUtils.parse(resumed.responseJson(), AgentResponse.class);
            response.getSteps().addAll(previous.getSteps());
            response.setIterations(previous.getIterations());
        }
        RuntimeContext context = RuntimeContext.builder()
                .userId(key(tenant, UserContextHolder.currentUserId()))
                .sessionId(key(tenant, definition.getId(), conversation))
                .put("tenantId", tenant)
                .put("conversationId", conversation)
                .put("runId", response.getRunId())
                .build();
        if (resumed != null) {
            var previous = JsonUtils.parse(resumed.responseJson(), AgentResponse.class);
            var counts = JsonUtils.parse(previous.getCheckpoint().stateJson(), ApprovalOwner.class);
            context.put("usedModelCalls", counts.modelCalls());
            context.put("usedToolCalls", counts.toolCalls());
        }
        AgentRunStore runStore = store.get();
        String runId = response.getRunId();
        if (resumed == null)
            runStore.create(
                    runId,
                    definition.toBuilder()
                            .tenantId(tenant)
                            .runtimeType("AGENTSCOPE")
                            .build(),
                    request,
                    conversation);
        Consumer<AgentStep> durableSteps = step -> {
            runStore.appendEvent(tenant, runId, "AGENT_STEP", JsonUtils.toJson(step));
            if (steps != null) steps.accept(step);
        };
        AgentRunStatus terminal = AgentRunStatus.COMPLETED;
        var activeKey = new RunKey(tenant, runId);
        var active = new ActiveRun();
        if (activeRuns.putIfAbsent(activeKey, active) != null) {
            throw new PlatformException("agent_run_active", "Run is already executing", null);
        }
        try {
            synchronized (active) {
                if (runStore.find(tenant, runId).orElseThrow().status() == AgentRunStatus.CANCELLED) {
                    activeRuns.remove(activeKey, active);
                    response.setStatus(AgentResponse.Status.FAILED);
                    response.setError("Cancelled by caller");
                    return response;
                }
                runStore.transition(tenant, runId, AgentRunStatus.RUNNING, null, null);
            }
        } catch (RuntimeException failure) {
            activeRuns.remove(activeKey, active);
            throw failure;
        }
        var trace = new ToolEventTrace();
        try (var agent = factory.create(definition, request, context)) {
            var events = reactor.core.publisher.Flux.defer(() -> active.cancelled
                            ? reactor.core.publisher.Flux.<AgentEvent>error(
                                    new IllegalStateException("Cancelled by caller"))
                            : confirmation == null
                                    ? agent.streamEvents(request.getQuery(), context)
                                    : agent.streamEvents(confirmation, context))
                    .doOnNext(event -> accept(event, response, durableSteps, tokens, trace))
                    .doOnCancel(agent::interrupt);
            Long timeout = request.getTimeoutMillis();
            var completion = events.then();
            if (timeout != null && timeout > 0) completion = completion.timeout(Duration.ofMillis(timeout));
            completion.block();
            if (response.getText() == null
                    && response.getStatus() != AgentResponse.Status.MAX_ITERATIONS
                    && response.getStatus() != AgentResponse.Status.AWAITING_APPROVAL) {
                throw new IllegalStateException("AgentScope execution ended without a terminal result");
            }
            if (response.getStatus() == AgentResponse.Status.AWAITING_APPROVAL) {
                AgentScopeCallBudget budget = context.get("callBudget");
                response.setCheckpoint(new io.aigoodle.agent.api.AgentCheckpoint(
                        "AGENTSCOPE",
                        JsonUtils.toJson(new ApprovalOwner(
                                UserContextHolder.currentUserId(),
                                budget == null ? 0 : budget.modelCount(),
                                budget == null ? 0 : budget.toolCount()))));
                terminal = AgentRunStatus.WAITING_APPROVAL;
            }
            if (response.getStatus() == AgentResponse.Status.MAX_ITERATIONS) terminal = AgentRunStatus.MAX_ITERATIONS;
        } catch (RuntimeException exception) {
            response.setStatus(AgentResponse.Status.FAILED);
            response.setError(exception.getMessage());
            boolean timedOut = false;
            boolean maximumIterations = false;
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.util.concurrent.TimeoutException) timedOut = true;
                if (cause instanceof PlatformException platform && "agent_max_iterations".equals(platform.getCode())) {
                    maximumIterations = true;
                }
            }
            terminal = timedOut ? AgentRunStatus.TIMED_OUT : AgentRunStatus.FAILED;
            if (maximumIterations) {
                response.stopAfterMaxIterations(definition.getMaxIterations());
                terminal = AgentRunStatus.MAX_ITERATIONS;
            }
        }
        try {
            synchronized (active) {
                if (active.cancelled) {
                    response.setStatus(AgentResponse.Status.FAILED);
                    response.setError("Cancelled by caller");
                } else {
                    runStore.transition(tenant, runId, terminal, response, response.getError());
                }
            }
        } finally {
            activeRuns.remove(activeKey, active);
        }
        return response;
    }

    private static String key(String... parts) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(JsonUtils.toJson(java.util.Arrays.asList(parts)).getBytes(StandardCharsets.UTF_8));
    }

    private static void accept(
            AgentEvent event,
            AgentResponse response,
            Consumer<AgentStep> listener,
            Consumer<String> tokens,
            ToolEventTrace trace) {
        trace.accumulate(event);
        if (event instanceof TextBlockDeltaEvent delta && tokens != null) tokens.accept(delta.getDelta());
        if (event instanceof ModelCallStartEvent) response.setIterations(response.getIterations() + 1);
        if (event instanceof ExceedMaxItersEvent exceeded) response.stopAfterMaxIterations(exceeded.getMaxIters());
        if (event instanceof RequireExternalExecutionEvent) {
            throw new PlatformException(
                    "agent_external_execution_required",
                    "AgentScope execution requires an external continuation",
                    null);
        }
        AgentStep step = null;
        if (event instanceof RequireUserConfirmEvent confirmationEvent) {
            response.awaitApprovals(AgentScopeApprovalBridge.pending(confirmationEvent));
            step = AgentStep.of(AgentStep.Kind.APPROVAL, JsonUtils.toJson(response.getPendingApprovals()));
        } else if (event instanceof AgentResultEvent result) {
            if (response.getStatus() != AgentResponse.Status.MAX_ITERATIONS
                    && response.getStatus() != AgentResponse.Status.AWAITING_APPROVAL) {
                response.complete(result.getResult().getTextContent());
                step = AgentStep.of(AgentStep.Kind.FINAL, result.getResult().getTextContent());
            }
        } else if (event instanceof ToolCallEndEvent tool) {
            step = AgentStep.action(tool.getToolCallName(), trace.takeInput(tool.getToolCallId()));
        } else if (event instanceof ToolResultEndEvent tool) {
            step = AgentStep.observation(
                    trace.takeOutput(tool.getToolCallId(), tool.getToolCallName() + ": " + tool.getState()));
        }
        if (step != null) {
            response.addStep(step);
            if (listener != null) listener.accept(step);
        }
    }

    /** SDK argument/result fragments are correlated by call ID, including parallel calls. */
    private static final class ToolEventTrace {
        private final Map<String, StringBuilder> inputs = new java.util.HashMap<>();
        private final Map<String, StringBuilder> outputs = new java.util.HashMap<>();

        void accumulate(AgentEvent event) {
            if (event instanceof ToolCallDeltaEvent delta) {
                append(inputs, delta.getToolCallId(), delta.getDelta());
            } else if (event instanceof ToolResultTextDeltaEvent delta) {
                append(outputs, delta.getToolCallId(), delta.getDelta());
            } else if (event instanceof ToolResultDataDeltaEvent delta) {
                append(outputs, delta.getToolCallId(), JsonUtils.toJson(delta.getData()));
            }
        }

        private static void append(Map<String, StringBuilder> fragments, String id, String delta) {
            if (delta != null)
                fragments.computeIfAbsent(id, ignored -> new StringBuilder()).append(delta);
        }

        String takeInput(String id) {
            var value = inputs.remove(id);
            return value == null ? null : value.toString();
        }

        String takeOutput(String id, String fallback) {
            var value = outputs.remove(id);
            return value == null ? fallback : value.toString();
        }
    }
}
