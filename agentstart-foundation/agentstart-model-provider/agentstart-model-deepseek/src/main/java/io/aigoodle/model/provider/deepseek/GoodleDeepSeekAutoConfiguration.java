package io.aigoodle.model.provider.deepseek;

import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Publishes a {@link DeepSeekModelProvider} bean. Runs before the model auto-config
 * so it lands in the {@code ObjectProvider<ModelProvider>} feed and beats the
 * built-in {@code "deepseek"} preset.
 */
@AutoConfiguration(before = GoodleModelAutoConfiguration.class)
@ConditionalOnClass(DeepSeekChatModel.class)
public class GoodleDeepSeekAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "deepSeekModelProvider")
    public DeepSeekModelProvider deepSeekModelProvider() {
        return new DeepSeekModelProvider();
    }
}
