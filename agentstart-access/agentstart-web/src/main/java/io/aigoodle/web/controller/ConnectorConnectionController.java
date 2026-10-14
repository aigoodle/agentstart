package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.connection.ConnectorConnectionService.ConnectionView;
import io.aigoodle.connector.connection.ConnectorConnectionService.SaveConnectionRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-scoped connection management. Secret values are accepted but never returned. */
@RestController
@ConditionalOnBean(ConnectorConnectionService.class)
@RequestMapping("/connector-connections")
public class ConnectorConnectionController {
    private final ConnectorConnectionService connections;

    public ConnectorConnectionController(ConnectorConnectionService connections) {
        this.connections = connections;
    }

    @GetMapping
    public ApiResponse<List<ConnectionView>> list() {
        return ApiResponse.ok(connections.list(currentTenantId()));
    }

    @PostMapping
    public ApiResponse<ConnectionView> save(@RequestBody SaveConnectionRequest request) {
        SaveConnectionRequest trusted = new SaveConnectionRequest(
                request.id(),
                currentTenantId(),
                request.installationId(),
                request.name(),
                request.credentials(),
                request.config());
        return ApiResponse.ok(connections.save(trusted));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        connections.delete(id, currentTenantId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ConnectorConnectionService.ConnectionTestResult> test(@PathVariable String id) {
        return ApiResponse.ok(connections.test(id, currentTenantId()));
    }
}
