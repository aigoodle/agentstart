package io.aigoodle.channel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small stale-safe catalog cache; account status remains independently refreshable. */
public class ChannelCatalogService {
    public record Snapshot(List<ChannelDefinition> channels, Instant loadedAt, boolean cached) {}

    private record CacheKey(String tenantId, String runtimeNodeId) {
        private CacheKey {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            runtimeNodeId = runtimeNodeId == null || runtimeNodeId.isBlank() ? "all" : runtimeNodeId;
        }
    }

    private final ChannelRuntimeRegistry runtimes;
    private final Duration ttl;
    private final Clock clock;
    private final Map<CacheKey, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<CacheKey, Object> locks = new ConcurrentHashMap<>();

    public ChannelCatalogService(ChannelRuntimeRegistry runtimes, Duration ttl) {
        this(runtimes, ttl, Clock.systemUTC());
    }

    ChannelCatalogService(ChannelRuntimeRegistry runtimes, Duration ttl, Clock clock) {
        this.runtimes = runtimes;
        this.ttl = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
        this.clock = clock;
    }

    public Snapshot get(String tenantId, String runtimeNodeId, boolean forceRefresh) {
        CacheKey key = new CacheKey(tenantId, runtimeNodeId);
        Snapshot current = snapshots.get(key);
        Instant now = clock.instant();
        if (!forceRefresh && current != null && current.loadedAt().plus(ttl).isAfter(now))
            return new Snapshot(current.channels(), current.loadedAt(), true);
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            current = snapshots.get(key);
            now = clock.instant();
            if (!forceRefresh && current != null && current.loadedAt().plus(ttl).isAfter(now))
                return new Snapshot(current.channels(), current.loadedAt(), true);
            try {
                String selectedNode = "all".equals(key.runtimeNodeId()) ? null : key.runtimeNodeId();
                Snapshot fresh = new Snapshot(List.copyOf(runtimes.discover(selectedNode)), now, false);
                snapshots.put(key, fresh);
                return fresh;
            } catch (RuntimeException failure) {
                if (current != null) return new Snapshot(current.channels(), current.loadedAt(), true);
                throw failure;
            }
        }
    }

    public void invalidate(String tenantId, String runtimeNodeId) {
        CacheKey key = new CacheKey(tenantId, runtimeNodeId);
        snapshots.remove(key);
        locks.remove(key);
    }

    /** Runtime plugin changes can affect every tenant and node view. */
    public void invalidate() {
        snapshots.clear();
        locks.clear();
    }
}
