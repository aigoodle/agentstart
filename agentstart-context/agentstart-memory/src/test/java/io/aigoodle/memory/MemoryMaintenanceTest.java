package io.aigoodle.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryMaintenanceTest {

    @Test
    void purgesAgainstTheInjectedClock() {
        Instant now = Instant.parse("2026-08-03T08:00:00Z");
        class Store implements MemoryStore {
            private Instant purgedAt;

            public void save(MemoryItem item) {}

            public List<MemoryItem> find(MemoryQuery query) {
                return List.of();
            }

            public void purgeExpired(Instant at) {
                purgedAt = at;
            }
        }
        Store store = new Store();
        MemoryMaintenance maintenance = new MemoryMaintenance(store, Clock.fixed(now, ZoneOffset.UTC));

        MemoryMaintenance.MaintenanceResult result = maintenance.runOnce();

        assertThat(store.purgedAt).isEqualTo(now);
        assertThat(result.startedAt()).isEqualTo(now);
        assertThat(result.completedAt()).isEqualTo(now);
    }
}
