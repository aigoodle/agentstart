package io.aigoodle.workflow.graph;

import io.aigoodle.common.exception.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * A workflow definition: nodes + edges, with convenient lookups used by the engine.
 */
@Data
public class WorkflowGraph {

    private List<NodeDef> nodes = new ArrayList<>();
    private List<EdgeDef> edges = new ArrayList<>();

    private final transient Map<String, NodeDef> index = new LinkedHashMap<>();

    public void reindex() {
        index.clear();
        for (NodeDef n : nodes) {
            index.put(n.getId(), n);
        }
    }

    public NodeDef node(String id) {
        if (index.isEmpty() && !nodes.isEmpty()) {
            reindex();
        }
        NodeDef n = index.get(id);
        if (n == null) {
            throw new PlatformException("node_not_found", "No node with id " + id, null);
        }
        return n;
    }

    public NodeDef startNode() {
        return nodes.stream()
                .filter(n -> n.getType() == NodeType.START)
                .findFirst()
                .orElseThrow(() -> new PlatformException("no_start_node", "Workflow has no START node", null));
    }

    public List<EdgeDef> outgoing(String nodeId) {
        return edges.stream().filter(e -> e.getSource().equals(nodeId)).toList();
    }

    public WorkflowGraph addNode(NodeDef node) {
        nodes.add(node);
        index.put(node.getId(), node);
        return this;
    }

    public WorkflowGraph addEdge(EdgeDef edge) {
        edges.add(edge);
        return this;
    }
}
