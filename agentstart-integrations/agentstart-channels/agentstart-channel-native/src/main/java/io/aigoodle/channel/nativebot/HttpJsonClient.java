package io.aigoodle.channel.nativebot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.ChannelException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class HttpJsonClient {
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;

    public HttpJsonClient(ObjectMapper json) {
        this.json = json;
    }

    public Map<String, Object> post(String url, Map<String, String> headers, Object body) {
        try {
            return exchange(
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))),
                    headers);
        } catch (ChannelException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ChannelException("json_encode_error", failure.getMessage(), false, failure);
        }
    }

    public Map<String, Object> get(String url, Map<String, String> headers) {
        return exchange(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    private Map<String, Object> exchange(HttpRequest.Builder request, Map<String, String> headers) {
        try {
            request.timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> value =
                    response.body() == null || response.body().isBlank()
                            ? Map.of()
                            : json.readValue(response.body(), new TypeReference<>() {});
            if (response.statusCode() / 100 != 2)
                throw new ChannelException(
                        "platform_http_" + response.statusCode(),
                        response.body(),
                        response.statusCode() >= 500 || response.statusCode() == 429);
            return value;
        } catch (ChannelException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ChannelException("platform_io_error", failure.getMessage(), true, failure);
        }
    }
}
