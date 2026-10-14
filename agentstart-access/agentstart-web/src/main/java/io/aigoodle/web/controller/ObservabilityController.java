package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.observability.api.LlmTrendPoint;
import io.aigoodle.observability.api.LlmTrendRange;
import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.entity.LlmCallRecord;
import io.aigoodle.observability.service.LlmMetricsService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade over {@link LlmMetricsService}. Exposes the numbers an LLMOps view
 * needs: per-model aggregates, a running total and the most recent raw calls.
 * <p>
 * Only wired when the observability module is on the classpath.
 */
@RestController
@ConditionalOnBean(LlmMetricsService.class)
@RequestMapping("/llmops")
public class ObservabilityController {

    private final LlmMetricsService metrics;

    public ObservabilityController(LlmMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/stats")
    public ApiResponse<List<LlmUsageStats>> statsByModel() {
        return ApiResponse.ok(metrics.statsByModel(currentTenantId()));
    }

    @GetMapping("/total")
    public ApiResponse<LlmUsageStats> total() {
        return ApiResponse.ok(metrics.total(currentTenantId()));
    }

    @GetMapping("/recent")
    public ApiResponse<List<LlmCallRecord>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(metrics.recentCalls(limit));
    }

    @GetMapping("/trend")
    public ApiResponse<List<LlmTrendPoint>> trend(@RequestParam(defaultValue = "HOUR") LlmTrendRange range) {
        return ApiResponse.ok(metrics.trend(currentTenantId(), range));
    }
}
