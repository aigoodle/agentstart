package io.aigoodle.plugin.remote;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.*;
import io.aigoodle.plugin.host.PluginHostTokenService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP protocol v1. Deployment configuration owns endpoint and service authentication. */
public final class RemotePlugin implements AsyncPlugin {
    public record HostCall(String id, String capability, Map<String, Object> arguments, Object state) {}

    public record Reply(PluginResult completed, HostCall hostCall) {}

    private final PluginManifest manifest;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;
    private final HttpClient client;
    private final PluginHostTokenService hostTokens;

    public RemotePlugin(PluginManifest manifest, URI endpoint, String token, Duration timeout) {
        this(manifest, endpoint, token, timeout, null);
    }

    public RemotePlugin(
            PluginManifest manifest, URI endpoint, String token, Duration timeout, PluginHostTokenService hostTokens) {
        if (endpoint == null
                || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()))
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null)
            throw new IllegalArgumentException(
                    "Remote plugin requires an HTTP(S) base URL without credentials/query/fragment");
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Remote plugin timeout must be positive");
        this.manifest = manifest;
        this.endpoint = URI.create(endpoint.toString().replaceAll("/+$", "") + "/");
        this.token = token;
        this.timeout = timeout;
        this.hostTokens = hostTokens;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public PluginResult execute(PluginInvocation invocation, PluginContext context) {
        return invoke("v1/execute", invocation, null, context);
    }

    @Override
    public PluginResult query(PluginInvocation invocation, PluginTask task, PluginContext context) {
        return invoke("v1/tasks/query", invocation, task, context);
    }

    @Override
    public PluginResult cancel(PluginInvocation invocation, PluginTask task, PluginContext context) {
        return invoke("v1/tasks/cancel", invocation, task, context);
    }

    private PluginResult invoke(
            String initialPath, PluginInvocation invocation, PluginTask task, PluginContext context) {
        Instant deadline = Instant.now().plus(timeout);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("protocolVersion", "1");
        body.put("pluginId", manifest.id());
        body.put("pluginVersion", manifest.version());
        body.put("invocation", invocation);
        body.put("identity", context.identity());
        body.put("configuration", context.configuration());
        body.put("credentials", context.credentials());
        if (task != null) body.put("task", task);
        if (hostTokens != null) body.put("host", hostTokens.issue(manifest, context.identity(), timeout));
        String path = initialPath;
        for (int count = 0; count <= 16; count++) {
            Reply reply = post(path, body, deadline);
            if (reply == null || (reply.completed() == null) == (reply.hostCall() == null))
                throw new ConnectorException(
                        "plugin_protocol_error", "Expected exactly one completed result or hostCall");
            if (reply.completed() != null) return reply.completed();
            if (count == 16) throw new ConnectorException("plugin_host_call_limit", "Too many host capability calls");
            HostCall call = reply.hostCall();
            if (call.id() == null || call.id().isBlank() || call.capability() == null)
                throw new ConnectorException("plugin_protocol_error", "Host call id and capability are required");
            Object output = context.host().call(call.capability(), call.arguments());
            // Host identity stays bound to this invocation; remote code cannot replace it.
            body = new LinkedHashMap<>(body);
            body.put("hostCallId", call.id());
            body.put("hostResult", output);
            body.put("state", call.state());
            path = "v1/resume";
        }
        throw new IllegalStateException("unreachable");
    }

    private Reply post(String path, Object body, Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero())
            throw new ConnectorException("plugin_timeout", "Remote plugin deadline exceeded");
        var builder = HttpRequest.newBuilder(endpoint.resolve(path))
                .timeout(remaining)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ConnectorException("plugin_http_error", "Remote plugin HTTP status " + response.statusCode());
            try {
                return JsonUtils.parse(response.body(), Reply.class);
            } catch (IllegalArgumentException exception) {
                throw new ConnectorException("plugin_protocol_error", "Invalid remote plugin response");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConnectorException("plugin_interrupted", "Remote plugin call interrupted");
        } catch (IOException exception) {
            // Do not automatically retry: the remote side may have accepted a side effect.
            throw new ConnectorException(
                    "plugin_transport_error", "Remote plugin response unavailable; submission outcome may be unknown");
        }
    }
}
