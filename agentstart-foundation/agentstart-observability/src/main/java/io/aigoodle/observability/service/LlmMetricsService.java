package io.aigoodle.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.api.LlmTrendPoint;
import io.aigoodle.observability.api.LlmTrendRange;
import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.config.ObservabilityProperties;
import io.aigoodle.observability.entity.LlmCallRecord;
import io.aigoodle.observability.mapper.LlmCallRecordMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records every LLM call (tokens, cost, latency, success) and answers the aggregate
 * questions an LLMOps view needs: spend and volume per model, error rate, latency.
 */
public class LlmMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(LlmMetricsService.class);

    private final LlmCallRecordMapper callRecordMapper;
    private final ObservabilityProperties observabilityProperties;
    private final LlmCallRecordFactory recordFactory;
    private final LlmUsageAggregator usageAggregator = new LlmUsageAggregator();

    public LlmMetricsService(LlmCallRecordMapper callRecordMapper, ObservabilityProperties observabilityProperties) {
        this.callRecordMapper = callRecordMapper;
        this.observabilityProperties = observabilityProperties;
        this.recordFactory = new LlmCallRecordFactory(this::costMicros);
    }

    public LlmCallRecord record(LlmCallMeasurement measurement) {
        resolveTenant(measurement.tenantId());
        LlmCallRecord record = recordFactory.create(measurement);
        persistWithoutDisruptingModelCall(record);
        return record;
    }

    /** @deprecated use {@link #record(LlmCallMeasurement)} to avoid positional parameter mistakes. */
    @Deprecated
    public LlmCallRecord record(
            String provider,
            String model,
            String tenantId,
            TokenUsage usage,
            long latencyMs,
            boolean success,
            String errorType) {
        return record(new LlmCallMeasurement(provider, model, tenantId, usage, latencyMs, success, errorType));
    }

    private void persistWithoutDisruptingModelCall(LlmCallRecord record) {
        try {
            callRecordMapper.insert(record);
        } catch (RuntimeException persistenceFailure) {
            // metering must never break the actual model call
            logger.warn("Failed to persist LLM call record: {}", persistenceFailure.getMessage());
        }
    }

    /** Cost in micro-currency units, from the configured pricing table (0 if unknown). */
    public long costMicros(String model, TokenUsage usage) {
        ObservabilityProperties.ModelPrice price =
                observabilityProperties.getPricing().get(model);
        if (price == null || usage == null) {
            return 0L;
        }
        double cost = usage.promptTokens() / 1000.0 * price.getInputPer1k()
                + usage.completionTokens() / 1000.0 * price.getOutputPer1k();
        return Math.round(cost * 1_000_000.0);
    }

    public List<LlmUsageStats> statsByModel(String tenantId) {
        Map<String, List<LlmCallRecord>> byModel = new LinkedHashMap<>();
        for (LlmCallRecord record : load(tenantId)) {
            byModel.computeIfAbsent(record.getModel(), ignored -> new ArrayList<>())
                    .add(record);
        }
        return byModel.entrySet().stream()
                .map(entry -> aggregate(entry.getKey(), entry.getValue()))
                .toList();
    }

    public LlmUsageStats total(String tenantId) {
        return total(tenantId, null);
    }

    public LlmUsageStats total(String tenantId, String appId) {
        return aggregate("*", load(tenantId, appId));
    }

    private LlmUsageStats aggregate(String model, List<LlmCallRecord> records) {
        LlmUsageStats result = usageAggregator.aggregate(model, records);
        result.setCurrency(observabilityProperties.getCurrency());
        return result;
    }

    public List<LlmCallRecord> recentCalls(int limit) {
        return recentCalls(null, null, limit);
    }

    public List<LlmCallRecord> recentCalls(String tenantId, String appId, int limit) {
        LambdaQueryWrapper<LlmCallRecord> query = new LambdaQueryWrapper<LlmCallRecord>()
                .orderByDesc(LlmCallRecord::getCreatedAt)
                .last("limit " + Math.min(200, Math.max(1, limit)));
        String tenant = resolveTenant(tenantId);
        if (tenant != null) {
            query.eq(LlmCallRecord::getTenantId, tenant);
        }
        if (appId != null) query.eq(LlmCallRecord::getAppId, appId);
        return callRecordMapper.selectList(query);
    }

    /**
     * Returns a fixed-size, zero-filled time series suitable for a cloud-monitor style chart.
     * HOUR has 6 ten-minute buckets, DAY has 24 hourly buckets and WEEK has 7 daily buckets.
     */
    public List<LlmTrendPoint> trend(String tenantId, LlmTrendRange range) {
        return trend(tenantId, null, range);
    }

    public List<LlmTrendPoint> trend(String tenantId, String appId, LlmTrendRange range) {
        tenantId = resolveTenant(tenantId);
        LlmTrendRange resolvedRange = range == null ? LlmTrendRange.HOUR : range;
        long bucketSeconds = resolvedRange.bucketSize().toSeconds();
        LocalDateTime currentBucket = floorToBucket(LocalDateTime.now(), bucketSeconds);
        LocalDateTime start =
                currentBucket.minus(resolvedRange.bucketSize().multipliedBy(resolvedRange.bucketCount() - 1L));
        LocalDateTime end = currentBucket.plus(resolvedRange.bucketSize());

        LambdaQueryWrapper<LlmCallRecord> query = new LambdaQueryWrapper<LlmCallRecord>()
                .ge(LlmCallRecord::getCreatedAt, start)
                .lt(LlmCallRecord::getCreatedAt, end)
                .orderByAsc(LlmCallRecord::getCreatedAt);
        if (tenantId != null) {
            query.eq(LlmCallRecord::getTenantId, tenantId);
        }

        if (appId != null) query.eq(LlmCallRecord::getAppId, appId);

        Map<LocalDateTime, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (int i = 0; i < resolvedRange.bucketCount(); i++) {
            buckets.put(start.plus(resolvedRange.bucketSize().multipliedBy(i)), new TrendAccumulator());
        }
        for (LlmCallRecord record : callRecordMapper.selectList(query)) {
            LocalDateTime bucket = floorToBucket(record.getCreatedAt(), bucketSeconds);
            TrendAccumulator accumulator = buckets.get(bucket);
            if (accumulator != null) {
                accumulator.include(record);
            }
        }
        return buckets.entrySet().stream()
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();
    }

    private static LocalDateTime floorToBucket(LocalDateTime value, long bucketSeconds) {
        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0);
        long seconds = ChronoUnit.SECONDS.between(epoch, value);
        return epoch.plusSeconds(Math.floorDiv(seconds, bucketSeconds) * bucketSeconds);
    }

    private static final class TrendAccumulator {
        private long calls;
        private long errors;
        private long totalTokens;
        private long costMicros;
        private long latencyMs;

        private void include(LlmCallRecord record) {
            calls++;
            if (!Boolean.TRUE.equals(record.getSuccess())) {
                errors++;
            }
            totalTokens += record.getTotalTokens() == null ? 0 : record.getTotalTokens();
            costMicros += record.getCostMicros() == null ? 0 : record.getCostMicros();
            latencyMs += record.getLatencyMs() == null ? 0 : record.getLatencyMs();
        }

        private LlmTrendPoint toPoint(LocalDateTime bucketStart) {
            return new LlmTrendPoint(
                    bucketStart, calls, errors, totalTokens, costMicros, calls == 0 ? 0.0 : (double) latencyMs / calls);
        }
    }

    private static String resolveTenant(String requestedTenant) {
        if (!UserContextHolder.isAuthenticated()) {
            return requestedTenant;
        }
        String currentTenant = UserContextHolder.currentTenantId();
        if (requestedTenant != null && !currentTenant.equals(requestedTenant)) {
            throw new SecurityException("Requested tenant does not match the authenticated tenant");
        }
        return currentTenant;
    }

    private List<LlmCallRecord> load(String tenantId) {
        return load(tenantId, null);
    }

    private List<LlmCallRecord> load(String tenantId, String appId) {
        tenantId = resolveTenant(tenantId);
        LambdaQueryWrapper<LlmCallRecord> query = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            query.eq(LlmCallRecord::getTenantId, tenantId);
        }
        if (appId != null) query.eq(LlmCallRecord::getAppId, appId);
        return callRecordMapper.selectList(query);
    }
}
