package io.aigoodle.model.provider.kimi;

import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.builtin.BuiltinModelProviders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Optional provider preset using the shared OpenAI-compatible transport. */
@AutoConfiguration(before = GoodleModelAutoConfiguration.class)
public class GoodleKimiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "kimiModelProvider")
    public ModelProvider kimiModelProvider() {
        return BuiltinModelProviders.moonshot();
    }
}
