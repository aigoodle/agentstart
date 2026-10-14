package io.aigoodle.channel.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Installs only the WeCom channel adapter when this platform module is present. */
@AutoConfiguration(before = NativeChannelAutoConfiguration.class)
public class WeComChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WeComChannel.class)
    WeComChannel wecomChannel(ObjectMapper j) {
        return new WeComChannel(j);
    }
}
