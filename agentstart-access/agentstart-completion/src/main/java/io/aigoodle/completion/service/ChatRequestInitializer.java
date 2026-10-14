package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;

/** Ensures chat requests have a conversation identity and best-effort catalog record. */
final class ChatRequestInitializer {

    private final ObjectProvider<AppConversationService> conversationServiceProvider;
    private final Logger logger;

    ChatRequestInitializer(ObjectProvider<AppConversationService> conversationServiceProvider, Logger logger) {
        this.conversationServiceProvider = conversationServiceProvider;
        this.logger = logger;
    }

    void initialize(AppEntity application, OpenAIChatRequest request) {
        ensureConversationId(request);
        createConversationRecord(application, request);
    }

    private static void ensureConversationId(OpenAIChatRequest request) {
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            request.setConversationId(UUID.randomUUID().toString());
        }
    }

    private void createConversationRecord(AppEntity application, OpenAIChatRequest request) {
        AppConversationService conversationService = conversationServiceProvider.getIfAvailable();
        if (conversationService == null) {
            return;
        }
        try {
            conversationService.ensure(
                    request.getConversationId(),
                    application.getId(),
                    application.getTenantId(),
                    request.lastUserMessage());
        } catch (Exception exception) {
            logger.debug("Conversation upsert skipped: {}", exception.getMessage());
        }
    }
}
