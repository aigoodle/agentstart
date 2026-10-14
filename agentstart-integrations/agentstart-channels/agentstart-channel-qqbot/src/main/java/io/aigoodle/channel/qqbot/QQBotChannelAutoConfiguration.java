package io.aigoodle.channel.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Installs only the QQBot channel adapter when this platform module is present. */
@AutoConfiguration(before = NativeChannelAutoConfiguration.class)
public class QQBotChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(QQBotChannel.class)
    QQBotChannel qqbotChannel(ObjectMapper j) {
        return new QQBotChannel(j);
    }
}
