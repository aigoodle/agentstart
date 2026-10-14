package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls a trusted internal HTTP service. Unlike the general HTTP node, this
 * node intentionally supports only URL, method, headers, query parameters and
 * timeout; authentication is expected from infrastructure such as mTLS or a gateway.
 */
public class ServiceApiNodeExecutor implements NodeExecutor {

    private final HttpClient httpClient;
    private final ServiceApiRequestFactory requestFactory;

    public ServiceApiNodeExecutor() {
        this("");
    }

    public ServiceApiNodeExecutor(String baseUrl) {
        this(defaultHttpClient(), new ServiceApiRequestFactory(baseUrl));
    }

    ServiceApiNodeExecutor(HttpClient httpClient, ServiceApiRequestFactory requestFactory) {
        this.httpClient = httpClient;
        this.requestFactory = requestFactory;
    }

    @Override
    public NodeType type() {
        return NodeType.SERVICE_API;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        context.throwIfCancelled();
        ServiceApiRequestFactory.PreparedServiceRequest preparedRequest;
        try {
            preparedRequest = requestFactory.create(node, context);
        } catch (ServiceApiRequestFactory.InvalidServiceRequestException invalidRequest) {
            return NodeResult.failure(invalidRequest.getMessage());
        }
        NodeResult result = send(preparedRequest);
        context.throwIfCancelled();
        return result;
    }

    private NodeResult send(ServiceApiRequestFactory.PreparedServiceRequest preparedRequest) {
        try {
            HttpResponse<String> response =
                    httpClient.send(preparedRequest.request(), HttpResponse.BodyHandlers.ofString());
            return NodeResult.empty()
                    .externalStatus(response.statusCode())
                    .output("status", response.statusCode())
                    .output("body", response.body());
        } catch (IOException transportFailure) {
            return NodeResult.failure(
                    "Service call to " + preparedRequest.url() + " failed: " + transportFailure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return NodeResult.failure("Service call to " + preparedRequest.url() + " was interrupted");
        }
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
