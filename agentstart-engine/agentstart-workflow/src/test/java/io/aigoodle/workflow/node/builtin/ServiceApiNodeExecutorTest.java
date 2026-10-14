package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceApiNodeExecutorTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ServiceApiNodeExecutor executor =
            new ServiceApiNodeExecutor(httpClient, new ServiceApiRequestFactory(""));
    private final ExecutionContext context = ExecutionContext.start(Map.of(), null, null);

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void reportsTransportFailureWithServiceUrl() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("connection refused"));

        NodeResult result = executor.execute(serviceNode(), context);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError())
                .isEqualTo("Service call to https://internal.test/health failed: connection refused");
    }

    @Test
    void restoresThreadInterruptStatusWhenCallIsInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new InterruptedException("cancelled"));

        NodeResult result = executor.execute(serviceNode(), context);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).isEqualTo("Service call to https://internal.test/health was interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static NodeDef serviceNode() {
        return NodeDef.of("service", NodeType.SERVICE_API).with("url", "https://internal.test/health");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }
}
