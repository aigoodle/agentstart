package io.aigoodle.completion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.memory.MemoryTier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConversationHistoryServiceTest {

    @Test
    void buildsAReadableConversationSummaryFromTheFirstUserMessage() {
        AppConversationService conversations = mock(AppConversationService.class);
        MemoryManager memory = mock(MemoryManager.class);
        AppConversationEntity conversation = new AppConversationEntity();
        conversation.setId("conversation-1");
        conversation.setName("New conversation");
        conversation.setAppId("app-1");
        conversation.setTenantId("default");
        conversation.setFromEndUserId("user-1");
        when(conversations.listByApp("default", "app-1")).thenReturn(List.of(conversation));
        when(memory.history("default", "app-1", "conversation-1", 1))
                .thenReturn(List.of(item("2", MemoryRole.USER, "Explain the quarterly report")));
        when(memory.history("default", "app-1", "conversation-1", 20))
                .thenReturn(List.of(
                        item("1", MemoryRole.ASSISTANT, "Hello"),
                        item("2", MemoryRole.USER, "Explain the quarterly report")));
        ConversationHistoryService history =
                new ConversationHistoryService(providerOf(conversations), providerOf(memory));

        List<Map<String, Object>> views = history.conversations("default", "app-1", 10);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view).containsEntry("conversationId", "conversation-1");
            assertThat(view).containsEntry("userId", "user-1");
            assertThat(view).containsEntry("firstMessage", "Explain the quarterly report");
        });
    }

    private static MemoryItem item(String id, MemoryRole role, String content) {
        return new MemoryItem(
                id,
                "default",
                "app-1",
                "conversation-1",
                MemoryTier.SHORT_TERM,
                role,
                content,
                .5,
                Instant.now(),
                null,
                0,
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
