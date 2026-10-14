package io.aigoodle.memory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Persistence SPI; applications may replace JDBC with a vector or remote store. */
public interface MemoryStore {
    void save(MemoryItem item);

    List<MemoryItem> find(MemoryQuery query);

    default void delete(String tenantId, String ownerId, String conversationId) {}

    default void purgeExpired(Instant now) {}
    /** Records successful recall so stores can reinforce frequently useful memories. */
    default void recordAccess(String tenantId, Collection<String> memoryIds, Instant accessedAt) {
        recordAccess(memoryIds, accessedAt);
    }
    /** @deprecated Implement the tenant-aware overload. */
    @Deprecated(forRemoval = false)
    default void recordAccess(Collection<String> memoryIds, Instant accessedAt) {}
}
