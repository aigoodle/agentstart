package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.observability.api.LlmTrendPoint;
import io.aigoodle.observability.api.LlmTrendRange;
import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.entity.LlmCallRecord;
import io.aigoodle.observability.service.LlmMetricsService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/** Application-scoped model usage, with the same access checks as application metrics. */
@RestController
@ConditionalOnBean({LlmMetricsService.class, AppService.class})
@RequestMapping("/apps/{appId}/metrics/llm")
public class AppObservabilityController {
    private final LlmMetricsService metrics;
    private final AppService apps;

    public AppObservabilityController(LlmMetricsService metrics, AppService apps) {
        this.metrics = metrics;
        this.apps = apps;
    }

    @GetMapping("/total")
    public ApiResponse<LlmUsageStats> total(@PathVariable String appId) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(metrics.total(currentTenantId(), appId));
    }

    @GetMapping("/recent")
    public ApiResponse<List<LlmCallRecord>> recent(
            @PathVariable String appId, @RequestParam(defaultValue = "10") int limit) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(metrics.recentCalls(currentTenantId(), appId, limit));
    }

    @GetMapping("/trend")
    public ApiResponse<List<LlmTrendPoint>> trend(
            @PathVariable String appId, @RequestParam(defaultValue = "HOUR") LlmTrendRange range) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(metrics.trend(currentTenantId(), appId, range));
    }
}
