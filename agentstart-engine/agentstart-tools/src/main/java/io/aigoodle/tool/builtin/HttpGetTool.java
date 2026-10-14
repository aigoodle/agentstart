package io.aigoodle.tool.builtin;

import io.aigoodle.tool.AbstractToolDefinition;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Performs an HTTP GET and returns the response body (truncated). Argument: {@code url}.
 * A simple but genuinely useful connector for agents that need to read web/API data.
 */
public class HttpGetTool extends AbstractToolDefinition {

    private static final int MAX_RESPONSE_LENGTH = 8_000;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String name() {
        return "http_get";
    }

    @Override
    public String description() {
        return "Fetch the contents of a URL via HTTP GET. Argument: 'url'.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String url = stringArgument(arguments, "url");
        if (url == null || url.isBlank()) {
            return "error: missing 'url'";
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_LENGTH) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_LENGTH) + "...[truncated]";
            }
            return "status=" + response.statusCode() + "\n" + responseBody;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "error: HTTP request interrupted";
        } catch (IOException | IllegalArgumentException requestFailure) {
            return "error: " + requestFailure.getMessage();
        }
    }
}
