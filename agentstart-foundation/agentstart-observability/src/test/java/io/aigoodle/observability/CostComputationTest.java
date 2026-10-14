package io.aigoodle.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.config.ObservabilityProperties;
import io.aigoodle.observability.service.LlmMetricsService;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test of cost computation from the pricing table.
 */
class CostComputationTest {

    private final LlmMetricsService service = new LlmMetricsService(null, new ObservabilityProperties());

    @Test
    void computesCostFromPricing() {
        // gpt-4o-mini default: input $0.00015/1k, output $0.0006/1k
        // 1000 prompt + 1000 completion = 0.00015 + 0.0006 = $0.00075 -> 750 micros
        assertEquals(750L, service.costMicros("gpt-4o-mini", new TokenUsage(1000, 1000, 2000)));
    }

    @Test
    void unknownModelCostsZero() {
        assertEquals(0L, service.costMicros("some-unlisted-model", new TokenUsage(1000, 1000, 2000)));
    }

    @Test
    void nullUsageCostsZero() {
        assertEquals(0L, service.costMicros("gpt-4o-mini", null));
    }

    @Test
    void missingPricingTableCostsZero() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setPricing(null);
        LlmMetricsService metricsService = new LlmMetricsService(null, properties);

        assertEquals(0L, metricsService.costMicros("gpt-4o-mini", new TokenUsage(1000, 1000, 2000)));
    }
}
