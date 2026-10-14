package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.builtin.AnswerNodeExecutor;
import io.aigoodle.workflow.node.builtin.CodeNodeExecutor;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.IterationNodeExecutor;
import io.aigoodle.workflow.node.builtin.ListOperatorNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor;
import io.aigoodle.workflow.node.builtin.VariableAssignerNodeExecutor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Exercises the newly added executors — assigner, answer, code, list-operator,
 * iteration — end-to-end through the engine. No external services required.
 */
class NewNodesTest {

    private WorkflowEngine buildEngine() {
        AtomicReference<WorkflowEngine> ref = new AtomicReference<>();
        List<NodeExecutor> executors = List.of(
                new StartNodeExecutor(),
                new EndNodeExecutor(),
                new AnswerNodeExecutor(),
                new TemplateTransformNodeExecutor(),
                new VariableAssignerNodeExecutor(),
                new ListOperatorNodeExecutor(),
                new CodeNodeExecutor(),
                new IterationNodeExecutor(ref::get));
        WorkflowEngine engine = new WorkflowEngine(new NodeExecutorRegistry(executors));
        ref.set(engine);
        return engine;
    }

    @Test
    void assignerCopiesValuesAndAnswerRenders() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("assign", NodeType.VARIABLE_ASSIGNER)
                .with(
                        "assignments",
                        List.of(
                                Map.of("name", "greeting", "value", "hi {{#sys.name#}}"),
                                Map.of("name", "count", "value", 3))));
        g.addNode(NodeDef.of("say", NodeType.ANSWER).with("answer", "{{#assign.greeting#}} x{{#assign.count#}}"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("out", "{{#say.answer#}}")));
        g.addEdge(EdgeDef.of("start", "assign"));
        g.addEdge(EdgeDef.of("assign", "say"));
        g.addEdge(EdgeDef.of("say", "end"));

        WorkflowRunResult r = buildEngine().run(g, Map.of("name", "Alice"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("hi Alice x3", r.output("out"));
    }

    @Test
    void listOperatorLimitsAndSortsMaps() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("seed", NodeType.VARIABLE_ASSIGNER)
                .with(
                        "assignments",
                        List.of(Map.of(
                                "name",
                                "items",
                                "value",
                                List.of(
                                        Map.of("id", "b", "score", 2),
                                        Map.of("id", "c", "score", 3),
                                        Map.of("id", "a", "score", 1))))));
        g.addNode(NodeDef.of("sortDesc", NodeType.LIST_OPERATOR)
                .with("inputList", "seed.items")
                .with("operation", "sort")
                .with("field", "score")
                .with("order", "desc"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("first", "{{#sortDesc.result#}}")));
        g.addEdge(EdgeDef.of("start", "seed"));
        g.addEdge(EdgeDef.of("seed", "sortDesc"));
        g.addEdge(EdgeDef.of("sortDesc", "end"));

        WorkflowRunResult r = buildEngine().run(g, Map.of(), null);
        assertTrue(r.isSuccess(), r.getError());
        String rendered = String.valueOf(r.output("first"));
        // Sort desc by score: c(3), b(2), a(1)
        assertTrue(rendered.indexOf("c") < rendered.indexOf("b"), rendered);
        assertTrue(rendered.indexOf("b") < rendered.indexOf("a"), rendered);
    }

    @Test
    void codeNodeEvaluatesJexlAgainstPool() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("calc", NodeType.CODE)
                .with("code", "sys.a * 2 + sys.b")
                .with("outputKey", "value"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("total", "{{#calc.value#}}")));
        g.addEdge(EdgeDef.of("start", "calc"));
        g.addEdge(EdgeDef.of("calc", "end"));

        WorkflowRunResult r = buildEngine().run(g, Map.of("a", 5, "b", 3), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("13", String.valueOf(r.output("total")));
    }

    @Test
    void iterationRunsSubGraphPerItem() {
        WorkflowGraph iterationGraph = new WorkflowGraph();
        iterationGraph.addNode(NodeDef.of("iteration-start", NodeType.START));
        iterationGraph.addNode(NodeDef.of("greeting", NodeType.TEMPLATE_TRANSFORM)
                .with("template", "{{#sys.position#}}: hello {{#sys.customer#}}")
                .with("outputKey", "line"));
        iterationGraph.addNode(
                NodeDef.of("iteration-end", NodeType.END).with("outputs", Map.of("line", "{{#greeting.line#}}")));
        iterationGraph.addEdge(EdgeDef.of("iteration-start", "greeting"));
        iterationGraph.addEdge(EdgeDef.of("greeting", "iteration-end"));

        WorkflowGraph workflow = new WorkflowGraph();
        workflow.addNode(NodeDef.of("start", NodeType.START));
        workflow.addNode(NodeDef.of("names", NodeType.VARIABLE_ASSIGNER)
                .with("assignments", List.of(Map.of("name", "list", "value", List.of("Alice", "Bob")))));
        workflow.addNode(NodeDef.of("loop", NodeType.ITERATION)
                .with("inputList", "names.list")
                .with("subGraph", iterationGraph)
                .with("itemKey", "customer")
                .with("indexKey", "position")
                .with("outputKey", "lines"));
        workflow.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("all", "{{#loop.lines#}}")));
        workflow.addEdge(EdgeDef.of("start", "names"));
        workflow.addEdge(EdgeDef.of("names", "loop"));
        workflow.addEdge(EdgeDef.of("loop", "end"));

        WorkflowRunResult result = buildEngine().run(workflow, Map.of(), null);
        assertTrue(result.isSuccess(), result.getError());
        String collectedGreetings = String.valueOf(result.output("all"));
        assertTrue(collectedGreetings.contains("0: hello Alice"), collectedGreetings);
        assertTrue(collectedGreetings.contains("1: hello Bob"), collectedGreetings);
    }

    @Test
    void codeNodeFailsGracefullyOnBadCode() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("bad", NodeType.CODE).with("code", ""));
        g.addNode(NodeDef.of("end", NodeType.END));
        g.addEdge(EdgeDef.of("start", "bad"));
        g.addEdge(EdgeDef.of("bad", "end"));

        WorkflowRunResult r = buildEngine().run(g, Map.of(), null);
        assertFalse(r.isSuccess());
    }
}
