package io.aigoodle.model.video;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Bounded requests, no redirects/retries, no credential-bearing error bodies. */
public final class VideoHttpTransport {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final URI base;
    private final String apiKey;

    public VideoHttpTransport(String baseUrl, String apiKey) {
        base = URI.create(baseUrl.replaceAll("/+$", "") + "/");
        if (base.getHost() == null
                || !("https".equals(base.getScheme()) || "http".equals(base.getScheme()))
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null)
            throw new IllegalArgumentException("Video endpoint must be HTTP(S) without credentials or query");
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("Video provider API Key is required");
        this.apiKey = apiKey;
    }

    public Map<String, Object> call(String method, String path, Object body, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        headers.forEach(request::header);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        try {
            var reply = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = reply.body()) {
                if (reply.statusCode() < 200 || reply.statusCode() >= 300)
                    throw new PlatformException(
                            "video_upstream_http", "Video provider HTTP " + reply.statusCode(), null);
                byte[] bytes = stream.readNBytes(1_048_577);
                if (bytes.length > 1_048_576)
                    throw new PlatformException("video_response_size", "Video response too large", null);
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                return text.isBlank() ? Map.of() : JsonUtils.parseMap(text);
            }
        } catch (IOException exception) {
            throw new PlatformException(
                    "video_transport_unknown",
                    "Video response unavailable; do not resubmit an uncertain generation",
                    null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException("video_interrupted", "Video request interrupted", null);
        }
    }

    public static String taskId(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,255}"))
            throw new IllegalArgumentException("Invalid video task ID");
        return value;
    }
}
