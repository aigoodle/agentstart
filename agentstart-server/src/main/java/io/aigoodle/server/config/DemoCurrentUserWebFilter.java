package io.aigoodle.server.config;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Local standalone-server identity; production hosts must supply their own trusted principal bridge. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "spring-agent.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoCurrentUserWebFilter implements WebFilter {

    private final String defaultUserId;
    private final String defaultUsername;
    private final String defaultTenantId;
    private final Set<String> defaultRoles;

    public DemoCurrentUserWebFilter(
            @Value("${spring-agent.demo.user-id:demo-user}") String defaultUserId,
            @Value("${spring-agent.demo.username:Demo Administrator}") String defaultUsername,
            @Value("${spring-agent.demo.tenant-id:default}") String defaultTenantId,
            @Value("${spring-agent.demo.roles:TENANT_ADMIN,CONNECTOR_ADMIN}") String defaultRoles) {
        this.defaultUserId = defaultUserId;
        this.defaultUsername = defaultUsername;
        this.defaultTenantId = defaultTenantId;
        this.defaultRoles = roles(defaultRoles);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        CurrentUser user = CurrentUser.builder()
                // Demo identity is deployment configuration, never caller-selected HTTP input.
                // Production embedding hosts replace this filter with their authenticated bridge.
                .userId(defaultUserId)
                .username(defaultUsername)
                .tenantId(defaultTenantId)
                .roles(defaultRoles)
                .principalType(PrincipalType.USER)
                .build();
        String authorization = exchange.getRequest().getHeaders().getFirst("X-MCP-Authorization");
        if (authorization == null || authorization.isBlank()) {
            authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        }
        if (authorization != null && !authorization.isBlank()) user.put("authorization", authorization);
        return chain.filter(exchange).contextWrite(context -> context.put(UserContextHolder.CONTEXT_KEY, user));
    }

    static Set<String> roles(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
