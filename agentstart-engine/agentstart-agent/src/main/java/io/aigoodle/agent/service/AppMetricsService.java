package io.aigoodle.agent.service;

import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.memory.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Derives application activity from the canonical memory store. */
public class AppMetricsService {
    private static final int METRICS_SCAN_LIMIT = 1000;
    private final MemoryManager memoryManager;
    private final AppMapper appMapper;

    public AppMetricsService(MemoryManager memoryManager, AppMapper appMapper) {
        this.memoryManager = memoryManager;
        this.appMapper = appMapper;
    }

    public AppMetricsView summarize(String appId) {
        return summarize(UserContextHolder.currentTenantId(), appId);
    }

    public AppMetricsView summarize(String tenantId, String appId) {
        String tenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        List<MemoryItem> messages = memoryManager.recall(
                new MemoryQuery(tenant, appId, null, null, Set.of(MemoryTier.SHORT_TERM), METRICS_SCAN_LIMIT));
        MetricsAccumulator metrics = new MetricsAccumulator();
        messages.forEach(metrics::include);
        return metrics.toView(appId, messages.size());
    }

    private static final class MetricsAccumulator {
        private final Set<String> conversationIds = new HashSet<>();
        private int userMessageCount;
        private int assistantMessageCount;
        private Instant mostRecentActivity;

        void include(MemoryItem message) {
            if (message.conversationId() != null) conversationIds.add(message.conversationId());
            if (message.role() == MemoryRole.USER) userMessageCount++;
            else if (message.role() == MemoryRole.ASSISTANT) assistantMessageCount++;
            if (message.createdAt() != null
                    && (mostRecentActivity == null || message.createdAt().isAfter(mostRecentActivity)))
                mostRecentActivity = message.createdAt();
        }

        AppMetricsView toView(String appId, int totalMessageCount) {
            int conversations = conversationIds.size();
            double average =
                    conversations == 0 ? 0.0 : Math.round((double) userMessageCount / conversations * 100.0) / 100.0;
            return AppMetricsView.builder()
                    .appId(appId)
                    .totalConversations(conversations)
                    .totalMessages(totalMessageCount)
                    .userMessages(userMessageCount)
                    .assistantMessages(assistantMessageCount)
                    .avgInteractionsPerConversation(average)
                    .lastActivityAt(mostRecentActivity == null ? null : mostRecentActivity.toString())
                    .build();
        }
    }
}
