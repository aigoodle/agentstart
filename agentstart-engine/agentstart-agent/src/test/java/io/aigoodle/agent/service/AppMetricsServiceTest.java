package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.memory.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppMetricsServiceTest {
    @Test
    void summarizesCanonicalMemoryItems() {
        MemoryManager memory = mock(MemoryManager.class);
        when(memory.recall(any()))
                .thenReturn(List.of(
                        item("1", "c1", MemoryRole.USER, Instant.parse("2026-01-01T00:00:00Z")),
                        item("2", "c1", MemoryRole.ASSISTANT, Instant.parse("2026-01-01T00:01:00Z")),
                        item("3", "c2", MemoryRole.USER, Instant.parse("2026-01-02T00:00:00Z"))));

        AppMetricsView view = new AppMetricsService(memory, mock(AppMapper.class)).summarize("app-1");

        assertThat(view.getTotalConversations()).isEqualTo(2);
        assertThat(view.getTotalMessages()).isEqualTo(3);
        assertThat(view.getUserMessages()).isEqualTo(2);
        assertThat(view.getAssistantMessages()).isEqualTo(1);
        assertThat(view.getAvgInteractionsPerConversation()).isEqualTo(1.0);
        assertThat(view.getLastActivityAt()).isEqualTo("2026-01-02T00:00:00Z");
    }

    @Test
    void emptyMemoryProducesZeroMetrics() {
        MemoryManager memory = mock(MemoryManager.class);
        when(memory.recall(any())).thenReturn(List.of());
        assertThat(new AppMetricsService(memory, mock(AppMapper.class))
                        .summarize("app-1")
                        .getTotalMessages())
                .isZero();
    }

    @Test
    void scopedMetricsPassTrustedTenantAndApplicationToMemoryQuery() {
        MemoryManager memory = mock(MemoryManager.class);
        when(memory.recall(any())).thenReturn(List.of());

        new AppMetricsService(memory, mock(AppMapper.class)).summarize("tenant-a", "app-a");

        ArgumentCaptor<MemoryQuery> query = ArgumentCaptor.forClass(MemoryQuery.class);
        verify(memory).recall(query.capture());
        assertThat(query.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(query.getValue().ownerId()).isEqualTo("app-a");
    }

    private static MemoryItem item(String id, String conversationId, MemoryRole role, Instant time) {
        return new MemoryItem(
                id, "default", "app-1", conversationId, MemoryTier.SHORT_TERM, role, id, .5, time, null, 0, Map.of());
    }
}
