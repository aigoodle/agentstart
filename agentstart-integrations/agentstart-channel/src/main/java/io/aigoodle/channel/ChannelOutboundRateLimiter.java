package io.aigoodle.channel;

import java.time.Duration;

/**
 * Outbound quota SPI. The optional {@code agentstart-channel-redis} module supplies a shared,
 * atomic implementation; hosts without it retain the default single-JVM fallback.
 */
public interface ChannelOutboundRateLimiter {
    record Scope(String tenantId, String provider, String runtimeNodeId, String accountId) {
        public Scope {
            tenantId = normalized(tenantId, "default");
            provider = normalized(provider, "unknown");
            runtimeNodeId = normalized(runtimeNodeId, "default");
            accountId = normalized(accountId, "default");
        }

        private static String normalized(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }

    Duration acquire(Scope scope, int maxPerSecond);
}
