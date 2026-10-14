package io.aigoodle.model.provider.volcengine;

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

class SeedanceVideoModelTest {
    HttpServer server;
    SeedanceVideoModel model;
    AtomicReference<Map<String, Object>> body = new AtomicReference<>();
    AtomicReference<String> response = new AtomicReference<>("{\"id\":\"task-1\"}");
    AtomicReference<String> authorization = new AtomicReference<>();
    List<String> calls = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/contents/generations/tasks", exchange -> {
            calls.add(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!text.isBlank()) body.set(JsonUtils.parseMap(text));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        model = new SeedanceVideoModel(ModelEndpoint.builder()
                .modelName("ep-video")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3")
                .apiKey("test-key")
                .properties(Map.of("endpointId", "ep-chat-must-not-win"))
                .build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void selectedVideoModelAndCredentialReachArk() {
        assertEquals("task-1", model.submit(new VideoRequest("A sunrise", Map.of("duration", 5))));
        assertEquals("ep-video", body.get().get("model"));
        assertEquals(5, body.get().get("duration"));
        assertEquals("Bearer test-key", authorization.get());
        assertEquals(List.of("POST /api/v3/contents/generations/tasks"), calls);
    }

    @Test
    void mapsSuccessAndRejectsMissingTaskId() {
        response.set(
                "{\"status\":\"succeeded\",\"content\":{\"video_url\":\"https://media.example/video.mp4\"},\"usage\":{\"completion_tokens\":42}}");
        assertEquals(VideoResult.Status.SUCCEEDED, model.query("task-1").status());
        response.set("{}");
        assertThrows(IllegalStateException.class, () -> model.submit(new VideoRequest("sunrise", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> model.query("../other"));
    }

    @Test
    void runningTaskIsNotReportedCancelledOrDeleted() {
        response.set("{\"status\":\"running\"}");
        assertFalse(model.cancel("task-1"));
        assertEquals(List.of("GET /api/v3/contents/generations/tasks/task-1"), calls);
    }
}
