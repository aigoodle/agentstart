package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.connector.execution.ConnectorExecutionQueryService;
import io.aigoodle.connector.persistence.ConnectorExecutionEntity;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnBean(ConnectorExecutionQueryService.class)
@RequestMapping("/connector-executions")
public class ConnectorExecutionController {
    private final ConnectorExecutionQueryService executions;

    public ConnectorExecutionController(ConnectorExecutionQueryService executions) {
        this.executions = executions;
    }

    @GetMapping
    public ApiResponse<List<ConnectorExecutionEntity>> list(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String connectorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(executions.list(currentTenantId(), provider, connectorId, status, limit));
    }
}
