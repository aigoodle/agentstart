package io.aigoodle.workflow.graph;

import lombok.Data;

/**
 * A directed edge. {@code sourceHandle} selects which branch of a multi-output node
 * (e.g. IF_ELSE's {@code true}/{@code false}) this edge belongs to; null means the
 * node's default/only output.
 */
@Data
public class EdgeDef {

    private String source;
    private String target;
    private String sourceHandle;

    public static EdgeDef of(String source, String target) {
        EdgeDef edge = new EdgeDef();
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

    public static EdgeDef of(String source, String target, String sourceHandle) {
        EdgeDef edge = of(source, target);
        edge.setSourceHandle(sourceHandle);
        return edge;
    }
}
