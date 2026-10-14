package io.aigoodle.model.provider.qwen;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.video.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class WanVideoModelTest {
    HttpServer server;
    WanVideoModel model;
    AtomicReference<Map<String, Object>> body = new AtomicReference<>();
    AtomicReference<String> response = new AtomicReference<>("{\"output\":{\"task_id\":\"wan-task\"}}");
    AtomicReference<String> async = new AtomicReference<>();
    List<String> calls = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/", exchange -> {
            calls.add(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            async.set(exchange.getRequestHeaders().getFirst("X-DashScope-Async"));
            var text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!text.isBlank()) body.set(JsonUtils.parseMap(text));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        model = new WanVideoModel(ModelEndpoint.builder()
                .modelName("wan2.6-t2v")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1")
                .apiKey("test-key")
                .build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void translatesSharedQwenEndpointToNativeAsyncVideoProtocol() {
        assertEquals(
                "wan-task", model.submit(new VideoRequest("A sunrise", Map.of("size", "1280*720", "duration", 5))));
        assertEquals("enable", async.get());
        assertEquals("wan2.6-t2v", body.get().get("model"));
        assertEquals(Map.of("prompt", "A sunrise"), body.get().get("input"));
        assertEquals(List.of("POST /api/v1/services/aigc/video-generation/video-synthesis"), calls);
    }

    @Test
    void mapsSuccessAndDoesNotInventCancellationEndpoint() {
        response.set("{\"output\":{\"task_status\":\"RUNNING\"}}");
        assertFalse(model.cancel("wan-task"));
        assertEquals(List.of("GET /api/v1/tasks/wan-task"), calls);
        response.set("{\"output\":{\"task_status\":\"SUCCEEDED\",\"video_url\":\"https://media.example/video.mp4\"}}");
        assertEquals(VideoResult.Status.SUCCEEDED, model.query("wan-task").status());
    }

    @Test
    void rejectsMissingTaskIdAndChatModels() {
        response.set("{}");
        assertThrows(IllegalStateException.class, () -> model.submit(new VideoRequest("sunrise", Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WanVideoModel(
                        ModelEndpoint.builder().modelName("qwen-plus").build()));
    }
}
