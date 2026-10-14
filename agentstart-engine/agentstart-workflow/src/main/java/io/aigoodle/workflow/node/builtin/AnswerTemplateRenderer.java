package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.chat.ChatFluxHandle;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.variable.VariablePool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders an answer template while forwarding stream-backed values token by token. */
final class AnswerTemplateRenderer {

    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\{\\{#\\s*([a-zA-Z0-9_\\-.]+)\\s*#}}");

    String render(String template, VariablePool variablePool, ChatStreamSink streamSink) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuilder answer = new StringBuilder();
        Matcher references = VARIABLE_REFERENCE.matcher(template);
        int renderedUntil = 0;
        while (references.find()) {
            appendLiteral(template, renderedUntil, references.start(), answer, streamSink);
            appendValue(variablePool.get(references.group(1)), answer, streamSink);
            renderedUntil = references.end();
        }
        appendLiteral(template, renderedUntil, template.length(), answer, streamSink);
        return answer.toString();
    }

    private static void appendLiteral(
            String template, int start, int end, StringBuilder answer, ChatStreamSink streamSink) {
        if (start >= end) {
            return;
        }
        appendAndPush(template.substring(start, end), answer, streamSink);
    }

    private static void appendValue(Object value, StringBuilder answer, ChatStreamSink streamSink) {
        if (value instanceof ChatFluxHandle streamHandle) {
            answer.append(consume(streamHandle, streamSink));
        } else if (value != null) {
            appendAndPush(String.valueOf(value), answer, streamSink);
        }
    }

    private static String consume(ChatFluxHandle streamHandle, ChatStreamSink streamSink) {
        if (streamSink == null) {
            return streamHandle.getFutureMessage();
        }
        streamHandle.stream().doOnNext(streamSink::push).blockLast();
        return streamHandle.snapshot();
    }

    private static void appendAndPush(String content, StringBuilder answer, ChatStreamSink streamSink) {
        answer.append(content);
        if (streamSink != null && !content.isEmpty()) {
            streamSink.push(content);
        }
    }
}
