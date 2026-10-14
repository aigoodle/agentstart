package io.aigoodle.workflow.engine;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionPolicy;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeFailureClassifier;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a {@link WorkflowGraph} as a DAG: independent branches run in parallel.
 * <p>
 * Every node's execution is wrapped in a {@link CompletableFuture} whose upstream
 * dependencies are its incoming edges' source nodes. A node runs only after all of
 * its structural parents have completed (executed or skipped), and only if at least
 * one incoming edge "fired" — i.e. the parent executed and produced a matching
 * {@code sourceHandle}. Untaken branches propagate as a "skipped" outcome so
 * downstream nodes correctly cascade to skipped without running.
 * <p>
 * Concurrency:
 * <ul>
 *   <li>Nodes run on a per-run virtual-thread executor — ideal for the I/O-heavy
 *       node types (HTTP / LLM / tools / retrieval).</li>
 *   <li>The variable pool is a {@link ConcurrentHashMap}; parent writes happen-before
 *       child reads via {@link CompletableFuture} completion.</li>
 *   <li>Step recording + the optional {@code stepListener} are serialised under a
 *       shared lock so observers see a totally-ordered stream, and the returned
 *       {@code steps} list stays consistent for callers that persist it.</li>
 * </ul>
 * A step ceiling still guards against pathologically large graphs; explicit iteration
 * is handled by the ITERATION node, not by re-entrant graph edges.
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_STEPS = 1000;
    private static final ScheduledExecutorService DEADLINE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("workflow-deadlines").factory());
    private static final ExecutorService NODE_ATTEMPT_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("workflow-node-attempt-", 0).factory());

    private final NodeExecutorRegistry executorRegistry;
    private final WorkflowCompiler compiler;

    public WorkflowEngine(NodeExecutorRegistry executorRegistry) {
        this(executorRegistry, new WorkflowCompiler());
    }

    public WorkflowEngine(NodeExecutorRegistry executorRegistry, WorkflowCompiler compiler) {
        this.executorRegistry = executorRegistry;
        this.compiler = compiler;
    }

    public NodeExecutionPolicy executionPolicy(NodeDef node, ExecutionContext context) {
        return executorRegistry.get(node.getType()).policy(node, context);
    }

    public void cancelWaiting(NodeDef node, Map<String, Object> savedNamespace) {
        executorRegistry.get(node.getType()).cancelWaiting(node, savedNamespace);
    }

    public WorkflowRunResult run(WorkflowGraph graph, Map<String, Object> inputs, String conversationId) {
        return run(graph, inputs, conversationId, null, null);
    }

    public WorkflowRunResult run(
            WorkflowGraph graph, Map<String, Object> inputs, String conversationId, Consumer<StepRecord> stepListener) {
        return run(graph, inputs, conversationId, stepListener, null);
    }

    /**
     * Full-fidelity entry point: threads an optional {@link
     * io.aigoodle.workflow.chat.ChatStreamSink} into the context so
     * downstream LLM / ANSWER nodes can push token-level deltas back to the
     * caller (SSE). Existing blocking callers still work by passing null.
     */
    public WorkflowRunResult run(
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            io.aigoodle.workflow.chat.ChatStreamSink chatSink) {
        return run(graph, inputs, conversationId, stepListener, chatSink, UserContextHolder.currentTenantId());
    }

    public WorkflowRunResult run(
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            io.aigoodle.workflow.chat.ChatStreamSink chatSink,
            String tenantId) {
        return run(graph, inputs, conversationId, stepListener, chatSink, tenantId, WorkflowRunOptions.defaults());
    }

    public WorkflowRunResult run(
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            io.aigoodle.workflow.chat.ChatStreamSink chatSink,
            String tenantId,
            WorkflowRunOptions options) {
        return run(
                graph,
                inputs,
                conversationId,
                stepListener,
                chatSink,
                tenantId,
                options,
                WorkflowResumeState.empty(),
                WorkflowExecutionObserver.NOOP);
    }

    public WorkflowRunResult run(
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            io.aigoodle.workflow.chat.ChatStreamSink chatSink,
            String tenantId,
            WorkflowRunOptions options,
            WorkflowResumeState resumeState,
            WorkflowExecutionObserver observer) {
        graph = compiler.compile(graph);
        ExecutionContext context = ExecutionContext.start(inputs, conversationId, chatSink, options.runId());
        if (!resumeState.variablePool().isEmpty()) context.getPool().restore(resumeState.variablePool());
        context.setCancellationToken(options.cancellationToken());
        context.setIterationCursors(new ConcurrentHashMap<>(resumeState.iterationCursors()));
        WorkflowExecutionObserver executionObserver = observer;
        context.setIterationProgressListener(
                (nodeId, cursor) -> executionObserver.iterationProgress(nodeId, cursor, context));
        // Capture the request tenant before node execution switches to per-run
        // virtual threads. UserContextHolder is backed by a regular ThreadLocal,
        // so reading it inside NodeExecutor/NodeModelResolver would otherwise
        // lose the authenticated tenant and silently fall back to "default".
        context.setTenantId(tenantId == null || tenantId.isBlank() ? UserContextHolder.currentTenantId() : tenantId);
        context.setUserId(UserContextHolder.currentUserId());
        context.setExecutionUser(UserContextHolder.get());
        context.setResourceTenantId(options.resourceTenantId());
        context.getPool().setSystem("tenant_id", context.getTenantId());
        context.getPool().setSystem("user_id", context.getUserId());
        WorkflowRunResult result = WorkflowRunResult.forRun(context.getRunId(), context.getSteps());
        RunState run = new RunState(context, stepListener, options, resumeState, observer);
        for (Map.Entry<String, WorkflowResumeState.ResumedNode> entry :
                resumeState.terminalNodes().entrySet()) {
            NodeDef resumedNode = graph.node(entry.getKey());
            if (resumedNode.getType() == NodeType.END && entry.getValue().executed()) {
                run.endOutputs.putAll(context.getPool().namespace(entry.getKey()));
            }
        }

        NodeDef startNode = graph.startNode();
        Map<String, List<EdgeDef>> incomingByTarget = indexIncoming(graph);

        Map<String, CompletableFuture<Void>> nodeFutures = new HashMap<>();
        for (NodeDef node : graph.getNodes()) {
            nodeFutures.put(node.getId(), new CompletableFuture<>());
        }

        // A per-run virtual-thread executor: nodes are typically I/O-bound (HTTP,
        // LLM, retrieval), so per-task virtual threads give ideal parallelism
        // without needing a bounded pool tuned for blocking.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledFuture<?> workflowDeadline = DEADLINE_SCHEDULER.schedule(
                () -> options.cancellationToken().timeout("Workflow deadline exceeded"),
                options.workflowTimeout().toMillis(),
                TimeUnit.MILLISECONDS);
        try {
            for (NodeDef node : graph.getNodes()) {
                scheduleNode(
                        node,
                        startNode,
                        incomingByTarget.getOrDefault(node.getId(), List.of()),
                        nodeFutures,
                        run,
                        executor);
            }

            try {
                CompletableFuture.allOf(nodeFutures.values().toArray(new CompletableFuture[0]))
                        .join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                log.error("Workflow run {} failed: {}", context.getRunId(), cause.getMessage(), cause);
                return result.fail(cause.getMessage(), run.endOutputs);
            } catch (Exception ex) {
                log.error("Workflow run {} failed: {}", context.getRunId(), ex.getMessage(), ex);
                return result.fail(ex.getMessage(), run.endOutputs);
            }
        } catch (RuntimeException ex) {
            log.error(
                    "Workflow run {} encountered an engine or persistence failure: {}",
                    context.getRunId(),
                    ex.getMessage(),
                    ex);
            options.cancellationToken().cancel("Workflow infrastructure failure");
            return result.fail(safeMessage(ex), run.endOutputs);
        } finally {
            workflowDeadline.cancel(false);
            executor.shutdownNow();
        }

        WorkflowWaitRequest waitRequest = run.waitRequest.get();
        if (waitRequest != null) {
            return result.waiting(run.waitingNodeId.get(), waitRequest, run.endOutputs);
        }
        String failureMessage = run.failure.get();
        if (failureMessage != null) {
            return result.fail(failureMessage, run.endOutputs);
        }
        if (options.cancellationToken().isCancelled()) {
            WorkflowRunStatus status = options.cancellationToken().isTimedOut()
                    ? WorkflowRunStatus.TIMED_OUT
                    : options.cancellationToken().isPaused() ? WorkflowRunStatus.PAUSED : WorkflowRunStatus.CANCELLED;
            return result.terminate(status, options.cancellationToken().reason(), run.endOutputs);
        }

        // Chatflows may terminate at a direct ANSWER node without a technical END
        // node. Promote the executed terminal answer to run outputs so blocking
        // chat and resumed human-input requests return the same visible content.
        if (run.endOutputs.isEmpty()) {
            for (NodeDef node : graph.getNodes()) {
                if (node.getType() == NodeType.ANSWER
                        && graph.outgoing(node.getId()).isEmpty()) {
                    run.endOutputs.putAll(context.getPool().namespace(node.getId()));
                }
            }
        }

        return result.succeed(run.endOutputs);
    }

    private void scheduleNode(
            NodeDef node,
            NodeDef startNode,
            List<EdgeDef> incoming,
            Map<String, CompletableFuture<Void>> nodeFutures,
            RunState run,
            Executor executor) {
        boolean isStart = node.getId().equals(startNode.getId());
        CompletableFuture<Void> nodeFuture = nodeFutures.get(node.getId());

        WorkflowResumeState.ResumedNode resumed =
                run.resumeState.terminalNodes().get(node.getId());
        if (resumed != null) {
            run.outcomes.put(
                    node.getId(), resumed.executed() ? NodeOutcome.executed(resumed.handle()) : NodeOutcome.skipped());
            nodeFuture.complete(null);
            return;
        }

        run.observer.nodeScheduled(node, run.nextAttempt(node.getId()), run.context);

        Runnable nodeTask = () -> executeNode(node, isStart, incoming, run, nodeFuture);

        if (isStart) {
            executor.execute(nodeTask);
            return;
        }
        if (incoming.isEmpty()) {
            // Orphan node: never reachable from START, so skip and let the run finish.
            run.outcomes.put(node.getId(), NodeOutcome.skipped());
            nodeFuture.complete(null);
            return;
        }
        CompletableFuture<?>[] parents = incoming.stream()
                .map(edge -> nodeFutures.get(edge.getSource()))
                .filter(Objects::nonNull)
                .distinct()
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(parents).thenRunAsync(nodeTask, executor);
    }

    private void executeNode(
            NodeDef node, boolean isStart, List<EdgeDef> incoming, RunState run, CompletableFuture<Void> nodeFuture) {
        boolean permit = false;
        try {
            if (run.waitRequest.get() != null) {
                run.outcomes.put(node.getId(), NodeOutcome.skipped());
                run.observer.nodeFinished(
                        node,
                        NodeExecutionStatus.CANCELLED,
                        NodeResult.empty(),
                        run.nextAttempt(node.getId()),
                        run.context);
                return;
            }
            boolean fired = isStart || anyIncomingFired(incoming, run.outcomes);
            if (!fired) {
                run.outcomes.put(node.getId(), NodeOutcome.skipped());
                run.observer.nodeFinished(
                        node,
                        NodeExecutionStatus.SKIPPED,
                        NodeResult.empty(),
                        run.nextAttempt(node.getId()),
                        run.context);
                return;
            }
            if (run.failure.get() != null || run.options.cancellationToken().isCancelled()) {
                run.outcomes.put(node.getId(), NodeOutcome.skipped());
                run.observer.nodeFinished(
                        node,
                        NodeExecutionStatus.CANCELLED,
                        NodeResult.empty(),
                        run.nextAttempt(node.getId()),
                        run.context);
                return;
            }
            run.concurrency.acquire();
            permit = true;
            run.options.cancellationToken().throwIfCancelled();
            NodeExecutor nodeExecutor = executorRegistry.get(node.getType());
            NodeExecutionPolicy policy = nodeExecutor.policy(node, run.context);
            int maximumAttempts = policy.executionMode() == io.aigoodle.workflow.node.NodeExecutionMode.SIDE_EFFECT
                            && !policy.resumable()
                    ? 1
                    : policy.retryPolicy().maxAttempts();
            NodeResult nodeResult;
            int attempt;
            while (true) {
                attempt = run.nextAttempt(node.getId());
                run.attempts.put(node.getId(), attempt);
                run.observer.nodeStarted(node, attempt, run.context);
                if (run.stepCount.incrementAndGet() > MAX_STEPS) {
                    nodeResult = NodeResult.failure("Workflow exceeded " + MAX_STEPS + " steps");
                } else {
                    AttemptExecution execution = executeAttempt(node, nodeExecutor, run);
                    nodeResult = execution.result();
                    run.record(
                            node,
                            nodeResult,
                            execution.elapsedMillis(),
                            attempt,
                            execution.startedAt(),
                            execution.finishedAt());
                }
                if (!nodeResult.isFailed()
                        || Boolean.FALSE.equals(nodeResult.getRetryable())
                        || run.options.cancellationToken().isCancelled()
                        || attempt >= maximumAttempts) break;

                run.observer.nodeFinished(node, NodeExecutionStatus.RETRYING, nodeResult, attempt, run.context);
                Duration backoff = policy.retryPolicy().backoffBefore(attempt + 1);
                if (!backoff.isZero()) {
                    try (RunCancellationToken.Registration ignored =
                            run.options.cancellationToken().registerCurrentThread()) {
                        Thread.sleep(backoff.toMillis());
                    }
                }
                run.options.cancellationToken().throwIfCancelled();
            }

            if (nodeResult.isWaiting()) {
                synchronized (run) {
                    if (run.waitRequest.get() == null) {
                        run.waitingNodeId.set(node.getId());
                        run.waitRequest.set(nodeResult.getWaitRequest());
                    }
                }
                run.observer.nodeFinished(node, NodeExecutionStatus.WAITING, nodeResult, attempt, run.context);
                return;
            }

            if (nodeResult.isFailed()) {
                NodeExecutionStatus terminalStatus =
                        run.options.cancellationToken().isCancelled()
                                ? NodeExecutionStatus.CANCELLED
                                : NodeExecutionStatus.FAILED;
                if (!run.options.cancellationToken().isCancelled()) {
                    run.failure.compareAndSet(null, "Node " + node.getId() + " failed: " + nodeResult.getError());
                    run.options.cancellationToken().cancel(run.failure.get());
                }
                run.outcomes.put(node.getId(), NodeOutcome.executed(nodeResult.getHandle()));
                run.observer.nodeFinished(node, terminalStatus, nodeResult, attempt, run.context);
                return;
            }
            if (node.getType() == NodeType.END && nodeResult.getOutputs() != null) {
                run.endOutputs.putAll(nodeResult.getOutputs());
            }
            run.outcomes.put(node.getId(), NodeOutcome.executed(nodeResult.getHandle()));
            run.observer.nodeFinished(node, NodeExecutionStatus.COMPLETED, nodeResult, attempt, run.context);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            run.outcomes.put(node.getId(), NodeOutcome.skipped());
            if (!run.options.cancellationToken().isCancelled()) {
                run.failure.compareAndSet(null, "Node " + node.getId() + " was interrupted");
                run.options.cancellationToken().cancel(run.failure.get());
            }
        } catch (RuntimeException exception) {
            // Observer/checkpoint failures happen outside executeAttempt(). They are
            // infrastructure failures and must never be mistaken for a successful node.
            String message = "Node " + node.getId() + " infrastructure failure: " + safeMessage(exception);
            log.error(message, exception);
            run.failure.compareAndSet(null, message);
            run.options.cancellationToken().cancel(message);
            run.outcomes.put(node.getId(), NodeOutcome.skipped());
        } finally {
            if (permit) run.concurrency.release();
            nodeFuture.complete(null);
        }
    }

    private AttemptExecution executeAttempt(NodeDef node, NodeExecutor executor, RunState run) {
        long start = System.nanoTime();
        Instant startedAt = Instant.now();
        NodeResult result;
        Duration nodeTimeout = nodeTimeout(node, run.options.defaultNodeTimeout());
        Future<NodeResult> attempt = NODE_ATTEMPT_EXECUTOR.submit(() -> {
            try (RunCancellationToken.Registration ignored =
                    run.options.cancellationToken().registerCurrentThread()) {
                run.context.throwIfCancelled();
                io.aigoodle.common.context.CurrentUser caller = run.context.getExecutionUser();
                if (caller == null) {
                    caller = io.aigoodle.common.context.CurrentUser.builder()
                            .tenantId(run.context.getTenantId())
                            .userId(run.context.getUserId())
                            .build();
                }
                io.aigoodle.common.context.CurrentUser propagated = caller;
                return UserContextHolder.callAs(propagated, () -> executor.execute(node, run.context));
            }
        });
        try (RunCancellationToken.Registration ignored =
                run.options.cancellationToken().registerCurrentThread()) {
            result = attempt.get(nodeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            attempt.cancel(true);
            result = NodeResult.transientFailure("NODE_TIMEOUT", "Node deadline exceeded after " + nodeTimeout);
        } catch (InterruptedException interrupted) {
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            String message = run.options.cancellationToken().reason();
            result = NodeResult.permanentFailure(
                    "NODE_CANCELLED", message == null ? "Node execution interrupted" : message);
        } catch (ExecutionException execution) {
            Throwable failure = execution.getCause() == null ? execution : execution.getCause();
            log.error("Node {} ({}) failed: {}", node.getId(), node.getType(), failure.getMessage(), failure);
            result = NodeFailureClassifier.classify(failure);
        } finally {
            if (!attempt.isDone()) attempt.cancel(true);
        }
        return new AttemptExecution(result, (System.nanoTime() - start) / 1_000_000, startedAt, Instant.now());
    }

    private record AttemptExecution(NodeResult result, long elapsedMillis, Instant startedAt, Instant finishedAt) {}

    private static Duration nodeTimeout(NodeDef node, Duration defaultTimeout) {
        int configured = node.getInt("timeoutMillis", -1);
        return configured > 0 ? Duration.ofMillis(configured) : defaultTimeout;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static boolean anyIncomingFired(List<EdgeDef> incoming, Map<String, NodeOutcome> outcomes) {
        for (EdgeDef edge : incoming) {
            NodeOutcome parent = outcomes.get(edge.getSource());
            if (parent != null && parent.executed() && matches(edge, parent.handle())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<EdgeDef>> indexIncoming(WorkflowGraph graph) {
        Map<String, List<EdgeDef>> incoming = new HashMap<>();
        for (NodeDef node : graph.getNodes()) {
            incoming.put(node.getId(), new ArrayList<>());
        }
        for (EdgeDef edge : graph.getEdges()) {
            List<EdgeDef> incomingEdges = incoming.get(edge.getTarget());
            if (incomingEdges != null) {
                incomingEdges.add(edge);
            }
        }
        return incoming;
    }

    private static boolean matches(EdgeDef edge, String handle) {
        if (edge.getSourceHandle() == null) {
            return handle == null;
        }
        return edge.getSourceHandle().equals(handle);
    }

    private static void notifyStepListener(Consumer<StepRecord> listener, StepRecord step) {
        if (listener == null) {
            return;
        }
        try {
            listener.accept(step);
        } catch (Exception exception) {
            log.warn("Workflow step listener threw: {}", exception.getMessage());
        }
    }

    /** Whether a node executed (with the chosen handle) or was skipped due to a dead branch. */
    private record NodeOutcome(boolean executed, String handle) {
        static NodeOutcome skipped() {
            return new NodeOutcome(false, null);
        }

        static NodeOutcome executed(String handle) {
            return new NodeOutcome(true, handle);
        }
    }

    private static final class RunState {

        private final ExecutionContext context;
        private final Consumer<StepRecord> stepListener;
        private final Map<String, NodeOutcome> outcomes = new ConcurrentHashMap<>();
        private final Map<String, Object> endOutputs = new ConcurrentHashMap<>();
        private final AtomicInteger stepCount = new AtomicInteger();
        private final AtomicReference<String> failure = new AtomicReference<>();
        private final WorkflowRunOptions options;
        private final Semaphore concurrency;
        private final WorkflowResumeState resumeState;
        private final WorkflowExecutionObserver observer;
        private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
        private final AtomicReference<WorkflowWaitRequest> waitRequest = new AtomicReference<>();
        private final AtomicReference<String> waitingNodeId = new AtomicReference<>();
        private final Object listenerLock = new Object();

        private RunState(
                ExecutionContext context,
                Consumer<StepRecord> stepListener,
                WorkflowRunOptions options,
                WorkflowResumeState resumeState,
                WorkflowExecutionObserver observer) {
            this.context = context;
            this.stepListener = stepListener;
            this.options = options;
            this.concurrency = new Semaphore(options.maxConcurrency());
            this.resumeState = resumeState;
            this.observer = observer;
            this.attempts.putAll(resumeState.attempts());
        }

        private int nextAttempt(String nodeId) {
            return attempts.getOrDefault(nodeId, 0) + 1;
        }

        private int currentAttempt(String nodeId) {
            return attempts.getOrDefault(nodeId, 0);
        }

        private void record(
                NodeDef node,
                NodeResult result,
                long elapsedMillis,
                int attempt,
                Instant startedAt,
                Instant finishedAt) {
            context.getPool().putAll(node.getId(), result.getOutputs());
            synchronized (listenerLock) {
                StepRecord step = StepRecord.completed(node, result, elapsedMillis, attempt, startedAt, finishedAt);
                context.record(step);
                notifyStepListener(stepListener, step);
            }
        }
    }
}
