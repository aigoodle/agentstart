package io.aigoodle.connector.execution;

import io.aigoodle.connector.ConnectorActionDefinition;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.registry.ConnectorRegistry;
import java.util.List;

/** Governed provider dispatch boundary shared by agents, workflows and direct API calls. */
public class DefaultConnectorExecutionGateway implements ConnectorExecutionGateway {
    private final ConnectorRegistry registry;
    private final List<ConnectorExecutionPolicy> policies;
    private final ConnectorExecutionRecorder recorder;

    public DefaultConnectorExecutionGateway(ConnectorRegistry registry, List<ConnectorExecutionPolicy> policies) {
        this(registry, policies, null);
    }

    public DefaultConnectorExecutionGateway(
            ConnectorRegistry registry, List<ConnectorExecutionPolicy> policies, ConnectorExecutionRecorder recorder) {
        this.registry = registry;
        this.policies = policies == null ? List.of() : List.copyOf(policies);
        this.recorder = recorder;
    }

    @Override
    public ConnectorResult execute(ConnectorExecutionRequest request) {
        long started = System.nanoTime();
        ConnectorResult result = null;
        Throwable failure = null;
        try {
            var definition = registry.get(request.connector());
            ConnectorActionDefinition action = definition.action(request.actionId());
            for (ConnectorExecutionPolicy policy : policies) {
                ConnectorExecutionPolicy.Decision decision = policy.evaluate(action, request);
                if (decision != null && !decision.allowed()) {
                    throw new ConnectorException(
                            "connector_denied",
                            decision.reason() == null ? "Connector execution denied" : decision.reason());
                }
            }
            result = registry.provider(request.connector().provider()).execute(request);
            if (result == null)
                throw new ConnectorException("connector_empty_result", "Connector provider returned no result");
            return result;
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            if (recorder != null) recorder.record(request, (System.nanoTime() - started) / 1_000_000, result, failure);
        }
    }
}
