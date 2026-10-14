package io.aigoodle.model.provider.openai;

import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.builtin.BuiltinModelProviders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Optional provider preset using the shared OpenAI-compatible transport. */
@AutoConfiguration(before = GoodleModelAutoConfiguration.class)
public class GoodleOpenAiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "openAiModelProvider")
    public ModelProvider openAiModelProvider() {
        return BuiltinModelProviders.openai();
    }
}
