package io.aigoodle.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Installs only the DingTalk channel adapter when this platform module is present. */
@AutoConfiguration(before = NativeChannelAutoConfiguration.class)
public class DingTalkChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DingTalkChannel.class)
    DingTalkChannel dingtalkChannel(ObjectMapper j) {
        return new DingTalkChannel(j);
    }
}
