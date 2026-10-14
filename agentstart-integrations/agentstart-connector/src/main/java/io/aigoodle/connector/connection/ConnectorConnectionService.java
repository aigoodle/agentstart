package io.aigoodle.connector.connection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.crypto.TenantSecretCodec;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.persistence.ConnectorConnectionEntity;
import io.aigoodle.connector.persistence.ConnectorConnectionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Tenant-scoped encrypted connection storage shared by every provider. */
public class ConnectorConnectionService {
    public record SaveConnectionRequest(
            String id,
            String tenantId,
            String installationId,
            String name,
            Map<String, Object> credentials,
            Map<String, Object> config) {}

    public record ConnectionView(
            String id,
            String tenantId,
            String installationId,
            String name,
            String status,
            boolean credentialsConfigured) {}

    private final ConnectorConnectionMapper mapper;
    private final TenantSecretCodec codec;

    public ConnectorConnectionService(ConnectorConnectionMapper mapper, TenantSecretCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    public ConnectionView save(SaveConnectionRequest request) {
        String tenantId = tenant(request.tenantId());
        ConnectorConnectionEntity entity =
                request.id() == null ? new ConnectorConnectionEntity() : requireOwned(request.id(), tenantId);
        entity.setTenantId(tenantId);
        entity.setInstallationId(required(request.installationId(), "installationId"));
        entity.setName(required(request.name(), "name"));
        if (request.credentials() != null)
            entity.setEncryptedCredentials(codec.encode(tenantId, request.credentials()));
        if (request.config() != null) entity.setEncryptedConfig(codec.encode(tenantId, request.config()));
        if (entity.getStatus() == null) entity.setStatus("CONFIGURED");
        if (entity.getId() == null) mapper.insert(entity);
        else updateOwned(entity);
        return view(entity);
    }

    public List<ConnectionView> list(String tenantId) {
        return mapper
                .selectList(new LambdaQueryWrapper<ConnectorConnectionEntity>()
                        .eq(ConnectorConnectionEntity::getTenantId, tenant(tenantId)))
                .stream()
                .map(ConnectorConnectionService::view)
                .toList();
    }

    public Map<String, Object> credentials(String id, String tenantId) {
        ConnectorConnectionEntity entity = requireOwned(id, tenant(tenantId));
        return codec.decode(entity.getTenantId(), entity.getEncryptedCredentials());
    }

    /** Decrypted configuration for a provider after validating its installation binding. */
    public ResolvedConnection resolve(String id, String tenantId, String installationId) {
        ConnectorConnectionEntity entity = requireOwned(id, tenant(tenantId));
        if (!java.util.Objects.equals(entity.getInstallationId(), installationId))
            throw new ConnectorException("connector_connection_mismatch", "Connection belongs to another installation");
        if (!"CONFIGURED".equals(entity.getStatus()))
            throw new ConnectorException("connector_connection_invalid", "Connection is not configured");
        return new ResolvedConnection(
                codec.decode(entity.getTenantId(), entity.getEncryptedConfig()),
                codec.decode(entity.getTenantId(), entity.getEncryptedCredentials()));
    }

    public record ResolvedConnection(Map<String, Object> configuration, Map<String, Object> credentials) {
        @Override
        public String toString() {
            return "ResolvedConnection[<redacted>]";
        }
    }

    /** Validates ownership and decryptability without leaking any secret value. */
    public ConnectionTestResult test(String id, String tenantId) {
        ConnectorConnectionEntity entity = requireOwned(id, tenant(tenantId));
        try {
            codec.decode(entity.getTenantId(), entity.getEncryptedCredentials());
            codec.decode(entity.getTenantId(), entity.getEncryptedConfig());
            entity.setStatus("CONFIGURED");
            entity.setLastTestedAt(LocalDateTime.now());
            updateOwned(entity);
            return new ConnectionTestResult(true, "configuration_valid", "连接配置可读取；外部连通性由具体 Action 测试确认");
        } catch (RuntimeException ex) {
            entity.setStatus("INVALID");
            entity.setLastTestedAt(LocalDateTime.now());
            updateOwned(entity);
            return new ConnectionTestResult(false, "configuration_invalid", "连接凭证无法解密");
        }
    }

    public record ConnectionTestResult(boolean success, String code, String message) {}

    public void delete(String id, String tenantId) {
        ConnectorConnectionEntity entity = requireOwned(id, tenant(tenantId));
        mapper.delete(new LambdaQueryWrapper<ConnectorConnectionEntity>()
                .eq(ConnectorConnectionEntity::getTenantId, entity.getTenantId())
                .eq(ConnectorConnectionEntity::getId, entity.getId()));
    }

    private ConnectorConnectionEntity requireOwned(String id, String tenantId) {
        ConnectorConnectionEntity entity = mapper.selectOne(new LambdaQueryWrapper<ConnectorConnectionEntity>()
                .eq(ConnectorConnectionEntity::getTenantId, tenantId)
                .eq(ConnectorConnectionEntity::getId, id)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ConnectorException("connector_connection_not_found", "Connector connection not found");
        }
        return entity;
    }

    private void updateOwned(ConnectorConnectionEntity entity) {
        mapper.update(
                entity,
                new LambdaUpdateWrapper<ConnectorConnectionEntity>()
                        .eq(ConnectorConnectionEntity::getTenantId, entity.getTenantId())
                        .eq(ConnectorConnectionEntity::getId, entity.getId()));
    }

    private static ConnectionView view(ConnectorConnectionEntity entity) {
        return new ConnectionView(
                entity.getId(),
                entity.getTenantId(),
                entity.getInstallationId(),
                entity.getName(),
                entity.getStatus(),
                entity.getEncryptedCredentials() != null);
    }

    private static String tenant(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
