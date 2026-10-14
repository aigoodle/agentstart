package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;

/**
 * Renders a template against the variable pool. Config: {@code template} (required),
 * {@code outputKey} (default {@code output}).
 */
public class TemplateTransformNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.TEMPLATE_TRANSFORM;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        String template = node.getString("template", "");
        String renderedText = VariableResolver.render(template, context.getPool());
        return NodeResult.of(node.getString("outputKey", "output"), renderedText);
    }
}
