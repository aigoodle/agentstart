package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.util.JsonUtils;
import java.util.Map;

/** Parses a JSON object even when a model unnecessarily wraps it in a Markdown fence. */
final class JsonObjectResponseParser {

    private JsonObjectResponseParser() {}

    static Map<String, Object> parse(String response) {
        return JsonUtils.parseMap(withoutMarkdownFence(response));
    }

    private static String withoutMarkdownFence(String response) {
        if (response == null) {
            return null;
        }
        String trimmedResponse = response.trim();
        if (!trimmedResponse.startsWith("```")) {
            return trimmedResponse;
        }
        int contentStart = trimmedResponse.indexOf('\n');
        int closingFence = trimmedResponse.lastIndexOf("```");
        if (contentStart < 0 || closingFence <= contentStart) {
            return trimmedResponse;
        }
        return trimmedResponse.substring(contentStart + 1, closingFence).trim();
    }
}
