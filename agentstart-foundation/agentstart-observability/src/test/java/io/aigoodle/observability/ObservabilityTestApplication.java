package io.aigoodle.observability;

import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.observability.support.UsageChatProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
public class ObservabilityTestApplication {

    @Bean
    public ModelProvider usageChatProvider() {
        return new UsageChatProvider();
    }
}
