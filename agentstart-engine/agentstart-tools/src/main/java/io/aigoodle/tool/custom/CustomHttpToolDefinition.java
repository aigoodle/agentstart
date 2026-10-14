package io.aigoodle.tool.custom;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolMetadata;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Runtime HTTP tool produced by a {@link CustomHttpToolSpec}. */
public final class CustomHttpToolDefinition implements ToolDefinition, ToolMetadata {
    private final CustomHttpToolSpec spec;
    private final HttpClient client;

    public CustomHttpToolDefinition(CustomHttpToolSpec spec) {
        this(
                spec,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    CustomHttpToolDefinition(CustomHttpToolSpec spec, HttpClient client) {
        this.spec = spec;
        this.client = client;
    }

    @Override
    public String name() {
        return spec.getName();
    }

    @Override
    public String description() {
        return spec.getDescription();
    }

    @Override
    public String inputSchema() {
        return spec.getInputSchema();
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String url = spec.getUrl();
        for (var entry : arguments.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", encode(String.valueOf(entry.getValue())));
        }
        String method = spec.getMethod().toUpperCase();
        HttpRequest.BodyPublisher body = method.equals("GET") || method.equals("DELETE")
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(arguments));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .method(method, body);
        spec.getHeaders().forEach(request::header);
        if (!method.equals("GET")
                && !method.equals("DELETE")
                && !spec.getHeaders().containsKey("Content-Type")) {
            request.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("status", response.statusCode(), "body", response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Custom tool request interrupted", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("Custom tool request failed: " + failure.getMessage(), failure);
        }
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of(
                "source",
                "custom",
                "category",
                "custom",
                "custom",
                true,
                "method",
                spec.getMethod(),
                "url",
                spec.getUrl(),
                "enabled",
                spec.isEnabled());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
