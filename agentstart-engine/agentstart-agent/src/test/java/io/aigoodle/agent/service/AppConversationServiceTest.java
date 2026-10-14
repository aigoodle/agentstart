package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.mapper.AppConversationMapper;
import io.aigoodle.memory.MemoryManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppConversationServiceTest {

    @Test
    void returnsExistingConversationWithoutOverwritingMetadata() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationEntity existingConversation = new AppConversationEntity();
        existingConversation.setId("conversation-1");
        existingConversation.setTenantId("tenant-1");
        existingConversation.setAppId("app-1");
        existingConversation.setName("Existing title");
        when(conversationMapper.selectOne(any())).thenReturn(existingConversation);
        AppConversationService conversationService =
                new AppConversationService(conversationMapper, mock(MemoryManager.class));

        AppConversationEntity resolvedConversation =
                conversationService.ensure(existingConversation.getId(), "app-1", "tenant-1", "New title");

        assertThat(resolvedConversation).isSameAs(existingConversation);
        assertThat(resolvedConversation.getName()).isEqualTo("Existing title");
        verify(conversationMapper, never()).insert(any(AppConversationEntity.class));
    }

    @Test
    void tenantScopedLookupDoesNotReturnAnotherTenantsConversation() {
        AppConversationMapper mapper = mock(AppConversationMapper.class);
        AppConversationEntity foreign = new AppConversationEntity();
        foreign.setId("shared-conversation");
        foreign.setTenantId("tenant-a");
        foreign.setAppId("app-1");
        when(mapper.selectOne(any())).thenReturn(null);
        AppConversationService service = new AppConversationService(mapper, mock(MemoryManager.class));

        AppConversationEntity created = service.ensure("shared-conversation", "app-1", "tenant-b", "hello");

        assertThat(created.getTenantId()).isEqualTo("tenant-b");
        verify(mapper).insert(created);
    }

    @Test
    void createsConversationWithNormalizedTenantAndBoundedTitle() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationService conversationService =
                new AppConversationService(conversationMapper, mock(MemoryManager.class));
        String firstMessage = "A".repeat(100);

        AppConversationEntity conversation = conversationService.ensure("conversation-1", "app-1", "  ", firstMessage);

        ArgumentCaptor<AppConversationEntity> insertedConversation =
                ArgumentCaptor.forClass(AppConversationEntity.class);
        verify(conversationMapper).insert(insertedConversation.capture());
        assertThat(conversation).isSameAs(insertedConversation.getValue());
        assertThat(conversation.getTenantId()).isEqualTo("default");
        assertThat(conversation.getName()).hasSize(60).endsWith("…");
        assertThat(conversation.getPinned()).isFalse();
        assertThat(conversation.getStatus()).isEqualTo("normal");
    }
}
