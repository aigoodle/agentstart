package io.aigoodle.memory.extraction;

/** One completed conversational exchange offered to memory extractors. */
public record MemoryExchange(
        String tenantId, String ownerId, String conversationId, String userContent, String assistantContent) {}
