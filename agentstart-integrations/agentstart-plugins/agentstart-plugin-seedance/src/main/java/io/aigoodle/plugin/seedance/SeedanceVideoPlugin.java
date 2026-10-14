package io.aigoodle.plugin.seedance;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;

/**
 * Text-to-video against the Ark API. One invocation owns one durable vendor task.
 */
public final class SeedanceVideoPlugin implements AsyncPlugin {
    private final PluginManifest manifest;
    private final SeedanceSubmissionStore submissions;
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public SeedanceVideoPlugin(SeedanceSubmissionStore submissions, URI baseUrl) throws IOException {
        if (baseUrl.getHost() == null
                || !("https".equals(baseUrl.getScheme()) || "http".equals(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null)
            throw new IllegalArgumentException("Seedance base URL must be a deployment-owned HTTP(S) URL");
        this.endpoint = URI.create(baseUrl.toString().replaceAll("/+$", "") + "/contents/generations/tasks");
        this.submissions = submissions;
        manifest = PluginManifests.load(new ClassPathResource("plugins/seedance/manifest.yaml"));
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public PluginResult execute(PluginInvocation invocation, PluginContext context) {
        requireAction(invocation);
        String key = key(context);
        Map<String, Object> body = new TreeMap<>();
        body.put("model", required(context.configuration(), "model"));
        body.put(
                "content",
                List.of(new TreeMap<>(Map.of("type", "text", "text", required(invocation.inputs(), "prompt")))));
        for (String field : List.of("duration", "ratio", "resolution", "generate_audio", "watermark", "seed"))
            if (invocation.inputs().containsKey(field))
                body.put(field, invocation.inputs().get(field));
        String apiKey = required(context.credentials(), "apiKey");
        String requestHash = hash(JsonUtils.toJson(body) + "|" + endpoint);
        if (!submissions.reserve(key, requestHash)) {
            var saved = submissions.get(key);
            if (saved == null || !requestHash.equals(saved.requestHash()))
                return PluginResult.failure(
                        "seedance_invocation_conflict", "Execution ID was already used with different inputs", false);
            if (saved.taskId() == null)
                return PluginResult.failure(
                        "seedance_submission_unknown",
                        "Submission is in progress or its outcome is unknown; reconcile before starting a new execution",
                        false);
            return pending(saved.taskId());
        }
        // No automatic retries. A timeout may occur after the vendor accepted a paid task.
        var reply = call("POST", endpoint, body, apiKey);
        String taskId = required(reply, "id");
        validateTaskId(taskId);
        submissions.accepted(key, taskId);
        return pending(taskId);
    }

    @Override
    public PluginResult query(PluginInvocation invocation, PluginTask task, PluginContext context) {
        requireAction(invocation);
        verifyTask(task, context);
        var reply = call("GET", taskUri(task.id()), null, required(context.credentials(), "apiKey"));
        String status = required(reply, "status");
        return switch (status) {
            case "queued", "running" -> pending(task.id());
            case "succeeded" -> {
                if (!(reply.get("content") instanceof Map<?, ?> content)
                        || !(content.get("video_url") instanceof String url)
                        || url.isBlank())
                    throw new ConnectorException("seedance_protocol", "Succeeded task has no video URL");
                yield PluginResult.success(Map.of("taskId", task.id(), "status", status, "videoUrl", url));
            }
            case "failed", "cancelled", "expired" -> PluginResult.failure(
                    "seedance_task_" + status,
                    "Seedance task " + status + "; inspect the vendor task for details",
                    false);
            default -> throw new ConnectorException("seedance_protocol", "Unknown Seedance task status");
        };
    }

    @Override
    public PluginResult cancel(PluginInvocation invocation, PluginTask task, PluginContext context) {
        requireAction(invocation);
        verifyTask(task, context);
        String apiKey = required(context.credentials(), "apiKey");
        var reply = call("GET", taskUri(task.id()), null, apiKey);
        String status = required(reply, "status");
        if ("queued".equals(status)) {
            call("DELETE", taskUri(task.id()), null, apiKey);
            return PluginResult.success(Map.of("cancellationRequested", true));
        }
        if ("running".equals(status))
            return PluginResult.failure(
                    "seedance_cancel_unavailable", "Running vendor tasks cannot be cancelled by this plugin", false);
        if (Set.of("cancelled", "succeeded", "failed", "expired").contains(status))
            return PluginResult.success(Map.of("status", status));
        return PluginResult.failure("seedance_protocol", "Unknown Seedance task status", false);
    }

    private void verifyTask(PluginTask task, PluginContext context) {
        validateTaskId(task.id());
        var saved = submissions.get(key(context));
        if (saved == null || !task.id().equals(saved.taskId()))
            throw new ConnectorException("seedance_task_denied", "Task does not belong to this tenant and execution");
    }

    private static String key(PluginContext context) {
        var identity = context.identity();
        if (identity.tenantId() == null
                || identity.tenantId().isBlank()
                || identity.executionId() == null
                || identity.executionId().isBlank())
            throw new ConnectorException("seedance_identity", "Tenant and stable execution ID are required");
        return hash(JsonUtils.toJson(List.of(identity.tenantId(), identity.executionId(), "generate")));
    }

    private static void requireAction(PluginInvocation invocation) {
        if (!"generate".equals(invocation.actionId()))
            throw new ConnectorException("seedance_action", "Unknown action");
    }

    private static void validateTaskId(String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,255}")) throw new ConnectorException("seedance_task_id", "Invalid task ID");
    }

    private URI taskUri(String id) {
        return URI.create(endpoint + "/" + id);
    }

    private static PluginResult pending(String id) {
        return PluginResult.pending(new PluginTask(id, Map.of(), Instant.now().plusSeconds(5)));
    }

    private Map<String, Object> call(String method, URI uri, Object body, String apiKey) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        try {
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ConnectorException(
                        "seedance_http", "Seedance HTTP " + response.statusCode() + "; no automatic resubmission");
            return response.body().isBlank() ? Map.of() : JsonUtils.parseMap(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConnectorException("seedance_interrupted", "Seedance request interrupted");
        } catch (IOException exception) {
            throw new ConnectorException(
                    "seedance_transport", "Seedance response unavailable; submission outcome may be unknown");
        }
    }

    private static String required(Map<String, Object> values, String field) {
        if (!(values.get(field) instanceof String value) || value.isBlank())
            throw new ConnectorException("seedance_input", field + " is required");
        return value;
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
