package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;

/** Renders the final user-visible answer of a workflow or chatflow. */
public class AnswerNodeExecutor implements NodeExecutor {

    private final AnswerTemplateRenderer templateRenderer = new AnswerTemplateRenderer();

    @Override
    public NodeType type() {
        return NodeType.ANSWER;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        String answerTemplate = node.getString("answer", node.getString("template", ""));
        String answer = templateRenderer.render(answerTemplate, context.getPool(), activeStreamSink(context));
        return NodeResult.of("answer", answer);
    }

    private static ChatStreamSink activeStreamSink(ExecutionContext context) {
        ChatStreamSink streamSink = context.getChatSink();
        return streamSink != null && streamSink.isStreaming() ? streamSink : null;
    }
}
