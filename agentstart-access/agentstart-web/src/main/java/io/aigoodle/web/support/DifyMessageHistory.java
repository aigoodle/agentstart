package io.aigoodle.web.support;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryRole;
import java.util.List;
import org.springframework.stereotype.Component;

/** Provides Dify history views from the canonical memory module. */
@Component
public final class DifyMessageHistory {
    private static final int TITLE_SCAN_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_HISTORY_SIZE = 500;

    private final MemoryManager memoryManager;
    private final AppConversationService conversationService;

    public DifyMessageHistory(MemoryManager memoryManager, AppConversationService conversationService) {
        this.memoryManager = memoryManager;
        this.conversationService = conversationService;
    }

    public List<MemoryItem> findAll(String conversationId) {
        AppConversationEntity conversation = conversationService.require(conversationId);
        return memoryManager.history(
                conversation.getTenantId(), conversation.getAppId(), conversationId, MAX_HISTORY_SIZE);
    }

    public List<MemoryItem> findAll(String tenantId, String appId, String conversationId) {
        AppConversationEntity conversation = conversationService.require(tenantId, appId, conversationId);
        return memoryManager.history(
                conversation.getTenantId(), conversation.getAppId(), conversationId, MAX_HISTORY_SIZE);
    }

    public String suggestTitle(String conversationId) {
        return findAll(conversationId).stream()
                .limit(TITLE_SCAN_SIZE)
                .filter(item -> item.role() == MemoryRole.USER)
                .map(MemoryItem::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .map(String::trim)
                .map(DifyMessageHistory::abbreviate)
                .orElse(null);
    }

    public String suggestTitle(String tenantId, String appId, String conversationId) {
        return findAll(tenantId, appId, conversationId).stream()
                .limit(TITLE_SCAN_SIZE)
                .filter(item -> item.role() == MemoryRole.USER)
                .map(MemoryItem::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .map(String::trim)
                .map(DifyMessageHistory::abbreviate)
                .orElse(null);
    }

    private static String abbreviate(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH - 1) + "…" : title;
    }
}
