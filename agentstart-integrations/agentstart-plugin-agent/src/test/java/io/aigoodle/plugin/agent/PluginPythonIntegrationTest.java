package io.aigoodle.plugin.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorKey;
import io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection;
import io.aigoodle.connector.execution.*;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.plugin.PluginManifest;
import io.aigoodle.plugin.host.*;
import io.aigoodle.plugin.remote.RemotePlugin;
import io.aigoodle.plugin.runtime.PluginConnectorProvider;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;

/** Set -Dplugin.python=/path/to/python to exercise the actual separate Python process. */
class PluginPythonIntegrationTest {
    @Test
    void separatePythonProcessQueriesHostAndCallsNonStreamingModelOverHttp() throws Exception {
        String python = System.getProperty("plugin.python");
        Assumptions.assumeTrue(
                python != null && !python.isBlank(),
                "Set plugin.python to run the independent Python runtime integration");
        Path directory = Path.of("..", "examples", "plugins", "python-video")
                .toAbsolutePath()
                .normalize();
        var manifest = JsonUtils.parse(Files.readString(directory.resolve("manifest.json")), PluginManifest.class);
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var builder = new ProcessBuilder(python, "main.py")
                .directory(directory.toFile())
                .redirectErrorStream(true);
        builder.environment().put("PORT", String.valueOf(port));
        builder.environment().put("PLUGIN_SERVICE_TOKEN", "integration-service-token");
        Process process = builder.start();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var http = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + port);
            boolean ready = false;
            for (int attempt = 0; attempt < 100 && process.isAlive(); attempt++) {
                try {
                    ready = http.send(
                                            HttpRequest.newBuilder(endpoint.resolve("/health"))
                                                    .timeout(Duration.ofMillis(200))
                                                    .build(),
                                            HttpResponse.BodyHandlers.discarding())
                                    .statusCode()
                            == 200;
                } catch (java.io.IOException ignored) {
                }
                if (ready) break;
                Thread.sleep(50);
            }
            assertThat(ready).as("Python health endpoint").isTrue();
            var tokens = new PluginHostTokenService(
                    "integration-only-signing-key-at-least-32-bytes",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/host");
            var models = mock(ModelService.class);
            var model = mock(ChatModel.class);
            var entity = new ModelEntity();
            entity.setEnabled(true);
            entity.setModelType(ModelType.LLM);
            when(models.require("tenant-a", "model-a")).thenReturn(entity);
            when(models.getChatModel("tenant-a", "model-a")).thenReturn(model);
            when(model.call(any(Prompt.class))).thenAnswer(call -> {
                Prompt prompt = call.getArgument(0);
                assertThat(prompt.getContents()).contains("Product 123");
                return new ChatResponse(List.of(new Generation(new AssistantMessage("Generated test script"))));
            });
            PluginHostCapability product = new PluginHostCapability() {
                public String name() {
                    return "product.read";
                }

                public Object execute(ConnectorExecutionContext identity, Map<String, Object> args) {
                    assertThat(identity.tenantId()).isEqualTo("tenant-a");
                    return Map.of("name", "Product " + args.get("productId"));
                }
            };
            var hosts = new PluginHostFactory(
                    List.of(product, new PluginModelCapability(() -> models)),
                    Map.of(manifest.id(), List.of("product.read", "model.chat")));
            var remote =
                    new RemotePlugin(manifest, endpoint, "integration-service-token", Duration.ofSeconds(15), tokens);
            var provider = new PluginConnectorProvider(
                    List.of(remote), request -> new ResolvedConnection(Map.of(), Map.of()), hosts);
            var registry = new ConnectorRegistry(List.of(provider));
            var installs = mock(ConnectorInstallationService.class);
            when(installs.list("tenant-a"))
                    .thenReturn(List.of(new ConnectorInstallationService.InstallationView(
                            "i", "tenant-a", "plugin", manifest.id(), manifest.version(), "REMOTE", true, "REVIEWED")));
            var api = new PluginHostApi(tokens, hosts, () -> registry, () -> installs);
            AtomicReference<Throwable> callbackError = new AtomicReference<>();
            server.createContext("/host/models/chat", exchange -> {
                try {
                    var args = JsonUtils.parseMap(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    String token = exchange.getRequestHeaders()
                            .getFirst("Authorization")
                            .substring(7);
                    byte[] output = JsonUtils.toJson(api.call(token, "model.chat", args))
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, output.length);
                    exchange.getResponseBody().write(output);
                } catch (Throwable error) {
                    callbackError.set(error);
                    exchange.sendResponseHeaders(500, -1);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            var result = new DefaultConnectorExecutionGateway(registry, List.of())
                    .execute(new ConnectorExecutionRequest(
                            new ConnectorKey("plugin", manifest.id()),
                            "prepare",
                            null,
                            null,
                            Map.of("productId", "123", "modelId", "model-a"),
                            new ConnectorExecutionContext(
                                    "exec", "tenant-a", "user-a", null, null, null, null, Map.of())));
            assertThat(callbackError.get()).isNull();
            assertThat(((Map<?, ?>) result.data()).get("text")).isEqualTo("Generated test script");
            verify(models).getChatModel("tenant-a", "model-a");
        } finally {
            server.stop(0);
            process.destroy();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
