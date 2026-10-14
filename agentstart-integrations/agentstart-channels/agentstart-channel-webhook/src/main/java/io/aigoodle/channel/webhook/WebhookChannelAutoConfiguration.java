package io.aigoodle.channel.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Installs only the Webhook channel adapter when this platform module is present. */
@AutoConfiguration(before = NativeChannelAutoConfiguration.class)
public class WebhookChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WebhookChannel.class)
    WebhookChannel webhookChannel(ObjectMapper j) {
        return new WebhookChannel(j);
    }
}
