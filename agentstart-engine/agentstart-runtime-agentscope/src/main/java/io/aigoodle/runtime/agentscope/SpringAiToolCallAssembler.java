package io.aigoodle.runtime.agentscope;

import io.aigoodle.common.util.JsonUtils;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;

/** Correlates provider argument fragments and rejects malformed JSON before SDK execution. */
final class SpringAiToolCallAssembler {
    private SpringAiToolCallAssembler() {}

    static List<AssistantMessage.ToolCall> mergeCalls(List<AssistantMessage.ToolCall> fragments) {
        var calls = new java.util.LinkedHashMap<String, AssistantMessage.ToolCall>();
        String lastId = null;
        for (var fragment : fragments) {
            String id = fragment.id();
            if (id == null || id.isBlank()) {
                if (fragment.name() != null && !fragment.name().isBlank()) {
                    throw new IllegalArgumentException("Tool call is missing its correlation ID");
                }
                if (lastId == null) throw new IllegalArgumentException("Tool argument fragment has no preceding call");
                if (calls.size() != 1)
                    throw new IllegalArgumentException("Ambiguous tool fragment without correlation ID");
                id = lastId;
            }
            lastId = id;
            var previous = calls.get(id);
            String name = fragment.name();
            if (previous != null) {
                if (name != null && !name.isBlank() && !name.equals(previous.name())) {
                    throw new IllegalArgumentException("Conflicting tool names for call " + id);
                }
                name = previous.name();
            }
            String arguments = fragment.arguments() == null ? "" : fragment.arguments();
            if (previous != null) {
                boolean duplicateComplete = arguments.equals(previous.arguments()) && completeObject(arguments);
                if (!duplicateComplete) arguments = previous.arguments() + arguments;
            }
            calls.put(id, new AssistantMessage.ToolCall(id, "function", name, arguments));
        }
        return List.copyOf(calls.values());
    }

    private static boolean completeObject(String value) {
        if (value.isBlank()) return false;
        try {
            strictArguments(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static java.util.Map<String, Object> strictArguments(String value) {
        if (value == null || value.isBlank()) return java.util.Map.of();
        try {
            java.util.Map<String, Object> arguments = JsonUtils.mapper()
                    .readerFor(new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {})
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(value);
            if (arguments == null) throw new IllegalArgumentException("Tool arguments must be a JSON object");
            return arguments;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Tool arguments must be exactly one JSON object", exception);
        }
    }
}
