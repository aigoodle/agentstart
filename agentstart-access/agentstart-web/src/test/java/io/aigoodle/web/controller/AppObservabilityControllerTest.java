package io.aigoodle.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.observability.api.LlmTrendRange;
import io.aigoodle.observability.service.LlmMetricsService;
import org.junit.jupiter.api.Test;

class AppObservabilityControllerTest {
    @Test
    void checksAppAccessBeforeQueryingTenantScopedUsage() {
        var apps = mock(AppService.class);
        var metrics = mock(LlmMetricsService.class);
        var controller = new AppObservabilityController(metrics, apps);
        UserContextHolder.callAs(
                CurrentUser.builder().tenantId("tenant-a").userId("u").build(), () -> {
                    controller.total("app-a");
                    controller.recent("app-a", 10);
                    controller.trend("app-a", LlmTrendRange.HOUR);
                    return null;
                });
        verify(apps, times(3)).require("tenant-a", "app-a");
        verify(metrics).total("tenant-a", "app-a");
        verify(metrics).recentCalls("tenant-a", "app-a", 10);
        verify(metrics).trend("tenant-a", "app-a", LlmTrendRange.HOUR);
    }

    @Test
    void inaccessibleAppNeverQueriesUsage() {
        var apps = mock(AppService.class);
        var metrics = mock(LlmMetricsService.class);
        when(apps.require("default", "hidden")).thenThrow(new SecurityException("Denied"));
        var controller = new AppObservabilityController(metrics, apps);
        assertThrows(SecurityException.class, () -> controller.total("hidden"));
        assertThrows(SecurityException.class, () -> controller.recent("hidden", 10));
        assertThrows(SecurityException.class, () -> controller.trend("hidden", LlmTrendRange.DAY));
        verifyNoInteractions(metrics);
    }
}
