package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    @Test
    void acceptsValidDagAndUpstreamVariableReference() {
        WorkflowGraph graph = chain();
        graph.node("end").with("outputs", Map.of("answer", "{{#work.value#}}"));
        assertDoesNotThrow(() -> validator.validate(graph));
    }

    @Test
    void acceptsDesignerVarNamespace() {
        WorkflowGraph graph = chain();
        graph.node("work").with("template", "{{#var.query#}}");
        assertDoesNotThrow(() -> validator.validate(graph));
    }

    @Test
    void rejectsCycleBeforeExecutionCanWaitForever() {
        WorkflowGraph graph = chain();
        graph.addEdge(EdgeDef.of("work", "work"));
        assertThrows(PlatformException.class, () -> validator.validate(graph));
    }

    @Test
    void rejectsUnreachableNode() {
        WorkflowGraph graph = chain();
        graph.addNode(NodeDef.of("orphan", NodeType.TEMPLATE_TRANSFORM));
        assertThrows(PlatformException.class, () -> validator.validate(graph));
    }

    @Test
    void rejectsMissingEndpointAndDuplicateId() {
        WorkflowGraph missing = chain();
        missing.addEdge(EdgeDef.of("missing", "end"));
        assertThrows(PlatformException.class, () -> validator.validate(missing));

        WorkflowGraph duplicate = chain();
        duplicate.addNode(NodeDef.of("work", NodeType.ANSWER));
        assertThrows(PlatformException.class, () -> validator.validate(duplicate));
    }

    @Test
    void rejectsVariableReferenceToNonUpstreamNode() {
        WorkflowGraph graph = chain();
        graph.node("work").with("template", "{{#end.answer#}}");
        assertThrows(PlatformException.class, () -> validator.validate(graph));
    }

    @Test
    void requiresHandlesOnlyForBranchingNodes() {
        WorkflowGraph graph = chain();
        graph.getEdges().getFirst().setSourceHandle("true");
        assertThrows(PlatformException.class, () -> validator.validate(graph));
    }

    @Test
    void validatesSchedulerCriticalNodeConfiguration() {
        WorkflowGraph missingCorrelation = chainWith(NodeDef.of("wait", NodeType.WAIT_EVENT));
        assertThrows(PlatformException.class, () -> validator.validate(missingCorrelation));

        WorkflowGraph invalidSleep =
                chainWith(NodeDef.of("wait", NodeType.SLEEP_UNTIL).with("delayMillis", 0));
        assertThrows(PlatformException.class, () -> validator.validate(invalidSleep));

        WorkflowGraph validWait = chainWith(NodeDef.of("wait", NodeType.WAIT_EVENT)
                .with("correlationKey", "order-42")
                .with("timeoutMillis", 1000));
        assertDoesNotThrow(() -> validator.validate(validWait));
    }

    @Test
    void rejectsBranchHandleThatExecutorCanNeverProduce() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("branch", NodeType.IF_ELSE));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "branch"));
        EdgeDef edge = EdgeDef.of("branch", "end");
        edge.setSourceHandle("typo");
        graph.addEdge(edge);
        assertThrows(PlatformException.class, () -> validator.validate(graph));
    }

    private static WorkflowGraph chainWith(NodeDef middle) {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(middle);
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", middle.getId()));
        graph.addEdge(EdgeDef.of(middle.getId(), "end"));
        return graph;
    }

    private static WorkflowGraph chain() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("work", NodeType.TEMPLATE_TRANSFORM));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "work"));
        graph.addEdge(EdgeDef.of("work", "end"));
        return graph;
    }
}
