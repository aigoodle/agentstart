package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.common.util.JsonUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in callback burst and idempotency acceptance test. This validates the Agent Start callback,
 * tenant/account ownership and durable claim path; it does not create QQ WebSocket connections.
 */
class ChannelInboundBurstAcceptanceTest {
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void callbackBurstIsDurableAndDuplicateReplaysAreAcknowledgedExactlyOnce() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(env("CHANNEL_INBOUND_BURST_ENABLED", "false")));
        String endpoint = env(
                "CHANNEL_INBOUND_BURST_URL",
                "http://127.0.0.1:18090/agent-start/channel-events/native/webhook/account-1");
        String token = required("CHANNEL_INBOUND_BURST_TOKEN");
        String provider = env("CHANNEL_INBOUND_BURST_PROVIDER", "native");
        String runtimeNode = env("CHANNEL_INBOUND_BURST_RUNTIME_NODE", provider + "-default");
        String channel = env("CHANNEL_INBOUND_BURST_CHANNEL", "webhook");
        String account = required("CHANNEL_INBOUND_BURST_ACCOUNT_ID");
        int messages = integer("CHANNEL_INBOUND_BURST_MESSAGES", 200);
        int concurrency = integer("CHANNEL_INBOUND_BURST_CONCURRENCY", 32);
        double minimumSuccessRate = decimal("CHANNEL_INBOUND_BURST_MIN_SUCCESS_RATE", 1.0);
        long maximumP95Millis = integer("CHANNEL_INBOUND_BURST_MAX_P95_MS", 3000);
        String reportPath = env("CHANNEL_INBOUND_BURST_REPORT", "target/channel-inbound-burst-report.json");
        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        List<Attempt> initial = execute(
                concurrency,
                messages,
                index -> callback(endpoint, token, payload(provider, runtimeNode, channel, account, runId, index)));
        // Replay only after all first deliveries have completed. Every replay must resolve the durable
        // winner and must never execute the Agent a second time.
        List<Attempt> duplicates = execute(
                concurrency,
                messages,
                index -> callback(endpoint, token, payload(provider, runtimeNode, channel, account, runId, index)));

        long initialSuccesses = initial.stream().filter(Attempt::success).count();
        long duplicateAcks = duplicates.stream()
                .filter(attempt -> attempt.success() && "duplicate".equals(attempt.code()))
                .count();
        double initialSuccessRate = initialSuccesses / (double) messages;
        double duplicateAckRate = duplicateAcks / (double) messages;
        List<Long> latencies = new ArrayList<>();
        initial.forEach(attempt -> latencies.add(attempt.latencyMillis()));
        duplicates.forEach(attempt -> latencies.add(attempt.latencyMillis()));
        latencies.sort(Comparator.naturalOrder());
        long p50 = percentile(latencies, 0.50);
        long p95 = percentile(latencies, 0.95);
        long maximum = latencies.getLast();
        List<String> failures = java.util.stream.Stream.concat(initial.stream(), duplicates.stream())
                .filter(attempt -> !attempt.success())
                .map(Attempt::detail)
                .limit(30)
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt.toString());
        report.put("finishedAt", Instant.now().toString());
        report.put("runId", runId);
        report.put("provider", provider);
        report.put("runtimeNodeId", runtimeNode);
        report.put("channel", channel);
        report.put("accountId", account);
        report.put("messages", messages);
        report.put("requests", messages * 2L);
        report.put("concurrency", concurrency);
        report.put("initialSuccessRate", initialSuccessRate);
        report.put("duplicateAcknowledgementRate", duplicateAckRate);
        report.put("latencyP50Millis", p50);
        report.put("latencyP95Millis", p95);
        report.put("latencyMaxMillis", maximum);
        report.put("minimumSuccessRate", minimumSuccessRate);
        report.put("maximumP95Millis", maximumP95Millis);
        report.put("failures", failures);
        report.put(
                "passed",
                initialSuccessRate >= minimumSuccessRate
                        && duplicateAckRate >= minimumSuccessRate
                        && p95 <= maximumP95Millis);
        writeReport(reportPath, report);

        assertThat(initialSuccessRate)
                .as("initial callback success rate; failures=%s", failures)
                .isGreaterThanOrEqualTo(minimumSuccessRate);
        assertThat(duplicateAckRate)
                .as("durable duplicate acknowledgement rate")
                .isGreaterThanOrEqualTo(minimumSuccessRate);
        assertThat(p95).as("callback p95 latency in ms").isLessThanOrEqualTo(maximumP95Millis);
    }

    private List<Attempt> execute(int concurrency, int count, AttemptFactory factory) throws Exception {
        try (var executor = Executors.newFixedThreadPool(Math.max(1, concurrency))) {
            List<Callable<Attempt>> tasks = java.util.stream.IntStream.range(0, count)
                    .mapToObj(index -> (Callable<Attempt>) () -> factory.create(index))
                    .toList();
            List<Attempt> attempts = new ArrayList<>(count);
            for (var future : executor.invokeAll(tasks)) attempts.add(future.get());
            return attempts;
        }
    }

    private Attempt callback(String endpoint, String token, String payload) {
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("X-Agent-Start-Token", token)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            JsonNode body = JsonUtils.readTree(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Attempt(
                    success,
                    millis,
                    body.path("code").asText(),
                    "HTTP " + response.statusCode() + ": " + response.body());
        } catch (Exception failure) {
            return new Attempt(
                    false,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    "",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static String payload(
            String provider, String node, String channel, String account, String runId, int index) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", provider);
        body.put("runtimeNodeId", node);
        body.put("channelId", channel);
        body.put("accountId", account);
        body.put("messageId", "capacity:" + runId + ":" + index);
        body.put("senderId", "capacity-sender-" + index);
        // Keep one clearly labelled conversation per run so an acceptance run does not create
        // hundreds of unrelated rows in the operator inbox.
        body.put("conversationId", "capacity-conversation-" + runId);
        body.put("content", "channel inbound capacity verification " + index);
        body.put("messageType", "TEXT");
        body.put("attachments", List.of());
        body.put("contentPayload", Map.of("verification", true));
        body.put("timestamp", Instant.now().toString());
        body.put("group", false);
        body.put("metadata", Map.of("capacityRunId", runId));
        return JsonUtils.toJson(body);
    }

    private static long percentile(List<Long> sorted, double quantile) {
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1));
    }

    private static void writeReport(String configuredPath, Map<String, Object> report) throws Exception {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, JsonUtils.toJson(report));
        System.out.println("Channel inbound burst report: " + path);
        System.out.println(JsonUtils.toJson(report));
    }

    private static int integer(String name, int fallback) {
        return Integer.parseInt(env(name, String.valueOf(fallback)));
    }

    private static double decimal(String name, double fallback) {
        return Double.parseDouble(env(name, String.valueOf(fallback)));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    @FunctionalInterface
    private interface AttemptFactory {
        Attempt create(int index);
    }

    private record Attempt(boolean success, long latencyMillis, String code, String detail) {}
}
