package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.*;
import io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection;
import io.aigoodle.connector.execution.*;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.plugin.config.PluginAutoConfiguration;
import io.aigoodle.plugin.host.*;
import io.aigoodle.plugin.remote.RemotePlugin;
import io.aigoodle.plugin.runtime.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PluginRuntimeTest {
    static PluginManifest manifest() {
        return new PluginManifest(
                "acme.video",
                "1.0.0",
                "Video",
                "Video plugin",
                null,
                "media",
                "{}",
                Map.of(),
                List.of(new ConnectorActionDefinition(
                        "prepare",
                        "Prepare",
                        null,
                        "{\"type\":\"object\",\"properties\":{\"productId\":{\"type\":\"string\"}}}",
                        "{}",
                        true,
                        Duration.ofSeconds(5),
                        ConnectorRiskLevel.READ,
                        Map.of())),
                List.of("product.read"));
    }

    static ConnectorExecutionRequest request() {
        return new ConnectorExecutionRequest(
                new ConnectorKey("plugin", "acme.video"),
                "prepare",
                null,
                null,
                Map.of("productId", "123"),
                new ConnectorExecutionContext("exec-1", "tenant-a", "user-a", null, null, "run-1", "node-1", Map.of()));
    }

    static Plugin local() {
        return new Plugin() {
            public PluginManifest manifest() {
                return PluginRuntimeTest.manifest();
            }

            public PluginResult execute(PluginInvocation invocation, PluginContext context) {
                return PluginResult.success(context.host().call("product.read", invocation.inputs()));
            }
        };
    }

    static PluginHostCapability capability(AtomicReference<ConnectorExecutionContext> captured) {
        return new PluginHostCapability() {
            public String name() {
                return "product.read";
            }

            public Object execute(ConnectorExecutionContext identity, Map<String, Object> inputs) {
                captured.set(identity);
                return Map.of("title", "Product " + inputs.get("productId"));
            }
        };
    }

    @Test
    void localBeanIsDiscoveredAndCallsHostWithOriginalIdentity() {
        AtomicReference<ConnectorExecutionContext> captured = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PluginAutoConfiguration.class))
                .withBean(Plugin.class, PluginRuntimeTest::local)
                .withBean(PluginHostCapability.class, () -> capability(captured))
                .withBean(PluginConnectionResolver.class, () -> request -> new ResolvedConnection(Map.of(), Map.of()))
                .withPropertyValues("spring-agent.plugin.grants[acme.video][0]=product.read")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var provider = context.getBean(PluginConnectorProvider.class);
                    var registry = new ConnectorRegistry(List.of(provider));
                    var gateway = new DefaultConnectorExecutionGateway(registry, List.of());
                    assertThat(registry.all().getFirst().metadata()).containsEntry("runtime", "JAVA");
                    assertThat(gateway.execute(request()).data()).isEqualTo(Map.of("title", "Product 123"));
                    assertThat(captured.get().tenantId()).isEqualTo("tenant-a");
                    assertThat(captured.get().userId()).isEqualTo("user-a");
                });
    }

    @Test
    void declarationDoesNotGrantHostAccess() {
        var host = new PluginHostFactory(List.of(capability(new AtomicReference<>())), Map.of())
                .forInvocation(manifest(), request().context());
        assertThatThrownBy(() -> host.call("product.read", Map.of()))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("not granted");
    }

    @Test
    void duplicatePluginIdsFailInsteadOfShadowing() {
        assertThatThrownBy(() -> new PluginConnectorProvider(
                        List.of(local(), local()), request -> null, new PluginHostFactory(List.of(), Map.of())))
                .hasMessageContaining("Duplicate plugin");
    }

    @Test
    void remoteProtocolSupportsHostRoundTripWithoutAnInboundCallback() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Map<String, Object>> resume = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/execute", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = JsonUtils.parseMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = JsonUtils.toJson(Map.of(
                            "hostCall",
                            Map.of(
                                    "id",
                                    "call-1",
                                    "capability",
                                    "product.read",
                                    "arguments",
                                    Map.of("productId", "123"),
                                    "state",
                                    body.get("pluginId"))))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/v1/resume", exchange -> {
            var body = JsonUtils.parseMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            resume.set(body);
            byte[] bytes = JsonUtils.toJson(new RemotePlugin.Reply(PluginResult.success(body.get("hostResult")), null))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var remote = new RemotePlugin(
                    manifest(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "service-token",
                    Duration.ofSeconds(5));
            AtomicReference<ConnectorExecutionContext> captured = new AtomicReference<>();
            var provider = new PluginConnectorProvider(
                    List.of(remote),
                    request -> new ResolvedConnection(Map.of(), Map.of()),
                    new PluginHostFactory(
                            List.of(capability(captured)), Map.of("acme.video", List.of("product.read"))));
            assertThat(provider.execute(request()).data()).isEqualTo(Map.of("title", "Product 123"));
            assertThat(authorization.get()).isEqualTo("Bearer service-token");
            assertThat(resume.get()).containsEntry("hostCallId", "call-1").containsEntry("state", "acme.video");
            assertThat(captured.get()).isEqualTo(request().context());
            assertThat(provider.discover().getFirst().metadata()).containsEntry("runtime", "REMOTE_HTTP");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void contextStringDoesNotExposeConnectionSecrets() {
        var context = new PluginContext(
                request().context(),
                Map.of("secret", "config-secret"),
                Map.of("token", "secret-token"),
                (name, args) -> null);
        assertThat(context.toString()).doesNotContain("config-secret", "secret-token");
    }
}
