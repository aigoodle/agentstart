package io.aigoodle.memory;

import java.time.Instant;
import java.util.Map;

/** Immutable, provider-neutral memory value. */
public record MemoryItem(
        String id,
        String tenantId,
        String ownerId,
        String conversationId,
        MemoryTier tier,
        MemoryRole role,
        String content,
        double importance,
        Instant createdAt,
        Instant expiresAt,
        int accessCount,
        Map<String, String> metadata) {

    public MemoryItem {
        tenantId = blankToDefault(tenantId, "default");
        tier = tier == null ? MemoryTier.SHORT_TERM : tier;
        role = role == null ? MemoryRole.FACT : role;
        content = content == null ? "" : content;
        importance = Math.max(0.0, Math.min(1.0, importance));
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
