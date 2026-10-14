package io.aigoodle.memory;

import java.time.Clock;
import java.time.Instant;

/** Explicit maintenance API suitable for an application's scheduler or operations endpoint. */
public final class MemoryMaintenance {

    private final MemoryStore store;
    private final Clock clock;

    public MemoryMaintenance(MemoryStore store) {
        this(store, Clock.systemUTC());
    }

    MemoryMaintenance(MemoryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public MaintenanceResult runOnce() {
        Instant startedAt = clock.instant();
        store.purgeExpired(startedAt);
        return new MaintenanceResult(startedAt, clock.instant());
    }

    public record MaintenanceResult(Instant startedAt, Instant completedAt) {}
}
