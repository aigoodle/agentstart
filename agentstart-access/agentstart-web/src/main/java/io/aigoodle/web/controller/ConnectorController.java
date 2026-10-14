package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;
import static io.aigoodle.common.context.UserContextHolder.currentUserId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.connector.ConnectorDefinition;
import io.aigoodle.connector.ConnectorKey;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.execution.ConnectorResult;
import io.aigoodle.connector.registry.ConnectorRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/** Provider-neutral catalog and direct test-execution API. */
@RestController
@ConditionalOnBean(ConnectorRegistry.class)
@RequestMapping("/connectors")
public class ConnectorController {
    private final ConnectorRegistry registry;
    private final ConnectorExecutionGateway gateway;

    public ConnectorController(ConnectorRegistry registry, ConnectorExecutionGateway gateway) {
        this.registry = registry;
        this.gateway = gateway;
    }

    @GetMapping
    public ApiResponse<List<ConnectorDefinition>> list() {
        return ApiResponse.ok(registry.all());
    }

    @GetMapping("/{provider}/{connectorId}")
    public ApiResponse<ConnectorDefinition> get(@PathVariable String provider, @PathVariable String connectorId) {
        return ApiResponse.ok(registry.get(new ConnectorKey(provider, connectorId)));
    }

    @PostMapping("/refresh")
    public ApiResponse<List<ConnectorDefinition>> refresh() {
        registry.refresh();
        return ApiResponse.ok(registry.all());
    }

    @PostMapping("/{provider}/{connectorId}/actions/{actionId}/execute")
    public ApiResponse<ConnectorResult> execute(
            @PathVariable String provider,
            @PathVariable String connectorId,
            @PathVariable String actionId,
            @RequestBody(required = false) Map<String, Object> arguments) {
        ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                new ConnectorKey(provider, connectorId),
                actionId,
                null,
                null,
                arguments,
                new ConnectorExecutionContext(
                        null,
                        currentTenantId(),
                        currentUserId(),
                        null,
                        null,
                        null,
                        null,
                        Map.of("surface", "management-api")));
        return ApiResponse.ok(gateway.execute(request));
    }
}
