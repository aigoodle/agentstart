package io.aigoodle.plugin.video;

import com.networknt.schema.*;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.service.CredentialCodec;
import io.aigoodle.model.video.*;
import io.aigoodle.plugin.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Generic video lifecycle. No vendor API details or credentials cross the plugin boundary. */
public final class VideoTaskService implements AutoCloseable {
    private final java.util.concurrent.ThreadPoolExecutor polling = new java.util.concurrent.ThreadPoolExecutor(
            4,
            4,
            0,
            java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<>(),
            Thread.ofPlatform().daemon(true).name("video-poll-", 0).factory());
    private final VideoTaskStore store;
    private final VideoModelService models;
    private final CredentialCodec codec;

    public VideoTaskService(VideoTaskStore store, VideoModelService models, CredentialCodec codec) {
        this.store = store;
        this.models = models;
        this.codec = codec;
    }

    public PluginResult submit(PluginInvocation invocation, PluginContext context) {
        String tenant = context.identity().tenantId(), key = key(context);
        String hash = digest(canonical(invocation.inputs()));
        var previous = store.byInvocation(tenant, key);
        if (previous != null) {
            if (!previous.requestHash().equals(hash))
                throw new IllegalArgumentException("Execution ID reused with different video inputs");
            return result(previous);
        }
        var selection = map(invocation.inputs().get("model"));
        var endpoint = models.resolve(tenant, selection);
        var video = models.create(endpoint);
        var parameters = new LinkedHashMap<String, Object>();
        var schema = video.parameterSchema();
        var allowed = map(schema.get("properties"));
        if (endpoint.getProperties() != null)
            endpoint.getProperties().forEach((name, value) -> {
                if (allowed.containsKey(name)) parameters.put(name, value);
            });
        parameters.putAll(map(invocation.inputs().get("parameters")));
        var validator = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(JsonUtils.mapper().valueToTree(schema).toString(), InputFormat.JSON);
        if (!validator
                .validate(JsonUtils.mapper().valueToTree(parameters).toString(), InputFormat.JSON)
                .isEmpty())
            throw new IllegalArgumentException("Video parameters are not supported by the selected model");
        if (!(invocation.inputs().get("prompt") instanceof String prompt) || prompt.isBlank())
            throw new IllegalArgumentException("Video prompt is required");
        var request = new VideoRequest(prompt, parameters);
        if (prompt.length() > video.maxPromptLength())
            throw new IllegalArgumentException(
                    "Video prompt exceeds selected model's character limit: " + video.maxPromptLength());
        String id = UUID.randomUUID().toString();
        String encrypted =
                codec.encode(tenant, Map.of("endpoint", JsonUtils.mapper().convertValue(endpoint, Map.class)));
        if (!store.reserve(id, key, tenant, context.identity().userId(), hash, encrypted)) {
            var raced = store.byInvocation(tenant, key);
            if (raced == null || !raced.requestHash().equals(hash))
                throw new IllegalArgumentException("Video invocation conflict");
            return result(raced);
        }
        try {
            store.accepted(tenant, id, video.submit(request));
        } catch (RuntimeException failure) {
            store.unknown(tenant, id);
            return result(store.get(tenant, id));
        }
        return result(store.get(tenant, id));
    }

    public PluginResult query(PluginTask task, PluginContext context) {
        var saved = owned(task, context);
        poll(saved);
        return result(store.get(saved.tenant(), saved.id()));
    }

    public PluginResult cancel(PluginTask task, PluginContext context) {
        var saved = owned(task, context);
        store.requestCancel(saved.tenant(), saved.id());
        poll(store.get(saved.tenant(), saved.id()));
        var current = store.get(saved.tenant(), saved.id());
        if (Set.of("QUEUED", "RUNNING", "UNKNOWN", "SUBMITTING").contains(current.status()))
            return PluginResult.failure(
                    "video_cancellation_pending", "Cancellation request persisted; vendor task has not stopped", false);
        return PluginResult.success(Map.of("status", current.status()));
    }

    public void recover() {
        for (var task : store.due(20)) {
            try {
                polling.execute(() -> poll(task));
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                break;
            }
        }
    }

    @Override
    public void close() {
        polling.shutdownNow();
    }

    private void poll(VideoTaskStore.Task saved) {
        if (!Set.of("QUEUED", "RUNNING").contains(saved.status())) return;
        String token = store.claim(saved);
        if (token == null) return;
        // Reload after claim so a concurrent cancellation request is not lost.
        var task = store.get(saved.tenant(), saved.id());
        try {
            var endpoint = JsonUtils.convert(
                    codec.decode(task.tenant(), task.endpointCipher()).get("endpoint"), ModelEndpoint.class);
            var video = models.create(endpoint);
            boolean expired = Instant.now().isAfter(task.deadline());
            if (task.cancel() || expired) video.cancel(task.vendorId());
            var output = video.query(task.vendorId());
            store.finish(
                    task,
                    token,
                    expired && !output.terminal() ? "EXPIRED" : output.status().name(),
                    JsonUtils.toJson(output),
                    output.errorCode(),
                    5);
        } catch (RuntimeException failure) {
            store.finish(
                    task,
                    token,
                    Instant.now().isAfter(task.deadline()) ? "EXPIRED" : task.status(),
                    task.result(),
                    "video_poll_failed",
                    Math.min(60, 5 + task.attempts() * 5));
        }
    }

    private VideoTaskStore.Task owned(PluginTask task, PluginContext context) {
        var saved = store.byInvocation(context.identity().tenantId(), key(context));
        if (saved == null || !saved.id().equals(task.id()))
            throw new IllegalArgumentException("Video task does not belong to this execution");
        return saved;
    }

    private PluginResult result(VideoTaskStore.Task task) {
        if (Set.of("SUBMITTING", "QUEUED", "RUNNING").contains(task.status()))
            return PluginResult.pending(
                    new PluginTask(task.id(), Map.of(), Instant.now().plusSeconds(5)));
        if ("SUCCEEDED".equals(task.status())) {
            var output = JsonUtils.parse(task.result(), VideoResult.class);
            return PluginResult.success(Map.of(
                    "taskId",
                    task.id(),
                    "status",
                    "SUCCEEDED",
                    "videoUrl",
                    output.videoUrl(),
                    "usage",
                    output.usage()));
        }
        return PluginResult.failure(
                "video_" + task.status().toLowerCase(Locale.ROOT),
                "Video task " + task.status() + " (" + task.id() + ")"
                        + ("UNKNOWN".equals(task.status()) ? "; reconcile vendor submission before retrying" : ""),
                false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Video configuration must be an object");
        return (Map<String, Object>) value;
    }

    private static String key(PluginContext context) {
        String tenant = context.identity().tenantId(),
                execution = context.identity().executionId();
        if (tenant == null || tenant.isBlank() || execution == null || execution.isBlank())
            throw new IllegalArgumentException("Video execution identity is required");
        return digest(JsonUtils.toJson(List.of(tenant, execution)));
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, String>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
            return JsonUtils.toJson(sorted);
        }
        if (value instanceof List<?> list)
            return JsonUtils.toJson(
                    list.stream().map(VideoTaskService::canonical).toList());
        return JsonUtils.toJson(value);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
