package io.aigoodle.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.WorkflowGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Converts persisted designer JSON into the typed graph used by the runtime. */
final class WorkflowGraphCodec {

    WorkflowGraph read(JsonNode graphDefinition) {
        if (graphDefinition == null || graphDefinition.isNull()) {
            throw new PlatformException("graph_required", "Workflow graph is required", null);
        }
        try {
            WorkflowGraph graph = JsonUtils.parse(graphDefinition.toString(), WorkflowGraph.class);
            removeDuplicateEdges(graph);
            graph.reindex();
            return graph;
        } catch (Exception exception) {
            throw new PlatformException(
                    "graph_parse_error", "Could not parse graph: " + exception.getMessage(), exception);
        }
    }

    /** Normalizes exact duplicate edges left by older designers. */
    private static void removeDuplicateEdges(WorkflowGraph graph) {
        if (graph.getEdges() == null || graph.getEdges().isEmpty()) return;
        Set<String> seen = new HashSet<>();
        var unique = new ArrayList<EdgeDef>();
        for (EdgeDef edge : graph.getEdges()) {
            if (edge == null) {
                unique.add(null);
                continue;
            }
            String key = edge.getSource() + "\u0000" + edge.getTarget() + "\u0000"
                    + Objects.toString(edge.getSourceHandle(), "");
            if (seen.add(key)) unique.add(edge);
        }
        graph.setEdges(unique);
    }

    JsonNode write(WorkflowGraph graph) {
        return JsonUtils.mapper().valueToTree(Objects.requireNonNull(graph, "graph must not be null"));
    }

    JsonNode emptyGraph() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(io.aigoodle.workflow.graph.NodeDef.of("start", io.aigoodle.workflow.graph.NodeType.START));
        graph.addNode(io.aigoodle.workflow.graph.NodeDef.of("end", io.aigoodle.workflow.graph.NodeType.END));
        graph.addEdge(io.aigoodle.workflow.graph.EdgeDef.of("start", "end"));
        return write(graph);
    }
}
