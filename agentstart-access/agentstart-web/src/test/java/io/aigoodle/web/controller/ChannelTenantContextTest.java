package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.channel.ChannelAgentBindingService;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelCatalogService;
import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelEventLogService;
import io.aigoodle.channel.ChannelRuntimeRegistry;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.web.support.DefaultChannelAdministrationPolicy;
import io.aigoodle.web.support.DefaultChannelRuntimeAdministrationPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChannelTenantContextTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void channelConnectionSaveOverridesAnUntrustedBodyTenant() {
        ChannelConnectionService service = mock(ChannelConnectionService.class);
        when(service.save(any())).thenReturn(null);
        ChannelConnectionController controller = new ChannelConnectionController(service);
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("operator-1")
                .build());

        controller.save(new ChannelConnectionService.SaveRequest(
                null,
                "forged-tenant",
                "USER",
                "employee-1",
                "openclaw",
                "qqbot",
                "QQ",
                Map.of("token", "secret"),
                Map.of(),
                true,
                null,
                null));

        verify(service).save(argThat(request -> "trusted-tenant".equals(request.tenantId())));
    }

    @Test
    void messageQueriesAndMutationsAlwaysUseTheTrustedTenant() {
        ChannelEventLogService service = mock(ChannelEventLogService.class);
        when(service.list(anyString(), isNull(), anyInt())).thenReturn(List.of());
        when(service.page(anyString(), isNull(), anyString(), isNull(), anyInt()))
                .thenReturn(new ChannelEventLogService.Page(List.of(), null, false));
        ChannelEventQueryController controller = new ChannelEventQueryController(service);
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("operator-1")
                .build());

        controller.list(null, 20);
        controller.page(null, "conversation-1", null, 20);
        controller.handoff("event-1", Map.of("note", "客服处理"));
        controller.reply(
                "event-1", new ChannelEventQueryController.ReplyRequest("你好", null, List.of(), Map.of(), null));

        verify(service).list("trusted-tenant", null, 20);
        verify(service).page("trusted-tenant", null, "conversation-1", null, 20);
        verify(service).handoff("trusted-tenant", "event-1", "客服处理", null);
        verify(service)
                .manualReply(
                        "trusted-tenant", "event-1", "你好", null, List.of(), Map.of(), null, "EMPLOYEE", "operator-1");
    }

    @Test
    void tenantAgentBindingUsesOnlyTrustedContextAndWritesAudit() {
        ChannelAgentBindingService service = mock(ChannelAgentBindingService.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        when(service.saveTenant(eq("trusted-tenant"), eq("agent-1"), eq("version-1"), isNull(), isNull(), eq(true)))
                .thenReturn(new ChannelAgentBindingService.TenantBinding(
                        "binding-1", "trusted-tenant", "agent-1", "version-1", null, null, 4, true));
        ChannelAgentBindingController controller = new ChannelAgentBindingController(
                service, new DefaultChannelAdministrationPolicy(), audits, mock(AgentVersionService.class));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("admin-1")
                .roles(Set.of("TENANT_ADMIN"))
                .build());

        controller.saveCurrentTenant(
                new ChannelAgentBindingController.TenantRequest("agent-1", "version-1", null, null, true));

        verify(service).saveTenant("trusted-tenant", "agent-1", "version-1", null, null, true);
        verify(audits)
                .success(
                        eq("TENANT_AGENT_BINDING_SAVE"),
                        eq("TENANT_AGENT_BINDING"),
                        eq("trusted-tenant"),
                        argThat(details -> "agent-1".equals(details.get("agentId"))));
    }

    @Test
    void connectionAgentOverrideRequiresAdministratorAndRunnableTenantVersion() {
        ChannelConnectionService service = mock(ChannelConnectionService.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        io.aigoodle.web.support.ChannelOwnershipPolicy ownership =
                mock(io.aigoodle.web.support.ChannelOwnershipPolicy.class);
        io.aigoodle.web.support.ChannelAdministrationPolicy administration =
                mock(io.aigoodle.web.support.ChannelAdministrationPolicy.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        when(service.save(any())).thenAnswer(invocation -> {
            ChannelConnectionService.SaveRequest request = invocation.getArgument(0);
            return new ChannelConnectionService.View(
                    "connection-1",
                    request.tenantId(),
                    request.ownerType(),
                    request.ownerId(),
                    request.provider(),
                    request.channelId(),
                    request.name(),
                    "ACTIVE",
                    "ONLINE",
                    "account-1",
                    request.agentId(),
                    request.agentVersionId(),
                    "openclaw-default",
                    Map.of(),
                    true,
                    null,
                    null,
                    1);
        });
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("admin-1")
                .build());
        ChannelConnectionController controller =
                new ChannelConnectionController(service, audits, ownership, administration, versions);

        controller.save(new ChannelConnectionService.SaveRequest(
                null,
                "forged",
                "USER",
                "employee-1",
                "openclaw",
                "qqbot",
                "QQ",
                Map.of(),
                Map.of(),
                true,
                "agent-1",
                "version-1",
                "openclaw-default"));

        verify(administration).requireAdministrator();
        verify(versions).requireRunnable("trusted-tenant", "agent-1", "version-1");
        verify(audits)
                .success(
                        eq("CHANNEL_CONNECTION_SAVE"),
                        eq("CHANNEL_CONNECTION"),
                        eq("connection-1"),
                        argThat(details -> Boolean.TRUE.equals(details.get("agentBindingChanged"))
                                && "agent-1".equals(details.get("agentId"))
                                && "version-1".equals(details.get("agentVersionId"))));
    }

    @Test
    void nonAdministratorCannotSetConnectionLevelAgentOverride() {
        ChannelConnectionService service = mock(ChannelConnectionService.class);
        io.aigoodle.web.support.ChannelAdministrationPolicy administration =
                mock(io.aigoodle.web.support.ChannelAdministrationPolicy.class);
        doThrow(new SecurityException("forbidden")).when(administration).requireAdministrator();
        ChannelConnectionController controller = new ChannelConnectionController(
                service,
                mock(ChannelAuditService.class),
                mock(io.aigoodle.web.support.ChannelOwnershipPolicy.class),
                administration,
                mock(AgentVersionService.class));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("employee-1")
                .build());

        assertThatThrownBy(() -> controller.save(new ChannelConnectionService.SaveRequest(
                        null,
                        "forged",
                        "USER",
                        "employee-1",
                        "openclaw",
                        "qqbot",
                        "QQ",
                        Map.of(),
                        Map.of(),
                        true,
                        "agent-1",
                        null,
                        "openclaw-default")))
                .isInstanceOf(SecurityException.class);
        verify(service, never()).save(any());
    }

    @Test
    void currentTenantBindingEndpointNeedsNoCallerSuppliedTenant() {
        ChannelAgentBindingService service = mock(ChannelAgentBindingService.class);
        ChannelAgentBindingService.TenantBinding expected = new ChannelAgentBindingService.TenantBinding(
                "binding-1", "trusted-tenant", "agent-1", "version-1", null, null, 1, true);
        when(service.tenant("trusted-tenant")).thenReturn(expected);
        ChannelAgentBindingController controller = new ChannelAgentBindingController(
                service, new DefaultChannelAdministrationPolicy(), mock(ChannelAuditService.class));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("employee-1")
                .build());

        assertThat(controller.currentTenant().getData()).isSameAs(expected);
        verify(service).tenant("trusted-tenant");
    }

    @Test
    void nonAdministratorCannotChangeEmployeeAgentBinding() {
        ChannelAgentBindingService service = mock(ChannelAgentBindingService.class);
        ChannelAgentBindingController controller = new ChannelAgentBindingController(
                service, new DefaultChannelAdministrationPolicy(), mock(ChannelAuditService.class));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("tenant-a")
                .userId("employee-1")
                .roles(Set.of("EMPLOYEE"))
                .build());

        assertThatThrownBy(() -> controller.saveEmployee(
                        "employee-2", new ChannelAgentBindingController.EmployeeRequest("agent-1", null, true)))
                .hasMessageContaining("403");
        verifyNoInteractions(service);
    }

    @Test
    void tenantAdministratorCannotEnumerateSharedRuntimeAccounts() {
        ChannelRuntimeRegistry runtimes = mock(ChannelRuntimeRegistry.class);
        ChannelController controller = new ChannelController(
                runtimes,
                mock(ChannelCatalogService.class),
                new DefaultChannelRuntimeAdministrationPolicy(),
                mock(ChannelAuditService.class));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("tenant-a")
                .userId("admin-a")
                .roles(Set.of("TENANT_ADMIN"))
                .build());

        assertThatThrownBy(() -> controller.accounts("openclaw", "qqbot")).hasMessageContaining("403");
        verifyNoInteractions(runtimes);
    }
}
