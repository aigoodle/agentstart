package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.*;
import io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection;
import io.aigoodle.connector.execution.*;
import io.aigoodle.plugin.host.PluginHostFactory;
import io.aigoodle.plugin.remote.RemotePlugin;
import io.aigoodle.plugin.runtime.PluginConnectorProvider;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PluginAsyncTest {
    @Test
    void remoteTaskRoundTripsAndRejectsVersionChange() throws Exception {
        List<String> paths = new ArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            var body = JsonUtils.parseMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            PluginResult result;
            if (path.endsWith("execute"))
                result = PluginResult.pending(
                        new PluginTask("job-1", Map.of(), Instant.now().plusSeconds(5)));
            else {
                assertThat(JsonUtils.convert(body.get("task"), PluginTask.class).id())
                        .isEqualTo("job-1");
                result = PluginResult.success(
                        path.endsWith("cancel") ? Map.of("cancelled", true) : Map.of("text", "video-url"));
            }
            byte[] bytes =
                    JsonUtils.toJson(new RemotePlugin.Reply(result, null)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var remote = new RemotePlugin(
                    manifest(true),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "service",
                    Duration.ofSeconds(5));
            var provider = provider(remote);
            var pending = provider.execute(request(Map.of()));
            // The checkpoint/wire representation must work, not just the in-memory record.
            var task = JsonUtils.parseMap(JsonUtils.toJson(pending.metadata().get("pluginTask")));
            assertThat(provider.execute(request(Map.of("pluginTask", task, "pluginTaskOperation", "QUERY")))
                            .data())
                    .isEqualTo(Map.of("text", "video-url"));
            assertThat(provider.execute(request(Map.of("pluginTask", task, "pluginTaskOperation", "CANCEL")))
                            .success())
                    .isTrue();
            task.put("pluginVersion", "obsolete");
            assertThatThrownBy(
                            () -> provider.execute(request(Map.of("pluginTask", task, "pluginTaskOperation", "QUERY"))))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("different plugin version");
            assertThat(paths).containsExactly("/v1/execute", "/v1/tasks/query", "/v1/tasks/cancel");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsafeAsyncDeclarationBeforeSubmission() {
        Plugin plugin = new AsyncPlugin() {
            public PluginManifest manifest() {
                return PluginAsyncTest.manifest(false);
            }

            public PluginResult execute(PluginInvocation call, PluginContext context) {
                throw new AssertionError("must not submit");
            }

            public PluginResult query(PluginInvocation call, PluginTask task, PluginContext context) {
                throw new AssertionError();
            }
        };
        assertThatThrownBy(() -> provider(plugin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotent");
    }

    private static PluginManifest manifest(boolean idempotent) {
        return new PluginManifest(
                "test.video",
                "1",
                "Video",
                "Test",
                null,
                "media",
                "{}",
                Map.of(),
                List.of(new ConnectorActionDefinition(
                        "generate",
                        "Generate",
                        null,
                        "{}",
                        "{\"type\":\"object\",\"required\":[\"text\"],\"properties\":{\"text\":{\"type\":\"string\"}}}",
                        idempotent,
                        Duration.ofSeconds(5),
                        ConnectorRiskLevel.WRITE,
                        Map.of("executionMode", "ASYNC"))),
                List.of());
    }

    private static PluginConnectorProvider provider(Plugin plugin) {
        return new PluginConnectorProvider(
                List.of(plugin),
                request -> new ResolvedConnection(Map.of(), Map.of()),
                new PluginHostFactory(List.of(), Map.of()));
    }

    private static ConnectorExecutionRequest request(Map<String, Object> attributes) {
        return new ConnectorExecutionRequest(
                new ConnectorKey("plugin", "test.video"),
                "generate",
                null,
                null,
                Map.of(),
                new ConnectorExecutionContext(
                        "exec-1", "tenant-a", "user-a", null, null, "run-1", "node-1", attributes));
    }
}
