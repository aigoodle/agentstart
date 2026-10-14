package io.aigoodle.memory;

import java.util.Map;

public record MemoryWrite(
        String tenantId,
        String ownerId,
        String conversationId,
        MemoryTier tier,
        MemoryRole role,
        String content,
        double importance,
        Map<String, String> metadata) {
    public static MemoryWrite shortTerm(String ownerId, String conversationId, MemoryRole role, String content) {
        return new MemoryWrite("default", ownerId, conversationId, MemoryTier.SHORT_TERM, role, content, 0.5, Map.of());
    }
}
