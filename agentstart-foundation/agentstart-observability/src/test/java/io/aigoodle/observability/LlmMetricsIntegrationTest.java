package io.aigoodle.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.api.LlmTrendRange;
import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.entity.LlmCallRecord;
import io.aigoodle.observability.service.LlmMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ObservabilityTestApplication.class)
class LlmMetricsIntegrationTest {

    @Autowired
    private ModelService modelService;

    @Autowired
    private LlmMetricsService metrics;

    @Test
    void applicationQueriesExcludeOtherAppsTenantsAndUnscopedHistory() {
        metrics.record(LlmCallMeasurement.successful(
                new io.aigoodle.observability.api.ModelCallContext("p", "m", "app-tenant", "app-one"),
                new TokenUsage(10, 5, 15),
                100));
        metrics.record(LlmCallMeasurement.failed(
                new io.aigoodle.observability.api.ModelCallContext("p", "m", "app-tenant", "app-one"), 300, "Timeout"));
        metrics.record(LlmCallMeasurement.successful(
                new io.aigoodle.observability.api.ModelCallContext("p", "m", "app-tenant", "app-two"),
                new TokenUsage(100, 50, 150),
                100));
        metrics.record(LlmCallMeasurement.successful(
                new io.aigoodle.observability.api.ModelCallContext("p", "m", "other-tenant", "app-one"),
                new TokenUsage(1000, 500, 1500),
                100));
        metrics.record(LlmCallMeasurement.successful("p", "m", "app-tenant", new TokenUsage(10000, 5000, 15000), 100));

        var usage = metrics.total("app-tenant", "app-one");
        assertEquals(2, usage.getCalls());
        assertEquals(1, usage.getErrors());
        assertEquals(15, usage.getTotalTokens());
        assertEquals(200, usage.getAvgLatencyMs());
        assertEquals("USD", usage.getCurrency());
        assertEquals(2, metrics.recentCalls("app-tenant", "app-one", 10).size());
        assertEquals(
                15,
                metrics.trend("app-tenant", "app-one", LlmTrendRange.HOUR).stream()
                        .mapToLong(p -> p.totalTokens())
                        .sum());
        assertEquals(0, metrics.total("app-tenant", "unknown-app").getCalls());
    }

    @Test
    void cachedModelUsesInvocationScopeRatherThanCredentialTenant() {
        var model = modelService.register(ModelRegistration.builder()
                .tenantId("shared-models")
                .providerName("scripted")
                .modelName("scoped-model")
                .modelType(ModelType.LLM)
                .build());
        var client = modelService.getChatClient("shared-models", model.getId());
        for (String appId : java.util.List.of("scoped-one", "scoped-two")) {
            try (var scope = io.aigoodle.common.context.ModelExecutionScope.open("business-tenant", appId)) {
                client.prompt().user("hello").call().content();
            }
        }
        assertNull(io.aigoodle.common.context.ModelExecutionScope.current());
        assertEquals(20, metrics.total("business-tenant", "scoped-one").getTotalTokens());
        assertEquals(20, metrics.total("business-tenant", "scoped-two").getTotalTokens());
        assertEquals(0, metrics.total("shared-models", "scoped-one").getCalls());
    }

    @Test
    void chatCallIsMeteredEndToEnd() {
        ModelEntity model = modelService.register(ModelRegistration.builder()
                .tenantId("obs")
                .providerName("scripted")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .build());

        // a real chat call through ModelService -> the metering decorator records it
        String reply = modelService
                .getModelInstance("obs", model.getId())
                .getChatClient()
                .prompt()
                .user("hello")
                .call()
                .content();
        assertEquals("ok", reply);

        LlmCallRecord rec = metrics.recentCalls(50).stream()
                .filter(r -> "gpt-4o-mini".equals(r.getModel()))
                .findFirst()
                .orElseThrow();
        assertEquals(20, rec.getTotalTokens());
        assertEquals(12, rec.getPromptTokens());
        assertEquals(8, rec.getCompletionTokens());
        assertTrue(rec.getSuccess());
        assertNotNull(rec.getCostMicros());
        assertEquals(metrics.costMicros("gpt-4o-mini", new TokenUsage(12, 8, 20)), rec.getCostMicros());

        LlmUsageStats stats = metrics.statsByModel(null).stream()
                .filter(s -> "gpt-4o-mini".equals(s.getModel()))
                .findFirst()
                .orElseThrow();
        assertTrue(stats.getCalls() >= 1);
        assertTrue(stats.getTotalTokens() >= 20);
    }

    @Test
    void recordsAggregateByTenant() {
        metrics.record(LlmCallMeasurement.successful("openai", "gpt-4o", "tenantA", new TokenUsage(100, 50, 150), 200));
        metrics.record(new LlmCallMeasurement(
                "openai", "gpt-4o", "tenantA", new TokenUsage(100, 50, 150), 400, false, "Timeout"));

        LlmUsageStats total = metrics.total("tenantA");
        assertEquals(2, total.getCalls());
        assertEquals(1, total.getErrors());
        assertEquals(300, total.getTotalTokens());
        assertEquals(300.0, total.getAvgLatencyMs());
        // gpt-4o priced: prompt 200/1k*0.0025 + completion 100/1k*0.01 = 0.0005 + 0.001 = $0.0015 -> 1500 micros
        assertEquals(1500L, total.getCostMicros());
    }

    @Test
    void directRecordAggregates() {
        metrics.record(LlmCallMeasurement.successful("ollama", "llama3.2", "tenantB", TokenUsage.of(10, 5, null), 50));
        assertEquals(15, metrics.total("tenantB").getTotalTokens());
    }

    @Test
    void trendReturnsFixedZeroFilledMonitoringBuckets() {
        metrics.record(
                LlmCallMeasurement.successful("openai", "gpt-4o-mini", "trendTenant", new TokenUsage(12, 8, 20), 120));

        var hour = metrics.trend("trendTenant", LlmTrendRange.HOUR);
        var day = metrics.trend("trendTenant", LlmTrendRange.DAY);
        var week = metrics.trend("trendTenant", LlmTrendRange.WEEK);

        assertEquals(6, hour.size());
        assertEquals(24, day.size());
        assertEquals(7, week.size());
        assertEquals(1, hour.stream().mapToLong(point -> point.calls()).sum());
        assertEquals(20, hour.stream().mapToLong(point -> point.totalTokens()).sum());
    }

    @Test
    void authenticatedMetricsCannotReadOrWriteAnotherTenant() {
        metrics.record(LlmCallMeasurement.successful(
                "scripted", "isolated-model", "metrics-a", TokenUsage.of(3, 2, null), 10));
        metrics.record(LlmCallMeasurement.successful(
                "scripted", "isolated-model", "metrics-b", TokenUsage.of(7, 2, null), 20));
        var principal = io.aigoodle.common.context.CurrentUser.builder()
                .tenantId("metrics-a")
                .userId("metrics-user")
                .build();
        io.aigoodle.common.context.UserContextHolder.runAs(principal, () -> {
            assertEquals(5, metrics.total(null).getTotalTokens());
            assertTrue(metrics.recentCalls(100).stream().allMatch(record -> "metrics-a".equals(record.getTenantId())));
            assertEquals(
                    1,
                    metrics.trend(null, LlmTrendRange.HOUR).stream()
                            .mapToLong(point -> point.calls())
                            .sum());
            assertThrows(SecurityException.class, () -> metrics.total("metrics-b"));
            assertThrows(SecurityException.class, () -> metrics.statsByModel("metrics-b"));
            assertThrows(SecurityException.class, () -> metrics.trend("metrics-b", LlmTrendRange.DAY));
            assertThrows(
                    SecurityException.class,
                    () -> metrics.record(LlmCallMeasurement.successful(
                            "scripted", "isolated-model", "metrics-b", TokenUsage.of(1, 1, null), 10)));
        });
    }
}
