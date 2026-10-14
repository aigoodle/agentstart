package io.aigoodle.mcp.auth.config;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webflux.autoconfigure.McpServerStreamableHttpWebFluxAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/** Replaces the stock streamable transport with one that retains request credentials. */
@AutoConfiguration(before = McpServerStreamableHttpWebFluxAutoConfiguration.class)
@ConditionalOnClass(WebFluxStreamableServerTransportProvider.class)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAuthTransportAutoConfiguration {

    public static final String AUTHORIZATION_CONTEXT_KEY = "authorization";

    @Bean
    @ConditionalOnMissingBean
    WebFluxStreamableServerTransportProvider authenticatedWebFluxStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebFluxStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    String authorization = request.headers().firstHeader("Authorization");
                    return authorization == null || authorization.isBlank()
                            ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(AUTHORIZATION_CONTEXT_KEY, authorization));
                })
                .build();
    }
}
