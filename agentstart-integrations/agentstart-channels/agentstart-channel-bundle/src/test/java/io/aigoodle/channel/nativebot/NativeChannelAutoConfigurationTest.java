package io.aigoodle.channel.nativebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.ChannelEventLogService;
import io.aigoodle.channel.ChannelInboundDispatcher;
import io.aigoodle.channel.core.GoodleChannelCoreAutoConfiguration;
import io.aigoodle.channel.dingtalk.DingTalkChannelAutoConfiguration;
import io.aigoodle.channel.email.EmailChannelAutoConfiguration;
import io.aigoodle.channel.feishu.FeishuChannelAutoConfiguration;
import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.channel.qqbot.QQBotChannelAutoConfiguration;
import io.aigoodle.channel.webhook.WebhookChannelAutoConfiguration;
import io.aigoodle.channel.wecom.WeComChannelAutoConfiguration;
import io.aigoodle.common.crypto.TenantSecretCodec;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NativeChannelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GoodleChannelCoreAutoConfiguration.class,
                    NativeChannelAutoConfiguration.class,
                    QQBotChannelAutoConfiguration.class,
                    WeComChannelAutoConfiguration.class,
                    FeishuChannelAutoConfiguration.class,
                    DingTalkChannelAutoConfiguration.class,
                    EmailChannelAutoConfiguration.class,
                    WebhookChannelAutoConfiguration.class))
            .withUserConfiguration(HostBeans.class);

    @Test
    void registersAllAdaptersRuntimePersistenceBridgeAndCallbackController() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NativeChannelRuntimeProvider.class);
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(NativeAccountStore.class);
            assertThat(context).hasSingleBean(NativeInboundBridge.class);
            assertThat(context).hasSingleBean(NativeChannelCallbackController.class);

            Set<String> ids = context.getBeansOfType(NativeChannel.class).values().stream()
                    .map(NativeChannel::id)
                    .collect(Collectors.toSet());
            assertThat(ids).containsExactlyInAnyOrder("qqbot", "feishu", "dingtalk", "wecom", "email", "webhook");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class HostBeans {
        @Bean
        ChannelConnectionMapper channelConnectionMapper() {
            return mock(ChannelConnectionMapper.class);
        }

        @Bean
        TenantSecretCodec channelSecretCodec() {
            return mock(TenantSecretCodec.class);
        }

        @Bean
        ChannelInboundDispatcher channelInboundDispatcher() {
            return mock(ChannelInboundDispatcher.class);
        }

        @Bean
        ChannelEventLogService channelEventLogService() {
            return mock(ChannelEventLogService.class);
        }
    }
}
