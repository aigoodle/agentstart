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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Opt-in acceptance test for a tenant's real channel-connection fleet. Does not send messages. */
class ChannelConnectionCapacityAcceptanceTest {
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void configuredConnectionFleetMeetsAvailabilityAndLatencyBudget() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(env("CHANNEL_CAPACITY_ENABLED", "false")));
        String baseUrl = env("CHANNEL_CAPACITY_BACKEND_URL", "http://127.0.0.1:18090/agent-start");
        String provider = env("CHANNEL_CAPACITY_PROVIDER", "native");
        String channel = env("CHANNEL_CAPACITY_CHANNEL", "qqbot");
        int expected = integer("CHANNEL_CAPACITY_EXPECTED_CONNECTIONS", 200);
        int concurrency = integer("CHANNEL_CAPACITY_CONCURRENCY", 32);
        int rounds = integer("CHANNEL_CAPACITY_ROUNDS", 3);
        long durationSeconds = integer("CHANNEL_CAPACITY_DURATION_SECONDS", 0);
        long roundIntervalSeconds = integer("CHANNEL_CAPACITY_ROUND_INTERVAL_SECONDS", 0);
        double minimumSuccessRate = decimal("CHANNEL_CAPACITY_MIN_SUCCESS_RATE", 0.99);
        long maximumP95Millis = integer("CHANNEL_CAPACITY_MAX_P95_MS", 5000);
        String reportPath = env("CHANNEL_CAPACITY_REPORT", "target/channel-capacity-report.json");
        Instant startedAt = Instant.now();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt.toString());
        report.put("provider", provider);
        report.put("channel", channel);
        report.put("expectedConnections", expected);

        long inventoryStarted = System.nanoTime();
        JsonNode listed =
                json(send(request(baseUrl + "/channel-connections").GET().build()));
        report.put(
                "inventoryLatencyMillis",
                Duration.ofNanos(System.nanoTime() - inventoryStarted).toMillis());
        long nodesStarted = System.nanoTime();
        JsonNode nodeInventory =
                json(send(request(baseUrl + "/channels/runtime-nodes").GET().build()));
        report.put(
                "runtimeNodeInventoryLatencyMillis",
                Duration.ofNanos(System.nanoTime() - nodesStarted).toMillis());
        LinkedHashSet<String> availableRuntimeNodes = new LinkedHashSet<>();
        nodeInventory.path("data").path(provider).forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) availableRuntimeNodes.add(node.asText());
        });
        List<String> ids = new ArrayList<>();
        LinkedHashSet<String> runtimeAccounts = new LinkedHashSet<>();
        LinkedHashSet<String> runtimeNodes = new LinkedHashSet<>();
        int[] online = {0};
        listed.path("data").forEach(connection -> {
            if (provider.equals(connection.path("provider").asText())
                    && channel.equals(connection.path("channelId").asText())
                    && "ACTIVE".equals(connection.path("desiredStatus").asText())) {
                ids.add(connection.path("id").asText());
                if ("ONLINE".equals(connection.path("runtimeStatus").asText())
                        || "RUNNING".equals(connection.path("runtimeStatus").asText())) online[0]++;
                String account = connection.path("runtimeAccountId").asText();
                if (!account.isBlank()) runtimeAccounts.add(account);
                JsonNode node = connection.path("runtimeNodeId");
                if (node.isTextual() && !node.asText().isBlank()) runtimeNodes.add(node.asText());
            }
        });
        report.put("observedActiveConnections", ids.size());
        report.put("observedOnlineConnections", online[0]);
        report.put("distinctRuntimeAccounts", runtimeAccounts.size());
        report.put("explicitRuntimeNodes", runtimeNodes);
        report.put("availableRuntimeNodes", availableRuntimeNodes);
        List<String> preflightFailures = new ArrayList<>();
        if (ids.size() < expected) preflightFailures.add("active connections " + ids.size() + " < " + expected);
        if (online[0] < expected) preflightFailures.add("online connections " + online[0] + " < " + expected);
        if (Boolean.parseBoolean(env("CHANNEL_CAPACITY_REQUIRE_DISTINCT_ACCOUNTS", "true"))
                && runtimeAccounts.size() < expected) {
            preflightFailures.add("distinct runtime accounts " + runtimeAccounts.size() + " < " + expected);
        }
        if (availableRuntimeNodes.isEmpty()) preflightFailures.add("provider has no available runtime node");
        if (!preflightFailures.isEmpty()) {
            report.put("finishedAt", Instant.now().toString());
            report.put("failurePhase", "inventory");
            report.put("failures", preflightFailures);
            report.put("passed", false);
            writeReport(reportPath, report);
            assertThat(preflightFailures).as("capacity inventory preflight").isEmpty();
        }

        List<Probe> probes = new ArrayList<>();
        int completedRounds = 0;
        long deadline = durationSeconds <= 0
                ? 0
                : System.nanoTime() + Duration.ofSeconds(durationSeconds).toNanos();
        try (var executor = Executors.newFixedThreadPool(concurrency)) {
            do {
                List<Callable<Probe>> tasks = ids.stream()
                        .map(id -> (Callable<Probe>) () -> probe(baseUrl, id))
                        .toList();
                for (var result : executor.invokeAll(tasks)) probes.add(result.get());
                completedRounds++;
                boolean continueForDuration = durationSeconds > 0 && System.nanoTime() < deadline;
                boolean continueForRounds = durationSeconds <= 0 && completedRounds < rounds;
                if ((continueForDuration || continueForRounds) && roundIntervalSeconds > 0) {
                    Thread.sleep(Duration.ofSeconds(roundIntervalSeconds).toMillis());
                }
            } while (durationSeconds > 0 ? System.nanoTime() < deadline : completedRounds < rounds);
        }

        long successes = probes.stream().filter(Probe::success).count();
        double successRate = successes / (double) probes.size();
        List<Long> sortedLatency = probes.stream()
                .map(Probe::latencyMillis)
                .sorted(Comparator.naturalOrder())
                .toList();
        long p95 = sortedLatency.get(Math.max(0, (int) Math.ceil(sortedLatency.size() * 0.95) - 1));
        long p50 = sortedLatency.get(Math.max(0, (int) Math.ceil(sortedLatency.size() * 0.50) - 1));
        long maximum = sortedLatency.getLast();
        List<String> failures = probes.stream()
                .filter(probe -> !probe.success())
                .map(Probe::detail)
                .limit(20)
                .toList();

        report.put("finishedAt", Instant.now().toString());
        report.put("completedRounds", completedRounds);
        report.put("probes", probes.size());
        report.put("successes", successes);
        report.put("successRate", successRate);
        report.put("latencyP50Millis", p50);
        report.put("latencyP95Millis", p95);
        report.put("latencyMaxMillis", maximum);
        report.put("minimumSuccessRate", minimumSuccessRate);
        report.put("maximumP95Millis", maximumP95Millis);
        report.put("failures", failures);
        report.put("passed", successRate >= minimumSuccessRate && p95 <= maximumP95Millis);
        writeReport(reportPath, report);

        assertThat(successRate)
                .as("success rate; first failures=%s", failures)
                .isGreaterThanOrEqualTo(minimumSuccessRate);
        assertThat(p95).as("p95 connection probe latency in ms").isLessThanOrEqualTo(maximumP95Millis);
    }

    private static void writeReport(String configuredPath, Map<String, Object> report) throws Exception {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, JsonUtils.toJson(report));
        System.out.println("Channel capacity report: " + path);
        System.out.println(JsonUtils.toJson(report));
    }

    private Probe probe(String baseUrl, String id) {
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(
                    request(baseUrl + "/channel-connections/" + id + "/test")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            JsonNode body = JsonUtils.readTree(response.body());
            String status = body.path("data").path("runtimeStatus").asText();
            boolean success = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && ("ONLINE".equals(status) || "RUNNING".equals(status));
            return new Probe(success, millis, id + ": HTTP " + response.statusCode() + ", status=" + status);
        } catch (Exception failure) {
            return new Probe(
                    false,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    id + ": " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private HttpRequest.Builder request(String uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(15));
        JsonNode headers = JsonUtils.readTree(env("CHANNEL_CAPACITY_HEADERS_JSON", "{}"));
        headers.fields()
                .forEachRemaining(
                        entry -> builder.header(entry.getKey(), entry.getValue().asText()));
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return response;
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JsonUtils.readTree(response.body());
    }

    private static int integer(String name, int fallback) {
        return Integer.parseInt(env(name, String.valueOf(fallback)));
    }

    private static double decimal(String name, double fallback) {
        return Double.parseDouble(env(name, String.valueOf(fallback)));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Probe(boolean success, long latencyMillis, String detail) {}
}
