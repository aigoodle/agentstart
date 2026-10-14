package io.aigoodle.channel.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.aigoodle.channel.ChannelOutboundRateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisChannelOutboundRateLimiterTest {
    @Test
    void returnsRedisAtomicWindowDelayWithoutSleepingTheOutboxWorker() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L, 437L);
        RedisChannelOutboundRateLimiter limiter = new RedisChannelOutboundRateLimiter(redis, "quota");
        ChannelOutboundRateLimiter.Scope scope =
                new ChannelOutboundRateLimiter.Scope("tenant-a", "openclaw", "node-1", "account-1");

        assertThat(limiter.acquire(scope, 10)).isZero();
        assertThat(limiter.acquire(scope, 10)).isEqualTo(Duration.ofMillis(437));
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of(limiter.key(scope))), eq("1000"), eq("10"));
    }

    @Test
    void keySeparatesTenantProviderRuntimeNodeAndAccountWithoutDelimiterCollisions() {
        RedisChannelOutboundRateLimiter limiter =
                new RedisChannelOutboundRateLimiter(mock(StringRedisTemplate.class), "quota");
        String baseline =
                limiter.key(new ChannelOutboundRateLimiter.Scope("tenant:a", "openclaw", "node:1", "account:1"));

        assertThat(List.of(
                        limiter.key(
                                new ChannelOutboundRateLimiter.Scope("tenant-b", "openclaw", "node:1", "account:1")),
                        limiter.key(new ChannelOutboundRateLimiter.Scope("tenant:a", "hermes", "node:1", "account:1")),
                        limiter.key(
                                new ChannelOutboundRateLimiter.Scope("tenant:a", "openclaw", "node:2", "account:1")),
                        limiter.key(
                                new ChannelOutboundRateLimiter.Scope("tenant:a", "openclaw", "node:1", "account:2"))))
                .doesNotContain(baseline)
                .doesNotHaveDuplicates();
    }

    @Test
    void redisFailureFailsClosedInsteadOfSilentlyBypassingEnterpriseQuota() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);
        RedisChannelOutboundRateLimiter limiter = new RedisChannelOutboundRateLimiter(redis, "quota");

        assertThatThrownBy(() -> limiter.acquire(
                        new ChannelOutboundRateLimiter.Scope("tenant-a", "openclaw", "node-1", "account-1"), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no result");
    }
}
