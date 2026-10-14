package io.aigoodle.workflow.service;

import io.aigoodle.common.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Produces bounded, redacted observability summaries; never used for recovery state. */
final class WorkflowDataSanitizer {
    private static final int MAX_TEXT = 512;
    private static final int MAX_COLLECTION = 20;
    private static final Set<String> SECRET_MARKERS =
            Set.of("password", "secret", "token", "authorization", "cookie", "api_key", "apikey", "credential");

    private WorkflowDataSanitizer() {}

    static String summary(Object value) {
        String json = JsonUtils.toJson(sanitize(value, 0));
        return json.length() <= 2048 ? json : json.substring(0, 2048) + "…";
    }

    static Object sanitize(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (depth >= 4) return "[max-depth]";
        if (value instanceof CharSequence text) {
            return text.length() <= MAX_TEXT ? text.toString() : text.subSequence(0, MAX_TEXT) + "…";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION) {
                    clean.put("_truncated", true);
                    break;
                }
                String key = String.valueOf(entry.getKey());
                clean.put(key, secret(key) ? "[REDACTED]" : sanitize(entry.getValue(), depth + 1));
            }
            return clean;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> clean = new java.util.ArrayList<>();
            for (Object item : iterable) {
                if (clean.size() >= MAX_COLLECTION) {
                    clean.add("[truncated]");
                    break;
                }
                clean.add(sanitize(item, depth + 1));
            }
            return List.copyOf(clean);
        }
        return "[" + value.getClass().getSimpleName() + "]";
    }

    private static boolean secret(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return SECRET_MARKERS.stream().anyMatch(normalized::contains);
    }
}
