package io.aigoodle.mcp.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.mcp.auth.aop.McpAuthorizationAspect;
import io.aigoodle.mcp.auth.internal.MissingTokenAuthenticator;
import io.aigoodle.mcp.auth.internal.TransportContextCredentialExtractor;
import io.aigoodle.mcp.auth.jwt.HmacJwtAuthenticator;
import io.aigoodle.mcp.auth.spi.McpCredentialExtractor;
import io.aigoodle.mcp.auth.spi.McpTokenAuthenticator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(McpAuthProperties.class)
@ConditionalOnProperty(prefix = "spring-agent.mcp.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    McpCredentialExtractor mcpCredentialExtractor(McpAuthProperties properties) {
        return new TransportContextCredentialExtractor(properties.getContextKeys());
    }

    @Bean
    @ConditionalOnMissingBean(McpTokenAuthenticator.class)
    @ConditionalOnProperty(prefix = "spring-agent.mcp.auth.jwt", name = "enabled", havingValue = "true")
    McpTokenAuthenticator jwtMcpTokenAuthenticator(
            McpAuthProperties properties, ObjectProvider<ObjectMapper> objectMapper) {
        return new HmacJwtAuthenticator(properties.getJwt(), objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(McpTokenAuthenticator.class)
    McpTokenAuthenticator missingMcpTokenAuthenticator() {
        return new MissingTokenAuthenticator();
    }

    @Bean
    @ConditionalOnMissingBean
    McpAuthorizationAspect mcpAuthorizationAspect(
            McpCredentialExtractor extractor, McpTokenAuthenticator authenticator) {
        return new McpAuthorizationAspect(extractor, authenticator);
    }
}
