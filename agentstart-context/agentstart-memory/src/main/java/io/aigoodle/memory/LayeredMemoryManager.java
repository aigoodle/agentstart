package io.aigoodle.memory;

import io.aigoodle.memory.config.MemoryProperties;
import io.aigoodle.memory.extraction.MemoryExchange;
import io.aigoodle.memory.extraction.MemoryExtractor;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded working memory plus persisted short/long-term memory with hybrid ranking. */
public class LayeredMemoryManager implements MemoryManager {
    private record WorkingKey(String tenantId, String ownerId, String conversationId) {
        private WorkingKey {
            tenantId = normalized(tenantId, "default");
            ownerId = normalized(ownerId, "anonymous");
            conversationId = normalized(conversationId, null);
            if (conversationId == null) throw new IllegalArgumentException("conversationId is required");
        }
    }

    private final MemoryStore store;
    private final MemoryProperties properties;
    private final Map<WorkingKey, Deque<MemoryItem>> working = new ConcurrentHashMap<>();
    private final List<MemoryExtractor> extractors;

    public LayeredMemoryManager(MemoryStore store, MemoryProperties properties) {
        this(store, properties, List.of());
    }

    public LayeredMemoryManager(MemoryStore store, MemoryProperties properties, List<MemoryExtractor> extractors) {
        this.store = store;
        this.properties = properties;
        this.extractors = extractors == null ? List.of() : List.copyOf(extractors);
    }

    @Override
    public void rememberExchange(
            String tenantId, String ownerId, String conversationId, String userContent, String assistantContent) {
        MemoryManager.super.rememberExchange(tenantId, ownerId, conversationId, userContent, assistantContent);
        if (!properties.isExtractionEnabled() || extractors.isEmpty()) return;
        Set<String> known = new HashSet<>();
        recall(new MemoryQuery(tenantId, ownerId, null, userContent, Set.of(MemoryTier.LONG_TERM), 100))
                .forEach(item -> known.add(normalize(item.content())));
        MemoryExchange exchange = new MemoryExchange(tenantId, ownerId, conversationId, userContent, assistantContent);
        for (MemoryExtractor extractor : extractors) {
            List<MemoryWrite> extracted;
            try {
                extracted = extractor.extract(exchange);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (extracted == null) continue;
            for (MemoryWrite write : extracted) {
                if (write != null && known.add(normalize(write.content()))) remember(write);
            }
        }
    }

    @Override
    public MemoryItem remember(MemoryWrite write) {
        Objects.requireNonNull(write, "write");
        if (write.content() == null || write.content().isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        MemoryTier tier = write.tier() == null ? MemoryTier.SHORT_TERM : write.tier();
        if (tier == MemoryTier.SHORT_TERM && write.importance() >= properties.getLongTermThreshold()) {
            tier = MemoryTier.LONG_TERM;
        }
        Instant now = Instant.now();
        Instant expiresAt = tier == MemoryTier.SHORT_TERM ? now.plus(properties.getShortTermTtl()) : null;
        MemoryItem item = new MemoryItem(
                UUID.randomUUID().toString(),
                write.tenantId(),
                write.ownerId(),
                write.conversationId(),
                tier,
                write.role(),
                write.content(),
                write.importance(),
                now,
                expiresAt,
                0,
                write.metadata());
        if (tier == MemoryTier.WORKING) {
            rememberWorking(item);
        } else {
            store.save(item);
        }
        return item;
    }

    @Override
    public List<MemoryItem> recall(MemoryQuery query) {
        Instant now = Instant.now();
        List<MemoryItem> candidates = new ArrayList<>();
        if (query.tiers().contains(MemoryTier.WORKING) && query.conversationId() != null) {
            Deque<MemoryItem> window =
                    working.get(workingKey(query.tenantId(), query.ownerId(), query.conversationId()));
            if (window != null) {
                synchronized (window) {
                    candidates.addAll(window);
                }
            }
        }
        Set<MemoryTier> persistedTiers = new HashSet<>(query.tiers());
        persistedTiers.remove(MemoryTier.WORKING);
        if (!persistedTiers.isEmpty()) {
            candidates.addAll(store.find(new MemoryQuery(
                    query.tenantId(),
                    query.ownerId(),
                    query.conversationId(),
                    query.query(),
                    persistedTiers,
                    query.limit())));
        }
        // A pure working-memory read without a search query is prompt context,
        // not retrieval: preserve complete turn order even when multiple writes
        // receive the same clock timestamp.
        if ((query.query() == null || query.query().isBlank()) && query.tiers().equals(Set.of(MemoryTier.WORKING))) {
            return candidates.stream()
                    .filter(item -> !item.expired(now))
                    .limit(query.limit())
                    .toList();
        }
        List<MemoryItem> result = candidates.stream()
                .filter(item -> !item.expired(now))
                .sorted(Comparator.comparingDouble((MemoryItem item) -> score(item, query.query(), now))
                        .reversed()
                        .thenComparing(MemoryItem::createdAt, Comparator.reverseOrder()))
                .limit(query.limit())
                .toList();
        store.recordAccess(
                query.tenantId(),
                result.stream()
                        .filter(item -> item.tier() != MemoryTier.WORKING)
                        .map(MemoryItem::id)
                        .filter(Objects::nonNull)
                        .toList(),
                now);
        return result;
    }

    @Override
    public List<MemoryItem> history(String tenantId, String ownerId, String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        List<MemoryItem> recentFirst = store.find(new MemoryQuery(
                tenantId, ownerId, conversationId, null, Set.of(MemoryTier.SHORT_TERM), Math.max(1, limit)));
        return recentFirst.stream()
                .filter(item -> !item.expired(Instant.now()))
                .sorted(Comparator.comparing(MemoryItem::createdAt))
                .skip(Math.max(0, recentFirst.size() - Math.max(1, limit)))
                .toList();
    }

    @Override
    public void clearWorkingMemory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String selected = conversationId.trim();
        working.keySet().removeIf(key -> selected.equals(key.conversationId()));
    }

    @Override
    public void clearWorkingMemory(String tenantId, String ownerId, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            working.remove(workingKey(tenantId, ownerId, conversationId));
        }
    }

    @Override
    public void forgetConversation(String tenantId, String ownerId, String conversationId) {
        clearWorkingMemory(tenantId, ownerId, conversationId);
        if (conversationId != null && !conversationId.isBlank()) {
            store.delete(tenantId == null || tenantId.isBlank() ? "default" : tenantId, ownerId, conversationId);
        }
    }

    private void rememberWorking(MemoryItem item) {
        if (item.conversationId() == null || item.conversationId().isBlank()) return;
        Deque<MemoryItem> window = working.computeIfAbsent(
                workingKey(item.tenantId(), item.ownerId(), item.conversationId()), key -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(item);
            while (window.size() > Math.max(1, properties.getWorkingCapacity())) window.removeFirst();
        }
    }

    private double score(MemoryItem item, String query, Instant now) {
        double ageHours = Math.max(0, Duration.between(item.createdAt(), now).toMinutes() / 60.0);
        double recency = Math.exp(-ageHours / (item.tier() == MemoryTier.LONG_TERM ? 720.0 : 72.0));
        return properties.getRecencyWeight() * recency
                + properties.getRelevanceWeight() * lexicalRelevance(item.content(), query)
                + properties.getImportanceWeight() * item.importance()
                + properties.getAccessWeight()
                        * Math.min(1.0, Math.log1p(Math.max(0, item.accessCount())) / Math.log(11));
    }

    static double lexicalRelevance(String content, String query) {
        if (query == null || query.isBlank()) return 0;
        Set<String> terms =
                new HashSet<>(Arrays.asList(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
        terms.remove("");
        if (terms.isEmpty()) return 0;
        String normalized = content.toLowerCase(Locale.ROOT);
        long matches = terms.stream().filter(normalized::contains).count();
        return (double) matches / terms.size();
    }

    private static String normalize(String content) {
        return content == null ? "" : content.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static WorkingKey workingKey(String tenantId, String ownerId, String conversationId) {
        return new WorkingKey(tenantId, ownerId, conversationId);
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
