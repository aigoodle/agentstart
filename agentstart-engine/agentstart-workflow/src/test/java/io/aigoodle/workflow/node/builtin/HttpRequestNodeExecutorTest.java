package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpRequestNodeExecutorTest {

    private final ExecutionContext context = ExecutionContext.start(Map.of(), null, null);

    @Test
    void retriesTransportFailureAndReturnsTheFollowingResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = response(200, "ok");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("connection reset"))
                .thenReturn(response);
        AtomicInteger backoffCount = new AtomicInteger();
        HttpRequestNodeExecutor executor = executor(httpClient, backoffCount::incrementAndGet);

        NodeResult result = executor.execute(httpNode().with("maxRetries", 2), context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs()).containsEntry("status", 200).containsEntry("body", "ok");
        assertThat(backoffCount).hasValue(1);
        verify(httpClient, times(2)).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    @Test
    void reportsAttemptCountAfterRetriesAreExhausted() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("connection refused"));
        AtomicInteger backoffCount = new AtomicInteger();
        HttpRequestNodeExecutor executor = executor(httpClient, backoffCount::incrementAndGet);

        NodeResult result = executor.execute(httpNode().with("maxRetries", 2), context);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).contains("after 3 attempts", "connection refused");
        assertThat(backoffCount).hasValue(2);
        verify(httpClient, times(3)).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    @Test
    void classifiesServiceUnavailableAsRetryableFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> serviceUnavailable = response(503, "unavailable");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(serviceUnavailable);
        AtomicInteger backoffCount = new AtomicInteger();
        HttpRequestNodeExecutor executor = executor(httpClient, backoffCount::incrementAndGet);

        NodeResult result = executor.execute(httpNode().with("maxRetries", 5), context);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo("HTTP_503");
        assertThat(result.getRetryable()).isTrue();
        assertThat(result.getExternalStatus()).isEqualTo(503);
        assertThat(backoffCount).hasValue(0);
        verify(httpClient).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    private static HttpRequestNodeExecutor executor(
            HttpClient httpClient, HttpRequestNodeExecutor.RetryBackoff retryBackoff) {
        return new HttpRequestNodeExecutor(httpClient, new HttpNodeRequestFactory(""), retryBackoff);
    }

    private static NodeDef httpNode() {
        return NodeDef.of("http", NodeType.HTTP_REQUEST).with("url", "https://example.test/resource");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        return response;
    }
}
