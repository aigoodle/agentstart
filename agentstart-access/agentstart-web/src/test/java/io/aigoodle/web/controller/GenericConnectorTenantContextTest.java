package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.execution.ConnectorExecutionQueryService;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.execution.ConnectorResult;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.registry.ConnectorRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenericConnectorTenantContextTest {
    @BeforeEach
    void context() {
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("operator-1")
                .build());
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void connectionEndpointsUseTrustedTenantOnly() {
        ConnectorConnectionService service = mock(ConnectorConnectionService.class);
        when(service.list(anyString())).thenReturn(List.of());
        ConnectorConnectionController controller = new ConnectorConnectionController(service);
        ConnectorConnectionService.SaveConnectionRequest forged = new ConnectorConnectionService.SaveConnectionRequest(
                null, "forged-tenant", "installation-1", "ERP", Map.of("token", "secret"), Map.of());

        controller.list();
        controller.save(forged);
        controller.delete("connection-1");
        controller.test("connection-2");

        verify(service).list("trusted-tenant");
        verify(service).save(argThat(value -> "trusted-tenant".equals(value.tenantId())));
        verify(service).delete("connection-1", "trusted-tenant");
        verify(service).test("connection-2", "trusted-tenant");
    }

    @Test
    void installationAndAuditQueriesUseTrustedTenantOnly() {
        ConnectorInstallationService installations = mock(ConnectorInstallationService.class);
        when(installations.list(anyString())).thenReturn(List.of());
        when(installations.synchronize(anyString())).thenReturn(List.of());
        ConnectorInstallationController controller = new ConnectorInstallationController(installations);
        controller.list();
        controller.synchronize();
        controller.enable("installation-1");
        controller.disable("installation-2");
        verify(installations).list("trusted-tenant");
        verify(installations).synchronize("trusted-tenant");
        verify(installations).setEnabled("installation-1", "trusted-tenant", true);
        verify(installations).setEnabled("installation-2", "trusted-tenant", false);

        ConnectorExecutionQueryService executions = mock(ConnectorExecutionQueryService.class);
        when(executions.list(anyString(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());
        new ConnectorExecutionController(executions).list(null, null, null, 20);
        verify(executions).list("trusted-tenant", null, null, null, 20);
    }

    @Test
    void directExecutionBuildsContextFromTrustedIdentity() {
        ConnectorExecutionGateway gateway = mock(ConnectorExecutionGateway.class);
        when(gateway.execute(any())).thenReturn(ConnectorResult.success(Map.of()));
        ConnectorController controller = new ConnectorController(mock(ConnectorRegistry.class), gateway);

        controller.execute("openclaw", "tool", "invoke", Map.of());

        ArgumentCaptor<ConnectorExecutionRequest> request = ArgumentCaptor.forClass(ConnectorExecutionRequest.class);
        verify(gateway).execute(request.capture());
        assertThat(request.getValue().context().tenantId()).isEqualTo("trusted-tenant");
        assertThat(request.getValue().context().userId()).isEqualTo("operator-1");
    }
}
