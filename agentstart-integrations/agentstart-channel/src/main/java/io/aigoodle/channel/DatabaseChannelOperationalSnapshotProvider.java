package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.channel.config.ChannelProperties;
import io.aigoodle.channel.persistence.ChannelConnectionEntity;
import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import io.aigoodle.channel.persistence.ChannelEventMapper;
import io.aigoodle.persistence.TenantSqlScope;
import java.time.Duration;

/** Cached aggregate query so one metrics scrape performs at most one database snapshot. */
public final class DatabaseChannelOperationalSnapshotProvider implements ChannelOperationalSnapshotProvider {
    private final ChannelEventMapper events;
    private final ChannelConnectionMapper connections;
    private final int maxAttempts;
    private final long cacheNanos;
    private volatile Cached cached;

    public DatabaseChannelOperationalSnapshotProvider(
            ChannelEventMapper events, ChannelConnectionMapper connections, ChannelProperties properties) {
        this(events, connections, properties.getOutboxMaxAttempts(), Duration.ofSeconds(10));
    }

    DatabaseChannelOperationalSnapshotProvider(
            ChannelEventMapper events, ChannelConnectionMapper connections, int maxAttempts, Duration cacheTtl) {
        this.events = events;
        this.connections = connections;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.cacheNanos = Math.max(0, cacheTtl.toNanos());
    }

    @Override
    public Snapshot snapshot() {
        long now = System.nanoTime();
        Cached value = cached;
        if (value != null && now - value.loadedAtNanos < cacheNanos) return value.snapshot;
        synchronized (this) {
            value = cached;
            if (value != null && now - value.loadedAtNanos < cacheNanos) return value.snapshot;
            Snapshot loaded = TenantSqlScope.bypass(this::load);
            cached = new Cached(now, loaded);
            return loaded;
        }
    }

    private Snapshot load() {
        return new Snapshot(
                eventCount("PENDING", false),
                eventCount("SENDING", false),
                eventCount("FAILED", false),
                eventCount("FAILED", true),
                connectionCount("ONLINE"),
                connectionCount("PENDING"),
                connectionCount("ERROR"),
                connectionCount("DISABLED"));
    }

    private long eventCount(String status, boolean exhausted) {
        var query = new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                .eq(ChannelEventEntity::getStatus, status);
        if ("FAILED".equals(status)) {
            if (exhausted) query.ge(ChannelEventEntity::getAttempts, maxAttempts);
            else query.lt(ChannelEventEntity::getAttempts, maxAttempts);
        }
        return events.selectCount(query);
    }

    private long connectionCount(String status) {
        return connections.selectCount(new LambdaQueryWrapper<ChannelConnectionEntity>()
                .eq(ChannelConnectionEntity::getRuntimeStatus, status));
    }

    private record Cached(long loadedAtNanos, Snapshot snapshot) {}
}
