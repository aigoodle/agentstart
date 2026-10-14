package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceApiRequestFactoryTest {

    @Test
    void createsARequestFromReadableWorkflowConfiguration() {
        ExecutionContext context = new ExecutionContext();
        context.getPool().setSystem("tenant", "acme team");
        NodeDef node = NodeDef.of("service", NodeType.SERVICE_API)
                .with("method", " post ")
                .with("url", "/customers")
                .with(
                        "parameters",
                        List.of(
                                Map.of("name", "tenant", "value", "{{#sys.tenant#}}"),
                                Map.of("name", "", "value", "ignored")))
                .with("headers", List.of(Map.of("name", "X-Tenant", "value", "{{#sys.tenant#}}")))
                .with("timeoutSeconds", 0);

        ServiceApiRequestFactory.PreparedServiceRequest prepared =
                new ServiceApiRequestFactory(" https://internal.example/// ").create(node, context);

        assertThat(prepared.url()).isEqualTo("https://internal.example/customers?tenant=acme+team");
        assertThat(prepared.request().method()).isEqualTo("POST");
        assertThat(prepared.request().headers().firstValue("X-Tenant")).contains("acme team");
        assertThat(prepared.request().timeout()).hasValue(java.time.Duration.ofSeconds(1));
    }

    @Test
    void explainsWhyARelativeUrlCannotBePrepared() {
        NodeDef node = NodeDef.of("service", NodeType.SERVICE_API).with("url", "/customers");

        assertThatThrownBy(() -> new ServiceApiRequestFactory("").create(node, new ExecutionContext()))
                .isInstanceOf(ServiceApiRequestFactory.InvalidServiceRequestException.class)
                .hasMessageContaining("no agent-boot.http-base-url is configured");
    }

    @Test
    void executorSendsThePreparedRequestAndMapsTheResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] response = "healthy".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            NodeDef node = NodeDef.of("service", NodeType.SERVICE_API).with("url", "/health");
            ServiceApiNodeExecutor executor = new ServiceApiNodeExecutor(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            NodeResult result = executor.execute(node, new ExecutionContext());

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutputs()).containsEntry("status", 202).containsEntry("body", "healthy");
        } finally {
            server.stop(0);
        }
    }
}
