package io.aigoodle.connector.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.aigoodle.connector.persistence.ConnectorExecutionEntity;
import io.aigoodle.connector.persistence.ConnectorExecutionMapper;
import java.util.List;

/** Read-only, tenant-scoped execution history for the Connector management UI. */
public class ConnectorExecutionQueryService {
    private final ConnectorExecutionMapper mapper;

    public ConnectorExecutionQueryService(ConnectorExecutionMapper mapper) {
        this.mapper = mapper;
    }

    public List<ConnectorExecutionEntity> list(
            String tenantId, String provider, String connectorId, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        QueryWrapper<ConnectorExecutionEntity> query = new QueryWrapper<ConnectorExecutionEntity>()
                .eq("tenant_id", tenant(tenantId))
                .eq(provider != null && !provider.isBlank(), "provider", provider)
                .eq(connectorId != null && !connectorId.isBlank(), "connector_id", connectorId)
                .eq(status != null && !status.isBlank(), "status", status)
                .orderByDesc("created_at")
                .last("limit " + safeLimit);
        return mapper.selectList(query);
    }

    private static String tenant(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }
}
