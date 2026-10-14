package io.aigoodle.workflow.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aigoodle.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

/**
 * Guards the "opaque JSON save + tolerant deserialize" contract between the
 * visual designer and the workflow engine. The designer's canvas emits a heavy
 * VueFlow shape (per-node {@code dimensions}, {@code computedPosition},
 * {@code handleBounds}, per-edge {@code sourceNode}/{@code targetNode}
 * duplicates) and Dify-style alias node types (e.g. {@code CONDITION} instead
 * of the engine's {@code IF_ELSE}). This test uses a trimmed-down copy of a
 * real payload observed hitting {@code POST /api/v1/workflows} to make sure
 * both directions round-trip cleanly.
 */
class DesignerGraphParsingTest {

    /**
     * The real payload the frontend visual designer sends — including
     * uppercase types, a {@code CONDITION} alias, VueFlow runtime fields and
     * embedded {@code sourceNode}/{@code targetNode} on every edge. Parsing
     * this must succeed and the {@code CONDITION} node must land as
     * {@link NodeType#IF_ELSE}.
     */
    @Test
    void parsesDesignerPayloadWithAliasesAndExtraFields() {
        String designerJson =
                """
                {
                  "nodes": [
                    {"id":"1","type":"START","dimensions":{"width":300,"height":84},
                     "computedPosition":{"x":96,"y":96,"z":0},"selected":false,
                     "position":{"x":96,"y":96},
                     "data":{"label":"开始","mode":"WORKFLOW","variables":[]}},
                    {"id":"2","type":"LLM","dimensions":{"width":300,"height":142},
                     "position":{"x":496,"y":96},
                     "data":{"label":"LLM","prompt":""}},
                    {"id":"3","type":"END","dimensions":{"width":300,"height":136},
                     "position":{"x":896,"y":96},
                     "data":{"label":"结束","output":[]}},
                    {"id":"cond_1","type":"CONDITION","position":{"x":848,"y":-128},
                     "data":{"label":"条件分支","cases":[]}}
                  ],
                  "edges": [
                    {"id":"e1-2","type":"custom","source":"1","target":"2",
                     "animated":true,"style":{"stroke":"#6366f1"},
                     "sourceNode":{"id":"1","type":"start"},
                     "targetNode":{"id":"2","type":"llm"}},
                    {"id":"e2-3","type":"custom","source":"2","target":"3"},
                    {"id":"e2-cond","type":"custom","source":"2","target":"cond_1",
                     "sourceHandle":null,"targetHandle":null}
                  ]
                }
                """;

        WorkflowGraph graph = JsonUtils.parse(designerJson, WorkflowGraph.class);
        assertNotNull(graph);
        assertEquals(4, graph.getNodes().size());
        assertEquals(NodeType.START, graph.getNodes().get(0).getType());
        assertEquals(NodeType.LLM, graph.getNodes().get(1).getType());
        assertEquals(NodeType.END, graph.getNodes().get(2).getType());
        // The alias-mapping payoff: designer's CONDITION → engine's IF_ELSE.
        assertEquals(NodeType.IF_ELSE, graph.getNodes().get(3).getType());
        // Free-form node.data survives round-trip (needed by node executors).
        assertEquals("开始", graph.getNodes().get(0).getString("label"));
        assertEquals(3, graph.getEdges().size());
        assertEquals("1", graph.getEdges().get(0).getSource());
        assertEquals("2", graph.getEdges().get(0).getTarget());
    }

    /** Kebab-case, snake_case and mixed-case designer types all normalise. */
    @Test
    void nodeTypeAliasesAreCaseAndDelimiterInsensitive() {
        assertEquals(NodeType.IF_ELSE, NodeType.fromJson("condition"));
        assertEquals(NodeType.IF_ELSE, NodeType.fromJson("Condition"));
        assertEquals(NodeType.QUESTION_CLASSIFIER, NodeType.fromJson("classifier"));
        assertEquals(NodeType.HTTP_REQUEST, NodeType.fromJson("http"));
        assertEquals(NodeType.TEMPLATE_TRANSFORM, NodeType.fromJson("template"));
        assertEquals(NodeType.ITERATION, NodeType.fromJson("loop"));
        assertEquals(NodeType.START, NodeType.fromJson("user-input"));
        assertEquals(NodeType.START, NodeType.fromJson("USER_INPUT"));
        assertEquals(NodeType.LLM, NodeType.fromJson("llm"));
        assertEquals(NodeType.KNOWLEDGE_RETRIEVAL, NodeType.fromJson("knowledge_retrieval"));
        assertEquals(NodeType.KNOWLEDGE_RETRIEVAL, NodeType.fromJson("knowledge-retrieval"));
    }

    /** Genuinely unknown types must not silently degrade — surface a clear error. */
    @Test
    void unknownNodeTypeThrowsClearError() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> NodeType.fromJson("teleport_via_wormhole"));
        assert ex.getMessage().contains("teleport_via_wormhole");
    }
}
