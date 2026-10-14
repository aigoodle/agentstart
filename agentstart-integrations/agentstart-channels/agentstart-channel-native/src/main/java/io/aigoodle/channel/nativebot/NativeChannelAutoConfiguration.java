package io.aigoodle.channel.nativebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.*;
import io.aigoodle.channel.core.ChannelRegistry;
import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.common.crypto.TenantSecretCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

@AutoConfiguration(
        afterName = {
            "io.aigoodle.channel.config.GoodleChannelAutoConfiguration",
            "io.aigoodle.channel.core.GoodleChannelCoreAutoConfiguration"
        })
public class NativeChannelAutoConfiguration {
    /**
     * Channel implementations still use Jackson 2 while Spring Boot 4 auto-configures a
     * Jackson 3 {@code tools.jackson.databind.ObjectMapper}. Keep the channel mapper separate
     * from Boot's HTTP codec mapper until the channel API is migrated to Jackson 3.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper channelObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    NativeAccountStore nativeAccountStore(ChannelConnectionMapper mapper, TenantSecretCodec secrets) {
        return new JdbcNativeAccountStore(mapper, secrets);
    }

    @Bean
    @ConditionalOnMissingBean
    NativeInboundBridge nativeInboundBridge(
            ObjectProvider<ChannelInboundDispatcher> d, ObjectProvider<ChannelEventLogService> e) {
        return new NativeInboundBridge(d, e);
    }

    @Bean
    @ConditionalOnMissingBean
    NativeChannelRuntimeProvider nativeChannelRuntimeProvider(
            ChannelRegistry channelRegistry,
            ObjectMapper j,
            ObjectProvider<NativeAccountStore> store,
            NativeInboundBridge sink) {
        java.util.List<NativeChannel<?>> nativeChannels = new java.util.ArrayList<>();
        for (var channel : channelRegistry.all()) {
            if (channel instanceof NativeChannel<?> nativeChannel) {
                nativeChannels.add(nativeChannel);
            }
        }
        return new NativeChannelRuntimeProvider(nativeChannels, j, store.getIfAvailable(), sink);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> nativeAccountSessionStarter(NativeChannelRuntimeProvider runtime) {
        return event -> runtime.restoreEnabledAccounts();
    }

    @Bean
    @ConditionalOnClass(RestController.class)
    @ConditionalOnBean({ChannelInboundDispatcher.class, ChannelEventLogService.class})
    NativeChannelCallbackController nativeChannelCallbackController(
            NativeChannelRuntimeProvider r, ChannelInboundDispatcher d, ChannelEventLogService e, ObjectMapper j) {
        return new NativeChannelCallbackController(r, d, e, j);
    }
}
