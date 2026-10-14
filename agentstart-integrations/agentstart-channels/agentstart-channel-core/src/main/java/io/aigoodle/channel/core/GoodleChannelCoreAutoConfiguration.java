package io.aigoodle.channel.core;

import io.aigoodle.channel.api.Channel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GoodleChannelCoreAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ChannelRegistry channelRegistry(ObjectProvider<Channel<?>> channels) {
        return new ChannelRegistry(channels.orderedStream().toList());
    }
}
