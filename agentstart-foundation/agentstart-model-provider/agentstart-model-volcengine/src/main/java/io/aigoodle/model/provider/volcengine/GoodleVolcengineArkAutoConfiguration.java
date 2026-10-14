package io.aigoodle.model.provider.volcengine;

import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = GoodleModelAutoConfiguration.class)
@ConditionalOnClass(OpenAiChatModel.class)
public class GoodleVolcengineArkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "volcengineArkModelProvider")
    public VolcengineArkModelProvider volcengineArkModelProvider() {
        return new VolcengineArkModelProvider();
    }
}
