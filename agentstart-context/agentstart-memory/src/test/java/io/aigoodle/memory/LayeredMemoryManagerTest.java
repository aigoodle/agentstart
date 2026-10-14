package io.aigoodle.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.memory.config.MemoryProperties;
import io.aigoodle.memory.extraction.RuleBasedMemoryExtractor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayeredMemoryManagerTest {
    private final RecordingStore store = new RecordingStore();
    private final MemoryProperties properties = new MemoryProperties();
    private final LayeredMemoryManager manager = new LayeredMemoryManager(store, properties);

    @Test
    void keepsWorkingMemoryBounded() {
        properties.setWorkingCapacity(2);
        for (int index = 0; index < 3; index++) {
            manager.remember(new MemoryWrite(
                    "default", "a", "c", MemoryTier.WORKING, MemoryRole.USER, "message " + index, .5, null));
        }
        List<MemoryItem> result =
                manager.recall(new MemoryQuery("default", "a", "c", null, Set.of(MemoryTier.WORKING), 10));
        assertThat(result).extracting(MemoryItem::content).containsExactly("message 1", "message 2");
    }

    @Test
    void isolatesWorkingMemoryByTenantOwnerAndConversation() {
        manager.remember(new MemoryWrite(
                "tenant-a", "employee-1", "support", MemoryTier.WORKING, MemoryRole.USER, "tenant-a memory", .5, null));
        manager.remember(new MemoryWrite(
                "tenant-b", "employee-1", "support", MemoryTier.WORKING, MemoryRole.USER, "tenant-b memory", .5, null));
        manager.remember(new MemoryWrite(
                "tenant-a",
                "employee-2",
                "support",
                MemoryTier.WORKING,
                MemoryRole.USER,
                "employee-2 memory",
                .5,
                null));

        assertThat(working("tenant-a", "employee-1", "support"))
                .extracting(MemoryItem::content)
                .containsExactly("tenant-a memory");
        assertThat(working("tenant-b", "employee-1", "support"))
                .extracting(MemoryItem::content)
                .containsExactly("tenant-b memory");
        assertThat(working("tenant-a", "employee-2", "support"))
                .extracting(MemoryItem::content)
                .containsExactly("employee-2 memory");

        manager.forgetConversation("tenant-a", "employee-1", "support");
        assertThat(working("tenant-a", "employee-1", "support")).isEmpty();
        assertThat(working("tenant-b", "employee-1", "support"))
                .extracting(MemoryItem::content)
                .containsExactly("tenant-b memory");
    }

    @Test
    void promotesImportantShortTermMemory() {
        MemoryItem item = manager.remember(new MemoryWrite(
                "default", "a", "c", MemoryTier.SHORT_TERM, MemoryRole.FACT, "User prefers concise answers", .9, null));
        assertThat(item.tier()).isEqualTo(MemoryTier.LONG_TERM);
        assertThat(item.expiresAt()).isNull();
    }

    @Test
    void semanticWordsOutrankUnrelatedRecentItems() {
        store.items.add(new MemoryItem(
                "1",
                "default",
                "a",
                "c",
                MemoryTier.LONG_TERM,
                MemoryRole.FACT,
                "production database is PostgreSQL",
                .8,
                Instant.now().minusSeconds(3600),
                null,
                0,
                null));
        store.items.add(new MemoryItem(
                "2",
                "default",
                "a",
                "c",
                MemoryTier.LONG_TERM,
                MemoryRole.FACT,
                "likes blue",
                .8,
                Instant.now(),
                null,
                0,
                null));
        assertThat(manager.recall(
                        new MemoryQuery("default", "a", "c", "PostgreSQL database", Set.of(MemoryTier.LONG_TERM), 1)))
                .extracting(MemoryItem::id)
                .containsExactly("1");
        assertThat(store.accessedIds).containsExactly("1");
    }

    @Test
    void extractsExplicitPreferenceOnceAsLongTermMemory() {
        LayeredMemoryManager extracting =
                new LayeredMemoryManager(store, properties, List.of(new RuleBasedMemoryExtractor()));

        extracting.rememberExchange("default", "a", "c", "我偏好简洁的回答。", "好的，我会保持简洁。");
        extracting.rememberExchange("default", "a", "c2", "我偏好简洁的回答。", "明白。");

        assertThat(store.items.stream()
                        .filter(item -> item.tier() == MemoryTier.LONG_TERM)
                        .toList())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.role()).isEqualTo(MemoryRole.FACT);
                    assertThat(item.content()).isEqualTo("我偏好简洁的回答");
                    assertThat(item.conversationId()).isNull();
                });
    }

    private List<MemoryItem> working(String tenantId, String ownerId, String conversationId) {
        return manager.recall(new MemoryQuery(tenantId, ownerId, conversationId, null, Set.of(MemoryTier.WORKING), 10));
    }

    private static class RecordingStore implements MemoryStore {
        private final List<MemoryItem> items = new ArrayList<>();
        private final List<String> accessedIds = new ArrayList<>();

        public void save(MemoryItem item) {
            items.add(item);
        }

        public List<MemoryItem> find(MemoryQuery query) {
            return items.stream()
                    .filter(item -> query.tiers().contains(item.tier()))
                    .filter(item -> query.tenantId().equals(item.tenantId()))
                    .filter(item -> query.ownerId() == null || query.ownerId().equals(item.ownerId()))
                    .filter(item -> query.conversationId() == null
                            || query.conversationId().equals(item.conversationId()))
                    .toList();
        }

        public void recordAccess(java.util.Collection<String> ids, Instant at) {
            accessedIds.addAll(ids);
        }
    }
}
