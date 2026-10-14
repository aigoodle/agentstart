package io.aigoodle.trigger.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes one request to invoke a trigger.
 *
 * <p>The named factories keep transport-specific source names out of callers and make
 * the invocation API harder to misuse than three adjacent parameters.</p>
 */
public record TriggerInvocationRequest(
        String triggerId, Map<String, Object> payload, String source, String conversationId) {

    public TriggerInvocationRequest {
        if (triggerId == null || triggerId.isBlank()) {
            throw new IllegalArgumentException("triggerId must not be blank");
        }
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        source = source == null || source.isBlank() ? "manual" : source;
    }

    public static TriggerInvocationRequest manual(String triggerId, Map<String, Object> payload) {
        return new TriggerInvocationRequest(triggerId, payload, "manual", null);
    }

    public static TriggerInvocationRequest webhook(String triggerId, Map<String, Object> payload) {
        return new TriggerInvocationRequest(triggerId, payload, "webhook", null);
    }

    public static TriggerInvocationRequest event(String triggerId, Map<String, Object> payload) {
        return new TriggerInvocationRequest(triggerId, payload, "event", null);
    }

    public static TriggerInvocationRequest scheduled(
            String triggerId, Map<String, Object> payload, String conversationId) {
        return new TriggerInvocationRequest(triggerId, payload, "cron", conversationId);
    }

    public static TriggerInvocationRequest cron(String triggerId) {
        return scheduled(triggerId, Map.of(), null);
    }
}
