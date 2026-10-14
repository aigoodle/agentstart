package io.aigoodle.plugin.host;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** A host-configured internal endpoint, never a URL supplied by plugin arguments. */
public final class HttpPluginHostCapability implements PluginHostCapability {
    private final String name;
    private final URI endpoint;
    private final String serviceToken;
    private final Duration timeout;
    private final HttpClient client;

    public HttpPluginHostCapability(String name, URI endpoint, String serviceToken, Duration timeout) {
        if (name == null
                || name.isBlank()
                || endpoint == null
                || endpoint.getHost() == null
                || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null)
            throw new IllegalArgumentException("A named HTTP(S) capability endpoint is required");
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("timeout must be positive");
        this.name = name;
        this.endpoint = endpoint;
        this.serviceToken = serviceToken;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object execute(ConnectorExecutionContext identity, Map<String, Object> arguments) {
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JsonUtils.toJson(Map.of("identity", identity, "arguments", arguments))));
        if (serviceToken != null && !serviceToken.isBlank()) request.header("Authorization", "Bearer " + serviceToken);
        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ConnectorException(
                        "plugin_host_http_error", "Internal capability HTTP status " + response.statusCode());
            return JsonUtils.parse(response.body(), Object.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConnectorException("plugin_host_interrupted", "Host call interrupted");
        } catch (java.io.IOException exception) {
            throw new ConnectorException("plugin_host_unavailable", "Host capability response unavailable");
        }
    }
}
