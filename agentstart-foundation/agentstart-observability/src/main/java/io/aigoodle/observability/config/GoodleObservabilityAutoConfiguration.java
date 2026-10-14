package io.aigoodle.observability.config;

import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.runtime.ChatModelDecorator;
import io.aigoodle.observability.mapper.LlmCallRecordMapper;
import io.aigoodle.observability.metering.MeteringChatModelDecorator;
import io.aigoodle.observability.service.LlmMetricsService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for LLMOps: the metrics service plus the metering decorator that
 * the model module applies to every chat model. Disable with
 * {@code spring-agent.observability.enabled=false}.
 */
@AutoConfiguration(
        before = GoodleModelAutoConfiguration.class,
        after = io.aigoodle.persistence.TenantPersistenceAutoConfiguration.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
@MapperScan("io.aigoodle.observability.mapper")
public class GoodleObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LlmMetricsService llmMetricsService(
            LlmCallRecordMapper callRecordMapper, ObservabilityProperties observabilityProperties) {
        return new LlmMetricsService(callRecordMapper, observabilityProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "meteringChatModelDecorator")
    @ConditionalOnProperty(
            prefix = "spring-agent.observability",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ChatModelDecorator meteringChatModelDecorator(LlmMetricsService metricsService) {
        return new MeteringChatModelDecorator(metricsService);
    }
}
