package io.aigoodle.mcp.auth.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.mcp.auth.annotation.McpAuthorize;
import io.aigoodle.mcp.auth.exception.McpAccessDeniedException;
import io.aigoodle.mcp.auth.internal.TransportContextCredentialExtractor;
import io.aigoodle.mcp.auth.support.McpCredentialContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class McpAuthorizationAspectTest {

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void authenticatesAuthorizesBindsAndAlwaysClearsContexts() {
        CurrentUser caller = CurrentUser.builder()
                .userId("u-1")
                .tenantId("tenant-a")
                .roles(Set.of("OPERATOR"))
                .scopes(Set.of("mes:read"))
                .build();
        Tool target = new Tool();
        Tool proxy = proxy(target, caller);

        assertThat(proxy.read(new RequestContext(Map.of("authorization", "Bearer signed-token"))))
                .isEqualTo("tenant-a|Bearer signed-token");
        assertThat(UserContextHolder.get()).isNull();
        assertThat(McpCredentialContext.current()).isEmpty();
        assertThat(target.invocations).isEqualTo(1);
    }

    @Test
    void deniesMissingPermissionBeforeBusinessMethodRuns() {
        CurrentUser caller =
                CurrentUser.builder().userId("u-1").scopes(Set.of("mes:write")).build();
        Tool target = new Tool();
        Tool proxy = proxy(target, caller);

        assertThatThrownBy(() -> proxy.read(new RequestContext(Map.of("authorization", "token"))))
                .isInstanceOf(McpAccessDeniedException.class)
                .hasMessageContaining("scope");
        assertThat(target.invocations).isZero();
        assertThat(UserContextHolder.get()).isNull();
    }

    private Tool proxy(Tool target, CurrentUser caller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new McpAuthorizationAspect(
                new TransportContextCredentialExtractor(List.of("authorization")), token -> caller));
        return factory.getProxy();
    }

    public record RequestContext(Map<String, Object> transportContext) {}

    public static class Tool {
        int invocations;

        @McpAuthorize(scopes = "mes:read")
        public String read(Object context) {
            invocations++;
            return UserContextHolder.currentTenantId() + "|" + McpCredentialContext.currentAuthorization();
        }
    }
}
