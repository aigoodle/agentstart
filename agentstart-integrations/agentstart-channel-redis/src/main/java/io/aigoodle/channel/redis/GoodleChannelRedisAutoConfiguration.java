package io.aigoodle.channel.redis;

import io.aigoodle.channel.ChannelOutboundRateLimiter;
import io.aigoodle.channel.config.GoodleChannelAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Activates shared channel send quotas when the embedding host supplies Spring Data Redis. */
@AutoConfiguration(after = DataRedisAutoConfiguration.class, before = GoodleChannelAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(
        prefix = "spring-agent.channel.rate-limit",
        name = "backend",
        havingValue = "redis",
        matchIfMissing = true)
public class GoodleChannelRedisAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChannelOutboundRateLimiter.class)
    public ChannelOutboundRateLimiter redisChannelOutboundRateLimiter(
            StringRedisTemplate redis,
            @Value("${spring-agent.channel.rate-limit.key-prefix:agent-start:channel-rate}") String keyPrefix) {
        return new RedisChannelOutboundRateLimiter(redis, keyPrefix);
    }
}
