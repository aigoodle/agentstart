package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.service.AppMetricsService;
import io.aigoodle.agent.service.AppMetricsView;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint for conversation activity derived from an application's messages. */
@RestController
@ConditionalOnBean(AppMetricsService.class)
@RequestMapping("/apps/{appId}/metrics")
public class AppMetricsController {

    private final AppMetricsService metricsService;
    private final AppService apps;

    public AppMetricsController(AppMetricsService metricsService, AppService apps) {
        this.metricsService = metricsService;
        this.apps = apps;
    }

    @GetMapping
    public ApiResponse<AppMetricsView> metrics(@PathVariable String appId) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(metricsService.summarize(currentTenantId(), appId));
    }
}
