package io.aigoodle.completion.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DifyEmitAdapterTest {

    @Test
    void translatesTypedOpenAiChunkIntoDifyMessage() {
        EventCollector events = new EventCollector();
        DifyEmitAdapter adapter = new DifyEmitAdapter(events::add, "task-1", "conversation-1");

        adapter.event("message", OpenAIChatResponse.chunk("chunk-1", "model", null, "hello", null));

        assertThat(events.payloads()).singleElement().satisfies(payload -> {
            assertThat(payload)
                    .containsEntry("event", "message")
                    .containsEntry("task_id", "task-1")
                    .containsEntry("conversation_id", "conversation-1")
                    .containsEntry("answer", "hello");
            assertThat(payload.get("message_id")).isEqualTo(payload.get("id"));
        });
    }

    @Test
    void emitsEmptyAnswerWhenStreamHadNoContent() {
        EventCollector events = new EventCollector();
        DifyEmitAdapter adapter = new DifyEmitAdapter(events::add, "task-1", "conversation-1");

        adapter.event("message_end", Map.of("status", "succeeded"));

        assertThat(events.payloads()).singleElement().satisfies(payload -> assertThat(payload)
                .containsEntry("event", "message_end")
                .containsEntry("answer", "")
                .containsEntry("status", "succeeded"));
    }

    @Test
    void transportIdentityCannotBeOverwrittenByUpstreamData() {
        EventCollector events = new EventCollector();
        DifyEmitAdapter adapter = new DifyEmitAdapter(events::add, "task-1", "conversation-1");

        adapter.event(
                "error",
                Map.of(
                        "event", "fake",
                        "task_id", "fake-task",
                        "conversation_id", "fake-conversation",
                        "message", "failed"));

        assertThat(events.payloads()).singleElement().satisfies(payload -> assertThat(payload)
                .containsEntry("event", "error")
                .containsEntry("task_id", "task-1")
                .containsEntry("conversation_id", "conversation-1")
                .containsEntry("message", "failed")
                .containsEntry("status", 500));
    }

    private static final class EventCollector {

        private final List<Map<String, Object>> payloads = new ArrayList<>();

        @SuppressWarnings("unchecked")
        void add(String eventName, Object eventData) {
            assertThat(eventName).isEqualTo("message");
            payloads.add((Map<String, Object>) eventData);
        }

        List<Map<String, Object>> payloads() {
            return payloads;
        }
    }
}
