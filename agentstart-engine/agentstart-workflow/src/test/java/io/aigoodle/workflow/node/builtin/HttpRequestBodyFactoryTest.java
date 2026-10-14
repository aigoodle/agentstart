package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class HttpRequestBodyFactoryTest {

    private final HttpRequestBodyFactory bodyFactory = new HttpRequestBodyFactory();
    private final ExecutionContext context =
            ExecutionContext.start(Map.of("customer", "Alice", "token", "a b"), null, null);

    @Test
    void rendersJsonBodyAndDeclaresContentType() {
        NodeDef node =
                httpNode().with("bodyType", "JSON").with("body", Map.of("JSON", "{\"name\":\"{{#sys.customer#}}\"}"));

        HttpRequestBodyFactory.HttpRequestBody body = bodyFactory.create(node, context);

        assertThat(body.failed()).isFalse();
        assertThat(body.contentType()).isEqualTo("application/json; charset=utf-8");
        assertThat(readBody(body.publisher())).isEqualTo("{\"name\":\"Alice\"}");
    }

    @Test
    void encodesFormNamesAndRenderedValues() {
        NodeDef node = httpNode()
                .with("bodyType", "X_WWW_FORM_URLENCODED")
                .with(
                        "body",
                        Map.of(
                                "X_WWW_FORM_URLENCODED",
                                List.of(Map.of("name", "access token", "value", "{{#sys.token#}}"))));

        HttpRequestBodyFactory.HttpRequestBody body = bodyFactory.create(node, context);

        assertThat(readBody(body.publisher())).isEqualTo("access+token=a+b");
        assertThat(body.contentType()).startsWith("application/x-www-form-urlencoded");
    }

    @Test
    void rejectsMultipartFileRowsInsteadOfSilentlyDroppingThem() {
        NodeDef node = httpNode()
                .with("bodyType", "FORM_DATA")
                .with(
                        "body",
                        Map.of("FORM_DATA", List.of(Map.of("name", "attachment", "type", "FILE", "value", "file-id"))));

        HttpRequestBodyFactory.HttpRequestBody body = bodyFactory.create(node, context);

        assertThat(body.failed()).isTrue();
        assertThat(body.error()).contains("attachment", "not supported");
    }

    @Test
    void preservesLegacyBareStringBodyWithoutInventingContentType() {
        NodeDef node = httpNode().with("body", "Hello {{#sys.customer#}}");

        HttpRequestBodyFactory.HttpRequestBody body = bodyFactory.create(node, context);

        assertThat(readBody(body.publisher())).isEqualTo("Hello Alice");
        assertThat(body.contentType()).isNull();
    }

    private static NodeDef httpNode() {
        return NodeDef.of("http", NodeType.HTTP_REQUEST);
    }

    private static String readBody(BodyPublisher publisher) {
        CompletableFuture<byte[]> content = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                content.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                content.complete(bytes.toByteArray());
            }
        });
        return new String(content.join(), StandardCharsets.UTF_8);
    }
}
