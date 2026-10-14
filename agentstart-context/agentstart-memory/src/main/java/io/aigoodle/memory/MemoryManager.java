package io.aigoodle.memory;

import java.util.List;

/** High-level lifecycle and retrieval facade used by agent runtimes. */
public interface MemoryManager {
    MemoryItem remember(MemoryWrite write);

    List<MemoryItem> recall(MemoryQuery query);

    /** Chronological short-term conversation history, oldest first. */
    List<MemoryItem> history(String tenantId, String ownerId, String conversationId, int limit);

    default void rememberExchange(
            String tenantId, String ownerId, String conversationId, String userContent, String assistantContent) {
        if (userContent != null && !userContent.isBlank()) {
            remember(new MemoryWrite(
                    tenantId, ownerId, conversationId, MemoryTier.SHORT_TERM, MemoryRole.USER, userContent, 0.5, null));
        }
        if (assistantContent != null && !assistantContent.isBlank()) {
            remember(new MemoryWrite(
                    tenantId,
                    ownerId,
                    conversationId,
                    MemoryTier.SHORT_TERM,
                    MemoryRole.ASSISTANT,
                    assistantContent,
                    0.5,
                    null));
        }
    }

    void forgetConversation(String tenantId, String ownerId, String conversationId);

    /** Tenant-scoped working-memory eviction for embedded multi-tenant hosts. */
    default void clearWorkingMemory(String tenantId, String ownerId, String conversationId) {
        clearWorkingMemory(conversationId);
    }

    /**
     * Compatibility-wide eviction by conversation id. New multi-tenant callers should use the
     * tenant-scoped overload to avoid clearing another tenant's same-named conversation.
     */
    void clearWorkingMemory(String conversationId);
}
