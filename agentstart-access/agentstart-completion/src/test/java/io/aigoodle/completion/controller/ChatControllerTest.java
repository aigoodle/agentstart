package io.aigoodle.completion.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppApiTokenService;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.completion.service.AppGenerateService;
import io.aigoodle.completion.service.ConversationHistoryService;
import io.aigoodle.completion.support.AppAccessResolver;
import io.aigoodle.completion.support.ChatAccessContext;
import io.aigoodle.completion.support.ChatAccessMode;
import io.aigoodle.completion.support.ChatAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ChatControllerTest {

    @Test
    void openAIEndpointRejectsMissingApiKey() {
        ChatController controller = new ChatController(
                mock(AppGenerateService.class),
                new AppAccessResolver(emptyProvider()),
                mock(ChatAccessPolicy.class),
                mock(ConversationHistoryService.class));

        assertThatThrownBy(() ->
                        controller.openAICompletions(null, new io.aigoodle.completion.dto.openai.OpenAIChatRequest()))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void apiKeyResolvesApplicationWithoutClientSuppliedAppId() {
        AppApiTokenService tokenService = mock(AppApiTokenService.class);
        AppApiTokenEntity token = new AppApiTokenEntity();
        token.setId("token-1");
        token.setAppId("app-from-key");
        when(tokenService.findByToken("secret-key")).thenReturn(token);

        AppAccessResolver resolver = new AppAccessResolver(providerOf(tokenService));

        org.assertj.core.api.Assertions.assertThat(resolver.requireTokenApp("Bearer secret-key"))
                .isEqualTo("app-from-key");
    }

    @Test
    void difyProductionEndpointRejectsBodySuppliedApplicationWithoutApiKey() {
        ChatController controller = new ChatController(
                mock(AppGenerateService.class),
                new AppAccessResolver(emptyProvider()),
                mock(ChatAccessPolicy.class),
                mock(ConversationHistoryService.class));
        io.aigoodle.completion.dto.dify.DifyChatMessagesRequest request =
                new io.aigoodle.completion.dto.dify.DifyChatMessagesRequest();
        request.setAppId("victim-app");
        request.setQuery("hello");
        request.setResponseMode("blocking");

        assertThatThrownBy(() -> controller.chatMessages(null, null, null, null, null, null, null, request))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void rejectsConversationOwnedByAnotherApplication() {
        AppConversationService conversationService = mock(AppConversationService.class);
        AppConversationEntity conversation = new AppConversationEntity();
        conversation.setId("conversation-1");
        conversation.setAppId("app-b");
        when(conversationService.require(conversation.getId())).thenReturn(conversation);
        when(conversationService.require("tenant-a", "app-a", conversation.getId()))
                .thenThrow(new PlatformException(
                        "conversation_not_found", "Conversation not found: " + conversation.getId(), null));
        ChatAccessPolicy accessPolicy = mock(ChatAccessPolicy.class);
        when(accessPolicy.authorizeInternal("app-a"))
                .thenReturn(new ChatAccessContext("app-a", "tenant-a", "user-a", ChatAccessMode.INTERNAL));

        ChatController controller = new ChatController(
                mock(AppGenerateService.class),
                new AppAccessResolver(emptyProvider()),
                accessPolicy,
                new ConversationHistoryService(providerOf(conversationService), emptyProvider()));

        assertThatThrownBy(() -> controller.conversationMessages("app-a", conversation.getId(), null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Conversation not found");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        return mock(ObjectProvider.class);
    }
}
