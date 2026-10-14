package io.aigoodle.memory;

import java.util.Set;

public record MemoryQuery(
        String tenantId, String ownerId, String conversationId, String query, Set<MemoryTier> tiers, int limit) {
    public MemoryQuery {
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        tiers = tiers == null || tiers.isEmpty() ? Set.of(MemoryTier.values()) : Set.copyOf(tiers);
        limit = Math.max(1, limit);
    }
}
