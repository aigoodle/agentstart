package io.aigoodle.completion.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds Dify-compatible event envelopes while protecting transport-owned fields. */
final class DifyEventPayloadFactory {

    private final String taskId;
    private final String conversationId;
    private final String messageId;

    DifyEventPayloadFactory(String taskId, String conversationId, String messageId) {
        this.taskId = taskId;
        this.conversationId = conversationId;
        this.messageId = messageId;
    }

    Map<String, Object> message(String answer) {
        Map<String, Object> payload = messageEnvelope("message");
        payload.put("answer", answer);
        payload.put("created_at", System.currentTimeMillis() / 1000);
        return payload;
    }

    Map<String, Object> messageEnd(Object eventData, boolean contentWasEmitted) {
        Map<String, Object> payload = messageEnvelope("message_end");
        mergeEventData(payload, eventData);
        if (!contentWasEmitted) {
            payload.putIfAbsent("answer", "");
        }
        return payload;
    }

    Map<String, Object> error(Object eventData) {
        Map<String, Object> payload = baseEnvelope("error");
        mergeEventData(payload, eventData);
        payload.putIfAbsent("status", 500);
        payload.putIfAbsent("code", "internal_server_error");
        return payload;
    }

    Map<String, Object> wrapped(String eventName, Object eventData) {
        Map<String, Object> payload = baseEnvelope(eventName);
        if (eventData != null) {
            payload.put("data", eventData);
        }
        return payload;
    }

    private Map<String, Object> messageEnvelope(String eventName) {
        Map<String, Object> payload = baseEnvelope(eventName);
        payload.put("id", messageId);
        payload.put("message_id", messageId);
        return payload;
    }

    private Map<String, Object> baseEnvelope(String eventName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("task_id", taskId);
        payload.put("conversation_id", conversationId);
        return payload;
    }

    private static void mergeEventData(Map<String, Object> target, Object eventData) {
        if (!(eventData instanceof Map<?, ?> values)) {
            return;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toString();
            if (!isTransportField(key)) {
                target.putIfAbsent(key, entry.getValue());
            }
        }
    }

    private static boolean isTransportField(String key) {
        return "event".equals(key) || "task_id".equals(key) || "conversation_id".equals(key);
    }
}
