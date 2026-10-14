package io.aigoodle.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.common.context.UserContextHolder;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class DemoCurrentUserWebFilterTest {
    @Test
    void callerCannotForgeTenantUserOrAdministrativeRolesThroughDemoHeaders() {
        DemoCurrentUserWebFilter filter =
                new DemoCurrentUserWebFilter("configured-user", "Configured User", "configured-tenant", "TENANT_ADMIN");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/agent-start/apps")
                .header("X-Tenant-Id", "forged-tenant")
                .header("X-Demo-User-Id", "forged-user")
                .header("X-Demo-Username", "Forged User")
                .header("X-Demo-Roles", "SYSTEM_ADMIN,PLATFORM_ADMIN"));

        filter.filter(
                        exchange,
                        ignored -> Mono.deferContextual(context -> {
                            var user = UserContextHolder.getReactive(context).orElseThrow();
                            assertThat(user.getTenantId()).isEqualTo("configured-tenant");
                            assertThat(user.getUserId()).isEqualTo("configured-user");
                            assertThat(user.getUsername()).isEqualTo("Configured User");
                            assertThat(user.getRoles()).isEqualTo(Set.of("TENANT_ADMIN"));
                            return Mono.empty();
                        }))
                .block();

        assertThat(UserContextHolder.get()).isNull();
    }
}
