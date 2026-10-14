package io.aigoodle.mcp.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.mcp.auth.aop.McpAuthorizationAspect;
import io.aigoodle.mcp.auth.jwt.HmacJwtAuthenticator;
import io.aigoodle.mcp.auth.spi.McpTokenAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class McpAuthAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(McpAuthAutoConfiguration.class));

    @Test
    void createsJwtAuthenticatorWhenConfigured() {
        runner.withPropertyValues(
                        "spring-agent.mcp.auth.jwt.enabled=true",
                        "spring-agent.mcp.auth.jwt.secret=01234567890123456789012345678901")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpAuthorizationAspect.class);
                    assertThat(context).hasSingleBean(McpTokenAuthenticator.class);
                    assertThat(context.getBean(McpTokenAuthenticator.class)).isInstanceOf(HmacJwtAuthenticator.class);
                });
    }

    @Test
    void applicationAuthenticatorOverridesBuiltInJwt() {
        runner.withUserConfiguration(CustomAuth.class)
                .withPropertyValues(
                        "spring-agent.mcp.auth.jwt.enabled=true",
                        "spring-agent.mcp.auth.jwt.secret=01234567890123456789012345678901")
                .run(context -> assertThat(context.getBean(McpTokenAuthenticator.class))
                        .isSameAs(context.getBean("customAuthenticator")));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAuth {
        @Bean
        McpTokenAuthenticator customAuthenticator() {
            return token -> CurrentUser.builder().userId("custom").build();
        }
    }
}
