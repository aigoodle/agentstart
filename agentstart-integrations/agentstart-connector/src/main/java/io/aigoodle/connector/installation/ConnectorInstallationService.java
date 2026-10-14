package io.aigoodle.connector.installation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorDefinition;
import io.aigoodle.connector.persistence.ConnectorInstallationEntity;
import io.aigoodle.connector.persistence.ConnectorInstallationMapper;
import io.aigoodle.connector.registry.ConnectorRegistry;
import java.util.List;

/** Persists tenant installation state independently from the provider's live catalog. */
public class ConnectorInstallationService {
    public record InstallationView(
            String id,
            String tenantId,
            String provider,
            String connectorId,
            String version,
            String source,
            boolean enabled,
            String trustLevel) {}

    private final ConnectorInstallationMapper mapper;
    private final ConnectorRegistry registry;

    public ConnectorInstallationService(ConnectorInstallationMapper mapper, ConnectorRegistry registry) {
        this.mapper = mapper;
        this.registry = registry;
    }

    public List<InstallationView> synchronize(String tenantId) {
        String tenant = tenant(tenantId);
        for (ConnectorDefinition definition : registry.all()) upsert(tenant, definition);
        return list(tenant);
    }

    public List<InstallationView> list(String tenantId) {
        return mapper
                .selectList(new LambdaQueryWrapper<ConnectorInstallationEntity>()
                        .eq(ConnectorInstallationEntity::getTenantId, tenant(tenantId)))
                .stream()
                .map(ConnectorInstallationService::view)
                .toList();
    }

    public InstallationView setEnabled(String id, String tenantId, boolean enabled) {
        ConnectorInstallationEntity entity = requireOwned(id, tenant(tenantId));
        entity.setEnabled(enabled);
        updateOwned(entity);
        return view(entity);
    }

    private void upsert(String tenant, ConnectorDefinition definition) {
        ConnectorInstallationEntity entity = mapper.selectOne(new LambdaQueryWrapper<ConnectorInstallationEntity>()
                .eq(ConnectorInstallationEntity::getTenantId, tenant)
                .eq(ConnectorInstallationEntity::getProvider, definition.key().provider())
                .eq(
                        ConnectorInstallationEntity::getConnectorId,
                        definition.key().connectorId())
                .last("limit 1"));
        boolean insert = entity == null;
        if (insert) {
            entity = new ConnectorInstallationEntity();
            entity.setTenantId(tenant);
            entity.setProvider(definition.key().provider());
            entity.setConnectorId(definition.key().connectorId());
            entity.setEnabled(true);
        }
        entity.setVersion(definition.version());
        entity.setSource(definition.source().name());
        entity.setTrustLevel(definition.trustLevel().name());
        entity.setManifestJson(JsonUtils.toJson(definition));
        entity.setConfigSchemaJson(definition.configurationSchema());
        if (insert) mapper.insert(entity);
        else updateOwned(entity);
    }

    private ConnectorInstallationEntity requireOwned(String id, String tenant) {
        ConnectorInstallationEntity entity = mapper.selectOne(new LambdaQueryWrapper<ConnectorInstallationEntity>()
                .eq(ConnectorInstallationEntity::getTenantId, tenant)
                .eq(ConnectorInstallationEntity::getId, id)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalArgumentException("connector installation not found");
        }
        return entity;
    }

    private void updateOwned(ConnectorInstallationEntity entity) {
        mapper.update(
                entity,
                new LambdaUpdateWrapper<ConnectorInstallationEntity>()
                        .eq(ConnectorInstallationEntity::getTenantId, entity.getTenantId())
                        .eq(ConnectorInstallationEntity::getId, entity.getId()));
    }

    private static InstallationView view(ConnectorInstallationEntity entity) {
        return new InstallationView(
                entity.getId(),
                entity.getTenantId(),
                entity.getProvider(),
                entity.getConnectorId(),
                entity.getVersion(),
                entity.getSource(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getTrustLevel());
    }

    private static String tenant(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }
}
