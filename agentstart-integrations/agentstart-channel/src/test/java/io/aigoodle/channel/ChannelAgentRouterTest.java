package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelAgentRouterTest {
    private final ChannelConnectionService connections = mock(ChannelConnectionService.class);
    private final ChannelAgentBindingService bindings = mock(ChannelAgentBindingService.class);
    private final ChannelAgentRouter router = new ChannelAgentRouter(connections, bindings);
    private final ChannelInboundEvent event = new ChannelInboundEvent(
            "openclaw",
            "qqbot",
            "account-1",
            "message-1",
            "sender-1",
            "conversation-1",
            "hello",
            Instant.now(),
            false,
            Map.of());

    @Test
    void explicitConnectionAgentWins() {
        connection("connection-agent", true);
        ChannelAgentRouter.Result result = router.route(event);
        assertThat(result.agentId()).isEqualTo("connection-agent");
        assertThat(result.source()).isEqualTo(ChannelAgentRouter.Source.CONNECTION);
        verify(bindings).tenant("tenant-a");
        verify(bindings, never()).employee(anyString(), anyString());
    }

    @Test
    void employeeAgentWinsBeforeTenantDefault() {
        connection(null, true);
        when(bindings.employee("tenant-a", "employee-1"))
                .thenReturn(new ChannelAgentBindingService.EmployeeBinding(
                        "binding-1", "tenant-a", "employee-1", "employee-agent", true));
        ChannelAgentRouter.Result result = router.route(event);
        assertThat(result.agentId()).isEqualTo("employee-agent");
        assertThat(result.source()).isEqualTo(ChannelAgentRouter.Source.EMPLOYEE);
        verify(bindings).tenant("tenant-a");
    }

    @Test
    void tenantDefaultHandlesEmployeesWithoutDedicatedAgent() {
        connection(null, true);
        when(bindings.tenant("tenant-a"))
                .thenReturn(new ChannelAgentBindingService.TenantBinding(
                        "binding-1", "tenant-a", "tenant-agent", "fallback-agent", true));
        ChannelAgentRouter.Result result = router.route(event);
        assertThat(result.agentId()).isEqualTo("tenant-agent");
        assertThat(result.source()).isEqualTo(ChannelAgentRouter.Source.TENANT_DEFAULT);
    }

    @Test
    void unmanagedOrInactiveConnectionIsSilent() {
        connection(null, false);
        ChannelAgentRouter.Result result = router.route(event);
        assertThat(result.routed()).isFalse();
        assertThat(result.source()).isEqualTo(ChannelAgentRouter.Source.NONE);
        assertThat(result.unboundPolicy()).isEqualTo(ChannelAgentRouter.UnboundPolicy.SILENT);
        assertThat(result.managed()).isFalse();
        verifyNoInteractions(bindings);
    }

    @Test
    void activeConnectionWithoutAnyBindingIsManagedAndSilent() {
        connection(null, true);
        ChannelAgentRouter.Result result = router.route(event);
        assertThat(result.routed()).isFalse();
        assertThat(result.managed()).isTrue();
        assertThat(result.source()).isEqualTo(ChannelAgentRouter.Source.NONE);
    }

    @Test
    void runtimeNodeIsPartOfTheRoutingLookup() {
        ChannelInboundEvent nodeEvent = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-1",
                "sender-1",
                "conversation-1",
                "hello",
                "TEXT",
                java.util.List.of(),
                Map.of(),
                Instant.now(),
                false,
                Map.of(),
                "node-b");
        when(connections.routingConnection("openclaw", "node-b", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.RoutingConnection(
                        "connection-b", "tenant-b", "employee-b", "agent-b", true));

        ChannelAgentRouter.Result result = router.route(nodeEvent);

        assertThat(result.tenantId()).isEqualTo("tenant-b");
        assertThat(result.agentId()).isEqualTo("agent-b");
        verify(connections).routingConnection("openclaw", "node-b", "qqbot", "account-1");
        verify(connections, never()).routingConnection("openclaw", "qqbot", "account-1");
    }

    private void connection(String agentId, boolean active) {
        when(connections.routingConnection("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.RoutingConnection(
                        "connection-1", "tenant-a", "employee-1", agentId, active));
    }
}
