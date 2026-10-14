package io.aigoodle.channel.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Installs only the Feishu channel adapter when this platform module is present. */
@AutoConfiguration(before = NativeChannelAutoConfiguration.class)
public class FeishuChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(FeishuChannel.class)
    FeishuChannel feishuChannel(ObjectMapper j) {
        return new FeishuChannel(j);
    }
}
