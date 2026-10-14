package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkflowRunControlTest {

    @Test
    void workflowDeadlineInterruptsNodeAndPreventsDownstreamExecution() {
        AtomicBoolean downstream = new AtomicBoolean();
        WorkflowEngine engine = engine(new ControlledExecutor(downstream, null));
        WorkflowRunResult result = run(
                engine,
                linear("sleep", "mark"),
                options(Duration.ofMillis(75), Duration.ofSeconds(5), 4, new RunCancellationToken()));

        assertEquals(WorkflowRunStatus.TIMED_OUT, result.getStatus());
        assertFalse(downstream.get());
    }

    @Test
    void externalCancellationInterruptsRunningNode() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        RunCancellationToken token = new RunCancellationToken();
        WorkflowEngine engine = engine(new ControlledExecutor(new AtomicBoolean(), started));
        CompletableFuture<WorkflowRunResult> future = CompletableFuture.supplyAsync(() ->
                run(engine, linear("sleep", "mark"), options(Duration.ofSeconds(5), Duration.ofSeconds(5), 4, token)));

        assertTrue(started.await(1, TimeUnit.SECONDS));
        token.cancel("stopped by user");
        WorkflowRunResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals(WorkflowRunStatus.CANCELLED, result.getStatus());
        assertEquals("stopped by user", result.getError());
    }

    @Test
    void failedParallelBranchCooperativelyInterruptsSibling() {
        CountDownLatch slowStarted = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        WorkflowEngine engine = engine(new ParallelExecutor(slowStarted, interrupted));
        WorkflowRunResult result = run(
                engine,
                parallelFailure(),
                options(Duration.ofSeconds(5), Duration.ofSeconds(5), 4, new RunCancellationToken()));

        assertEquals(WorkflowRunStatus.FAILED, result.getStatus());
        assertTrue(interrupted.get(), "already-running sibling should receive interruption");
    }

    @Test
    void nodeDeadlineFailsRunAndRecordsAttemptAndTimestamps() {
        WorkflowEngine engine = engine(new ControlledExecutor(new AtomicBoolean(), null));
        WorkflowGraph graph = linear("sleep", "mark");
        graph.node("sleep").with("timeoutMillis", 50);
        WorkflowRunResult result = run(
                engine, graph, options(Duration.ofSeconds(5), Duration.ofSeconds(5), 2, new RunCancellationToken()));

        assertEquals(WorkflowRunStatus.FAILED, result.getStatus());
        assertEquals(1, result.getSteps().get(1).getAttempt());
        assertNotNull(result.getSteps().get(1).getStartedAt());
        assertNotNull(result.getSteps().get(1).getFinishedAt());
    }

    @Test
    void nodeDeadlineReturnsEvenWhenExecutorIgnoresInterrupts() {
        AtomicBoolean release = new AtomicBoolean();
        NodeExecutor uncooperative = new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                while (!release.get()) Thread.onSpinWait();
                return NodeResult.of("value", "late");
            }
        };
        WorkflowGraph graph = linear("work", "mark");
        graph.node("work").with("timeoutMillis", 50);
        long started = System.nanoTime();
        try {
            WorkflowRunResult result = run(
                    engine(uncooperative),
                    graph,
                    options(Duration.ofSeconds(2), Duration.ofSeconds(1), 2, new RunCancellationToken()));
            assertEquals(WorkflowRunStatus.FAILED, result.getStatus());
            assertTrue(
                    Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000,
                    "non-cooperative executor must not hold the workflow open");
        } finally {
            release.set(true);
        }
    }

    @Test
    void boundsConcurrentNodeExecutions() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        NodeExecutor executor = new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                active.decrementAndGet();
                return NodeResult.of("value", node.getId());
            }
        };
        WorkflowEngine engine = engine(executor);
        WorkflowGraph graph = fanOut(12);
        WorkflowRunResult result = run(
                engine, graph, options(Duration.ofSeconds(5), Duration.ofSeconds(2), 3, new RunCancellationToken()));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(peak.get() <= 3, "peak concurrency was " + peak.get());
    }

    @Test
    void retriesNodeWithStableAttemptNumbersBeforeSchedulingDownstream() {
        AtomicInteger calls = new AtomicInteger();
        NodeExecutor retrying = new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                if ("work".equals(node.getId()) && calls.incrementAndGet() == 1) {
                    return NodeResult.failure("transient");
                }
                return NodeResult.of("value", "ok");
            }
        };
        WorkflowGraph graph = linear("work", "mark");
        graph.node("work").with("maxAttempts", 3).with("retryBackoffMillis", 1);

        WorkflowRunResult result = run(
                engine(retrying),
                graph,
                options(Duration.ofSeconds(5), Duration.ofSeconds(2), 2, new RunCancellationToken()));

        assertTrue(result.isSuccess(), result.getError());
        assertEquals(2, calls.get());
        assertEquals(
                List.of(1, 2),
                result.getSteps().stream()
                        .filter(step -> step.getNodeId().equals("work"))
                        .map(step -> step.getAttempt())
                        .toList());
    }

    @Test
    void permanentFailureIsNotRetriedEvenWhenAttemptsAreConfigured() {
        AtomicInteger calls = new AtomicInteger();
        NodeExecutor invalid = new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                calls.incrementAndGet();
                return NodeResult.permanentFailure("INVALID_CONFIG", "bad template");
            }
        };
        WorkflowGraph graph = linear("work", "mark");
        graph.node("work").with("maxAttempts", 5);

        WorkflowRunResult result = run(
                engine(invalid),
                graph,
                options(Duration.ofSeconds(5), Duration.ofSeconds(2), 2, new RunCancellationToken()));

        assertEquals(WorkflowRunStatus.FAILED, result.getStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void observerFailureCannotBeReportedAsSuccessfulRun() {
        WorkflowEngine engine = engine(new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                return NodeResult.of("value", "ok");
            }
        });
        WorkflowExecutionObserver brokenPersistence = new WorkflowExecutionObserver() {
            @Override
            public void nodeStarted(NodeDef node, int attempt, ExecutionContext context) {
                if ("work".equals(node.getId())) throw new IllegalStateException("database unavailable");
            }
        };

        WorkflowRunResult result = engine.run(
                linear("work", "mark"),
                Map.of(),
                null,
                null,
                null,
                "test",
                options(Duration.ofSeconds(5), Duration.ofSeconds(2), 2, new RunCancellationToken()),
                WorkflowResumeState.empty(),
                brokenPersistence);

        assertEquals(WorkflowRunStatus.FAILED, result.getStatus());
        assertTrue(result.getError().contains("database unavailable"), result.getError());
    }

    @Test
    void retryStormRemainsInsideConfiguredConcurrencyAndAttemptBudgets() {
        java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> attempts =
                new java.util.concurrent.ConcurrentHashMap<>();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        NodeExecutor transientWork = new NodeExecutor() {
            public NodeType type() {
                return NodeType.TEMPLATE_TRANSFORM;
            }

            public NodeResult execute(NodeDef node, ExecutionContext context) {
                int running = active.incrementAndGet();
                peak.accumulateAndGet(running, Math::max);
                try {
                    int attempt = attempts.computeIfAbsent(node.getId(), ignored -> new AtomicInteger())
                            .incrementAndGet();
                    return attempt < 3
                            ? NodeResult.transientFailure("OVERLOADED", "try later")
                            : NodeResult.of("value", "ok");
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        WorkflowGraph graph = fanOut(100);
        graph.getNodes().stream()
                .filter(node -> node.getType() == NodeType.TEMPLATE_TRANSFORM)
                .forEach(node -> node.with("maxAttempts", 3).with("retryBackoffMillis", 1));

        WorkflowRunResult result = run(
                engine(transientWork),
                graph,
                options(Duration.ofSeconds(10), Duration.ofSeconds(2), 8, new RunCancellationToken()));

        assertTrue(result.isSuccess(), result.getError());
        assertEquals(
                300, attempts.values().stream().mapToInt(AtomicInteger::get).sum());
        assertTrue(peak.get() <= 8, "retry storm exceeded concurrency limit: " + peak.get());
    }

    private static WorkflowRunResult run(WorkflowEngine engine, WorkflowGraph graph, WorkflowRunOptions options) {
        return engine.run(graph, Map.of(), null, null, null, "test", options);
    }

    private static WorkflowRunOptions options(
            Duration workflow, Duration node, int concurrency, RunCancellationToken token) {
        return new WorkflowRunOptions(workflow, node, concurrency, token);
    }

    private static WorkflowEngine engine(NodeExecutor executor) {
        return new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor(), executor)));
    }

    private static WorkflowGraph linear(String first, String second) {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of(first, NodeType.TEMPLATE_TRANSFORM).with("action", first));
        graph.addNode(NodeDef.of(second, NodeType.TEMPLATE_TRANSFORM).with("action", second));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", first));
        graph.addEdge(EdgeDef.of(first, second));
        graph.addEdge(EdgeDef.of(second, "end"));
        return graph;
    }

    private static WorkflowGraph parallelFailure() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("slow", NodeType.TEMPLATE_TRANSFORM).with("action", "slow"));
        graph.addNode(NodeDef.of("fail", NodeType.TEMPLATE_TRANSFORM).with("action", "fail"));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "slow"));
        graph.addEdge(EdgeDef.of("start", "fail"));
        graph.addEdge(EdgeDef.of("slow", "end"));
        graph.addEdge(EdgeDef.of("fail", "end"));
        return graph;
    }

    private static WorkflowGraph fanOut(int count) {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("end", NodeType.END));
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String id = "node-" + index;
            ids.add(id);
            graph.addNode(NodeDef.of(id, NodeType.TEMPLATE_TRANSFORM));
            graph.addEdge(EdgeDef.of("start", id));
            graph.addEdge(EdgeDef.of(id, "end"));
        }
        return graph;
    }

    private static final class ControlledExecutor implements NodeExecutor {
        private final AtomicBoolean downstream;
        private final CountDownLatch started;

        private ControlledExecutor(AtomicBoolean downstream, CountDownLatch started) {
            this.downstream = downstream;
            this.started = started;
        }

        public NodeType type() {
            return NodeType.TEMPLATE_TRANSFORM;
        }

        public NodeResult execute(NodeDef node, ExecutionContext context) {
            if ("mark".equals(node.getString("action"))) {
                downstream.set(true);
                return NodeResult.empty();
            }
            if (started != null) started.countDown();
            try {
                Thread.sleep(10_000);
                return NodeResult.empty();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
        }
    }

    private static final class ParallelExecutor implements NodeExecutor {
        private final CountDownLatch slowStarted;
        private final AtomicBoolean interrupted;

        private ParallelExecutor(CountDownLatch slowStarted, AtomicBoolean interrupted) {
            this.slowStarted = slowStarted;
            this.interrupted = interrupted;
        }

        public NodeType type() {
            return NodeType.TEMPLATE_TRANSFORM;
        }

        public NodeResult execute(NodeDef node, ExecutionContext context) {
            if ("fail".equals(node.getString("action"))) {
                try {
                    slowStarted.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return NodeResult.failure("boom");
            }
            slowStarted.countDown();
            try {
                Thread.sleep(10_000);
                return NodeResult.empty();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
        }
    }
}
