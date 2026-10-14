package io.aigoodle.completion.service;

import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.completion.dto.openai.OpenAIChoice;
import io.aigoodle.completion.dto.openai.OpenAIDelta;
import java.util.List;
import java.util.Map;

/** Reads a content delta from either the typed response or its map-shaped equivalent. */
final class OpenAIChunkContentExtractor {

    private OpenAIChunkContentExtractor() {}

    static String extract(Object eventData) {
        if (eventData instanceof OpenAIChatResponse response) {
            return fromTypedResponse(response);
        }
        if (eventData instanceof Map<?, ?> response) {
            return fromMap(response);
        }
        return null;
    }

    private static String fromTypedResponse(OpenAIChatResponse response) {
        List<OpenAIChoice> choices = response.getChoices();
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        OpenAIDelta delta = choices.get(0).getDelta();
        return delta == null ? null : delta.getContent();
    }

    private static String fromMap(Map<?, ?> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            return null;
        }
        Object deltaValue = choice.get("delta");
        if (!(deltaValue instanceof Map<?, ?> delta)) {
            return null;
        }
        Object content = delta.get("content");
        return content == null ? null : content.toString();
    }
}
