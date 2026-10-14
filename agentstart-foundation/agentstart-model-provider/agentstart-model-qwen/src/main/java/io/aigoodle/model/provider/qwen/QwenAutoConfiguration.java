package io.aigoodle.model.provider.qwen;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class QwenAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(QwenModelProvider.class)
    public QwenModelProvider qwenModelProvider() {
        return new QwenModelProvider();
    }
}
