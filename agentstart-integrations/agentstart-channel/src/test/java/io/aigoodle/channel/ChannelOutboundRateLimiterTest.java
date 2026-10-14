package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChannelOutboundRateLimiterTest {
    @Test
    void limitsEachTenantRuntimeNodeAndAccountIndependently() {
        ChannelOutboundRateLimiter limiter = new InMemoryChannelOutboundRateLimiter();

        assertThat(limiter.acquire(scope("tenant-a", "node-1"), 1)).isZero();
        assertThat(limiter.acquire(scope("tenant-a", "node-1"), 1)).isPositive();
        assertThat(limiter.acquire(scope("tenant-b", "node-1"), 1)).isZero();
        assertThat(limiter.acquire(scope("tenant-a", "node-2"), 1)).isZero();
    }

    private static ChannelOutboundRateLimiter.Scope scope(String tenant, String node) {
        return new ChannelOutboundRateLimiter.Scope(tenant, "openclaw", node, "account-1");
    }

    @Test
    void isolatesTwoHundredEmployeeAccountsUnderConcurrentAccess() throws Exception {
        ChannelOutboundRateLimiter limiter = new InMemoryChannelOutboundRateLimiter();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger throttled = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(32)) {
            for (int account = 0; account < 200; account++) {
                int id = account;
                executor.submit(() -> {
                    var employeeScope = new ChannelOutboundRateLimiter.Scope(
                            "tenant-a", "openclaw", "node-1", "employee-account-" + id);
                    if (limiter.acquire(employeeScope, 1).isZero()) accepted.incrementAndGet();
                    if (limiter.acquire(employeeScope, 1).isPositive()) throttled.incrementAndGet();
                });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(accepted).hasValue(200);
        assertThat(throttled).hasValue(200);
    }
}
