package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs a sub-workflow once per element of a list variable. Config:
 * <ul>
 *   <li>{@code inputList} — dotted pool path resolving to a {@code List}</li>
 *   <li>{@code subGraph} — an inline {@link WorkflowGraph} definition (as a Map)</li>
 *   <li>{@code itemKey} / {@code indexKey} — names used to expose the current item /
 *       zero-based index to the sub-graph via its {@code sys} namespace
 *       (defaults: {@code item} / {@code index})</li>
 *   <li>{@code outputKey} — where to store the aggregated list of sub-run outputs
 *       (default {@code output})</li>
 * </ul>
 * A failed sub-run fails the iteration node; use {@code continueOnError=true} to
 * keep going and collect nulls in the aggregated output.
 * <p>
 * WorkflowEngine is injected lazily to break the DI cycle (executors are collected
 * into the registry the engine uses).
 */
public class IterationNodeExecutor implements NodeExecutor {

    private final Supplier<WorkflowEngine> engineSupplier;

    public IterationNodeExecutor(Supplier<WorkflowEngine> engineSupplier) {
        this.engineSupplier = engineSupplier;
    }

    @Override
    public NodeType type() {
        return NodeType.ITERATION;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        Object inputValue = resolveInputValue(node, context);
        if (!(inputValue instanceof List<?> items)) {
            return NodeResult.failure("Iteration 'inputList' must resolve to a list");
        }

        WorkflowGraph subGraph = parseSubGraph(node.get("subGraph"));
        if (subGraph == null || subGraph.getNodes().isEmpty()) {
            return NodeResult.failure("Iteration requires a non-empty 'subGraph'");
        }

        IterationConfiguration configuration = IterationConfiguration.from(node);
        WorkflowEngine workflowEngine = engineSupplier.get();
        List<Object> collectedOutputs = new ArrayList<>(items.size());
        int startIndex = 0;
        Object durableCursor = context.iterationCursor(node.getId());
        if (durableCursor instanceof Map<?, ?> cursor) {
            Object nextIndex = cursor.get("nextIndex");
            if (nextIndex instanceof Number number) startIndex = Math.max(0, number.intValue());
            Object priorOutputs = cursor.get("outputs");
            if (priorOutputs instanceof List<?> list) collectedOutputs.addAll(list);
        }
        for (int index = startIndex; index < items.size(); index++) {
            context.throwIfCancelled();
            Map<String, Object> iterationInputs = configuration.inputsFor(items.get(index), index);
            WorkflowRunResult iterationResult = workflowEngine.run(
                    subGraph,
                    iterationInputs,
                    context.getConversationId(),
                    null,
                    null,
                    context.getTenantId(),
                    io.aigoodle.workflow.engine.WorkflowRunOptions.defaults()
                            .withResourceTenantId(context.resourceTenant())
                            .withAppId(context.getAppId()));
            if (iterationResult.isSuccess()) {
                collectedOutputs.add(iterationResult.getOutputs());
                context.checkpointIteration(
                        node.getId(), Map.of("nextIndex", index + 1, "outputs", new ArrayList<>(collectedOutputs)));
                continue;
            }
            if (configuration.continueOnError()) {
                collectedOutputs.add(null);
                context.checkpointIteration(
                        node.getId(), Map.of("nextIndex", index + 1, "outputs", new ArrayList<>(collectedOutputs)));
                continue;
            }
            return NodeResult.failure("Iteration failed at index " + index + ": " + iterationResult.getError());
        }
        return NodeResult.of(configuration.outputVariable(), collectedOutputs);
    }

    private static Object resolveInputValue(NodeDef node, ExecutionContext context) {
        String inputReference = node.getString("inputList");
        return inputReference == null ? null : context.getPool().get(inputReference);
    }

    private static WorkflowGraph parseSubGraph(Object configuredGraph) {
        if (configuredGraph == null) {
            return null;
        }
        if (configuredGraph instanceof WorkflowGraph graph) {
            return graph;
        }
        // Config maps come from JSON, so round-trip through JsonUtils to bind the shape.
        String graphJson = configuredGraph instanceof String json ? json : JsonUtils.toJson(configuredGraph);
        return JsonUtils.parse(graphJson, WorkflowGraph.class);
    }
}
