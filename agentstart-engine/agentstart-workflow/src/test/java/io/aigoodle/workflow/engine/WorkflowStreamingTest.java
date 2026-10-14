package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link WorkflowEngine#run(WorkflowGraph, Map, String, java.util.function.Consumer)}
 * overload fires the listener once per node in the same order the engine visits them —
 * the guarantee the SSE endpoint relies on to look "live" to the user.
 */
class WorkflowStreamingTest {

    private WorkflowEngine engine() {
        List<NodeExecutor> execs =
                List.of(new StartNodeExecutor(), new EndNodeExecutor(), new TemplateTransformNodeExecutor());
        return new WorkflowEngine(new NodeExecutorRegistry(execs));
    }

    private WorkflowGraph threeNodeGraph() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("greet", NodeType.TEMPLATE_TRANSFORM)
                .with("template", "hi {{#start.name#}}")
                .with("outputKey", "msg"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("out", "{{#greet.msg#}}")));
        g.addEdge(EdgeDef.of("start", "greet"));
        g.addEdge(EdgeDef.of("greet", "end"));
        return g;
    }

    @Test
    void listenerReceivesEveryStepInOrder() {
        List<StepRecord> streamed = new ArrayList<>();
        WorkflowRunResult r = engine().run(threeNodeGraph(), Map.of("name", "Alice"), null, streamed::add);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals(3, streamed.size(), "should stream start, greet, end");
        assertEquals("start", streamed.get(0).getNodeId());
        assertEquals("greet", streamed.get(1).getNodeId());
        assertEquals("end", streamed.get(2).getNodeId());
        // and the streamed steps should be the same references as the final result's steps
        assertEquals(r.getSteps(), streamed);
    }

    @Test
    void nullListenerDoesNotBreakBackwardCompat() {
        WorkflowRunResult r = engine().run(threeNodeGraph(), Map.of("name", "Bob"), null, null);
        assertTrue(r.isSuccess());
        assertEquals("hi Bob", r.output("out"));
    }

    @Test
    void listenerExceptionsDoNotFailRun() {
        WorkflowRunResult r = engine().run(threeNodeGraph(), Map.of("name", "Zoe"), null, step -> {
            throw new RuntimeException("boom");
        });
        assertTrue(r.isSuccess(), "listener errors must not fail the run: " + r.getError());
    }
}
