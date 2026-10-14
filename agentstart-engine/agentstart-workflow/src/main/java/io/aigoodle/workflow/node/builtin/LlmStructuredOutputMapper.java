package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.node.NodeResult;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Expands a JSON object response into workflow-addressable structured fields. */
final class LlmStructuredOutputMapper {

    private static final Logger log = LoggerFactory.getLogger(LlmStructuredOutputMapper.class);

    private LlmStructuredOutputMapper() {}

    static NodeResult map(String content) {
        NodeResult result = NodeResult.of("text", content);
        if (content == null || content.isBlank()) {
            return result;
        }

        try {
            Object parsedContent = JsonUtils.parse(content, Object.class);
            if (parsedContent instanceof Map<?, ?> structuredFields) {
                addStructuredFields(result, structuredFields);
            }
        } catch (Exception malformedJson) {
            // Structured mode is tolerant: providers may still return plain text.
            log.debug("LLM response was not a JSON object; preserving text output: {}", malformedJson.getMessage());
        }
        return result;
    }

    private static void addStructuredFields(NodeResult result, Map<?, ?> structuredFields) {
        result.output("struct", structuredFields);
        structuredFields.forEach((fieldName, value) -> result.output("struct." + fieldName, value));
    }
}
