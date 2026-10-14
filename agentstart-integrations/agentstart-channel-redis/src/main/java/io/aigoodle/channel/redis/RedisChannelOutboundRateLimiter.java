package io.aigoodle.channel.redis;

import io.aigoodle.channel.ChannelOutboundRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Cluster-wide atomic fixed-window quota for one tenant/runtime/account scope. */
public final class RedisChannelOutboundRateLimiter implements ChannelOutboundRateLimiter {
    static final String SCRIPT =
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if count <= tonumber(ARGV[2]) then return 0 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 1 then return 1 end
            return ttl
            """;
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>(SCRIPT, Long.class);
    private static final long WINDOW_MILLIS = 1_000;

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisChannelOutboundRateLimiter(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "agent-start:channel-rate" : keyPrefix.trim();
    }

    @Override
    public Duration acquire(Scope scope, int maxPerSecond) {
        if (maxPerSecond <= 0) return Duration.ZERO;
        Long delay = redis.execute(
                ACQUIRE, List.of(key(scope)), String.valueOf(WINDOW_MILLIS), String.valueOf(maxPerSecond));
        if (delay == null) throw new IllegalStateException("Redis channel rate limiter returned no result");
        return delay <= 0 ? Duration.ZERO : Duration.ofMillis(delay);
    }

    String key(Scope scope) {
        return keyPrefix
                + ':'
                + encoded(scope.tenantId())
                + ':'
                + encoded(scope.provider())
                + ':'
                + encoded(scope.runtimeNodeId())
                + ':'
                + encoded(scope.accountId());
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
