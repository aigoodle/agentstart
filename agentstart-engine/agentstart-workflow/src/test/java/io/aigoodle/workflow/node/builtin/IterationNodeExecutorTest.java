package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IterationNodeExecutorTest {

    @Test
    void failsAtTheExactItemWhenSubRunFails() {
        WorkflowEngine engine = mock(WorkflowEngine.class);
        when(engine.run(
                        any(WorkflowGraph.class),
                        anyMap(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(io.aigoodle.workflow.engine.WorkflowRunOptions.class)))
                .thenReturn(success("first"))
                .thenReturn(failure("broken item"));
        IterationNodeExecutor executor = new IterationNodeExecutor(() -> engine);

        NodeResult result = executor.execute(iterationNode(false), contextWithItems());

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).isEqualTo("Iteration failed at index 1: broken item");
    }

    @Test
    void continueOnErrorKeepsOutputPositionsStable() {
        WorkflowEngine engine = mock(WorkflowEngine.class);
        when(engine.run(
                        any(WorkflowGraph.class),
                        anyMap(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(io.aigoodle.workflow.engine.WorkflowRunOptions.class)))
                .thenReturn(failure("first failed"))
                .thenReturn(success("second"));
        IterationNodeExecutor executor = new IterationNodeExecutor(() -> engine);

        NodeResult result = executor.execute(iterationNode(true), contextWithItems());

        assertThat(result.isFailed()).isFalse();
        assertThat(iterationOutputs(result)).containsExactly(null, Map.of("value", "second"));
    }

    @Test
    void resumesAtDurableCursorWithoutRepeatingCompletedItems() {
        WorkflowEngine engine = mock(WorkflowEngine.class);
        when(engine.run(
                        any(WorkflowGraph.class),
                        anyMap(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(io.aigoodle.workflow.engine.WorkflowRunOptions.class)))
                .thenReturn(success("second"));
        ExecutionContext context = contextWithItems();
        context.setIterationCursors(new java.util.concurrent.ConcurrentHashMap<>(
                Map.of("iteration", Map.of("nextIndex", 1, "outputs", List.of(Map.of("value", "first"))))));

        NodeResult result = new IterationNodeExecutor(() -> engine).execute(iterationNode(false), context);

        assertThat(iterationOutputs(result)).containsExactly(Map.of("value", "first"), Map.of("value", "second"));
        verify(engine, times(1))
                .run(
                        any(WorkflowGraph.class),
                        anyMap(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(io.aigoodle.workflow.engine.WorkflowRunOptions.class));
    }

    private static NodeDef iterationNode(boolean continueOnError) {
        WorkflowGraph subGraph = new WorkflowGraph();
        subGraph.addNode(NodeDef.of("start", NodeType.START));
        return NodeDef.of("iteration", NodeType.ITERATION)
                .with("inputList", "sys.items")
                .with("subGraph", subGraph)
                .with("continueOnError", continueOnError);
    }

    private static ExecutionContext contextWithItems() {
        return ExecutionContext.start(Map.of("items", List.of("first", "second")), null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> iterationOutputs(NodeResult result) {
        return (List<Object>) result.getOutputs().get("output");
    }

    private static WorkflowRunResult success(String value) {
        return WorkflowRunResult.forRun("run-success", new ArrayList<>()).succeed(Map.of("value", value));
    }

    private static WorkflowRunResult failure(String error) {
        return WorkflowRunResult.forRun("run-failure", new ArrayList<>()).fail(error, Map.of());
    }
}
