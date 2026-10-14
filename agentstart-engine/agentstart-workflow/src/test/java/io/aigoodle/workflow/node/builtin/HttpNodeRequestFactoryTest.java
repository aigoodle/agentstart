package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpNodeRequestFactoryTest {

    private final ExecutionContext context =
            ExecutionContext.start(Map.of("tenant", "acme", "apiKey", "secret"), null, null);

    @Test
    void compilesRelativeUrlParametersHeadersAndAuthorization() {
        NodeDef node = httpNode()
                .with("method", "post")
                .with("url", "/customers")
                .with("parameters", List.of(Map.of("name", "tenant name", "value", "{{#sys.tenant#}}")))
                .with("headers", List.of(Map.of("name", "X-Tenant", "value", "{{#sys.tenant#}}")))
                .with(
                        "authorization",
                        Map.of(
                                "auth_type", "api_key",
                                "api_key_header_prefix", "bearer",
                                "api_key_value", "{{#sys.apiKey#}}"))
                .with("bodyType", "JSON")
                .with("body", Map.of("JSON", "{}"))
                .with("maxRetries", 3);

        HttpNodeRequestFactory.PreparedRequest prepared =
                new HttpNodeRequestFactory("https://example.test/api/").prepare(node, context);

        assertThat(prepared.failed()).isFalse();
        assertThat(prepared.request().method()).isEqualTo("POST");
        assertThat(prepared.request().uri().toString())
                .isEqualTo("https://example.test/api/customers?tenant+name=acme");
        assertThat(prepared.request().headers().firstValue("X-Tenant")).contains("acme");
        assertThat(prepared.request().headers().firstValue("Authorization")).contains("Bearer secret");
        assertThat(prepared.request().headers().firstValue("Content-Type")).contains("application/json; charset=utf-8");
        assertThat(prepared.retryCount()).isEqualTo(3);
    }

    @Test
    void rejectsUnsupportedMethodBeforeCreatingRequest() {
        NodeDef node = httpNode().with("method", "TRACE").with("url", "https://example.test");

        HttpNodeRequestFactory.PreparedRequest prepared = new HttpNodeRequestFactory("").prepare(node, context);

        assertThat(prepared.failed()).isTrue();
        assertThat(prepared.error()).isEqualTo("Unsupported HTTP method: TRACE");
        assertThat(prepared.request()).isNull();
    }

    @Test
    void explainsRelativeUrlWhenNoBaseUrlIsConfigured() {
        NodeDef node = httpNode().with("url", "/customers");

        HttpNodeRequestFactory.PreparedRequest prepared = new HttpNodeRequestFactory("").prepare(node, context);

        assertThat(prepared.failed()).isTrue();
        assertThat(prepared.error()).contains("not absolute", "http-base-url");
    }

    @Test
    void capsRetryCountFromDesignerConfiguration() {
        NodeDef node = httpNode().with("url", "https://example.test").with("maxRetries", 999);

        HttpNodeRequestFactory.PreparedRequest prepared = new HttpNodeRequestFactory("").prepare(node, context);

        assertThat(prepared.retryCount()).isEqualTo(10);
    }

    private static NodeDef httpNode() {
        return NodeDef.of("http", NodeType.HTTP_REQUEST);
    }
}
