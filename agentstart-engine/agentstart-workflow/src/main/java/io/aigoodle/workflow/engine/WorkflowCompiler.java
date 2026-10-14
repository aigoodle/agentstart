package io.aigoodle.workflow.engine;

import io.aigoodle.workflow.graph.WorkflowGraph;

/** Validates and prepares an immutable execution view of a workflow graph. */
public final class WorkflowCompiler {

    private final GraphValidator validator;

    public WorkflowCompiler() {
        this(new GraphValidator());
    }

    public WorkflowCompiler(GraphValidator validator) {
        this.validator = validator;
    }

    public WorkflowGraph compile(WorkflowGraph graph) {
        validator.validate(graph);
        graph.reindex();
        return graph;
    }
}
