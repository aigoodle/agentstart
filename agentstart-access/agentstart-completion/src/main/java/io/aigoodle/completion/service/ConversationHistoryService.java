package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.completion.support.AppAccessResolver;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryRole;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Provides the console-oriented conversation and message history read model. */
@Service
public class ConversationHistoryService {

    private final ObjectProvider<AppConversationService> conversationServices;
    private final ObjectProvider<MemoryManager> memoryManagers;

    public ConversationHistoryService(
            ObjectProvider<AppConversationService> conversationServices, ObjectProvider<MemoryManager> memoryManagers) {
        this.conversationServices = conversationServices;
        this.memoryManagers = memoryManagers;
    }

    public List<Map<String, Object>> conversations(String tenantId, String appId, int limit) {
        AppConversationService conversationService = conversationServices.getIfAvailable();
        if (conversationService == null) {
            return List.of();
        }
        MemoryManager memory = memoryManagers.getIfAvailable();
        return conversationService.listByApp(tenantId, appId).stream()
                .filter(conversation -> hasMessages(conversation, memory))
                .limit(limit)
                .map(conversation -> toConversationView(conversation, memory))
                .toList();
    }

    public List<Map<String, Object>> messages(String tenantId, String appId, String conversationId, int limit) {
        AppConversationService conversationService = conversationServices.getIfAvailable();
        if (conversationService == null) return List.of();
        AppConversationEntity conversation = conversationService.require(tenantId, appId, conversationId);
        MemoryManager memory = memoryManagers.getIfAvailable();
        if (memory == null) {
            return List.of();
        }
        return memory.history(conversation.getTenantId(), appId, conversationId, limit).stream()
                .map(ConversationHistoryService::toMessageView)
                .toList();
    }

    private Map<String, Object> toConversationView(AppConversationEntity conversation, MemoryManager memory) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("conversationId", conversation.getId());
        view.put("name", conversation.getName());
        view.put("userId", userIdOf(conversation));
        view.put("firstMessage", firstMessageOf(conversation, memory));
        view.put("updatedAt", formatDateTime(conversation.getUpdatedAt(), conversation.getCreatedAt()));
        view.put("pinned", Boolean.TRUE.equals(conversation.getPinned()));
        return view;
    }

    private static Map<String, Object> toMessageView(MemoryItem message) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("role", message.role() == null ? null : message.role().name());
        view.put("content", message.content());
        view.put(
                "createdAt",
                message.createdAt() == null ? null : message.createdAt().toString());
        return view;
    }

    private static boolean hasMessages(AppConversationEntity conversation, MemoryManager memory) {
        if (memory == null) return false;
        try {
            return !memory.history(conversation.getTenantId(), conversation.getAppId(), conversation.getId(), 1)
                    .isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String firstMessageOf(AppConversationEntity conversation, MemoryManager memory) {
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            return conversation.getSummary();
        }
        if (memory == null) {
            return conversation.getName();
        }
        try {
            return memory
                    .history(conversation.getTenantId(), conversation.getAppId(), conversation.getId(), 20)
                    .stream()
                    .filter(message -> message.role() == MemoryRole.USER)
                    .map(MemoryItem::content)
                    .findFirst()
                    .orElse(conversation.getName());
        } catch (RuntimeException memoryFailure) {
            return conversation.getName();
        }
    }

    private static String userIdOf(AppConversationEntity conversation) {
        String endUserId = AppAccessResolver.trimToNull(conversation.getFromEndUserId());
        return endUserId != null ? endUserId : AppAccessResolver.trimToNull(conversation.getFromAccountId());
    }

    private static String formatDateTime(LocalDateTime updatedAt, LocalDateTime createdAt) {
        LocalDateTime effectiveTime = updatedAt != null ? updatedAt : createdAt;
        return effectiveTime == null ? null : effectiveTime.toString();
    }
}
