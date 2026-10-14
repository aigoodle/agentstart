package io.aigoodle.api.config;

import io.aigoodle.api.ApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ApiErrorsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ApiExceptionHandler.class)
    public ApiExceptionHandler agentStartApiExceptionHandler() {
        return new ApiExceptionHandler();
    }
}
