package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.channel.persistence.ChannelConnectionEntity;
import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.common.crypto.TenantSecretCodec;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.persistence.TenantSqlScope;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores user/tenant channel connections and reconciles their desired state to a runtime. */
public class ChannelConnectionService {
    public record ReconcileCandidate(String id, String tenantId) {}

    public record HealthCandidate(String id, String tenantId) {}

    public record SaveRequest(
            String id,
            String tenantId,
            String ownerType,
            String ownerId,
            String provider,
            String channelId,
            String name,
            Map<String, Object> credentials,
            Map<String, Object> config,
            Boolean enabled,
            String agentId,
            String agentVersionId,
            String runtimeNodeId) {
        public SaveRequest(
                String id,
                String tenantId,
                String ownerType,
                String ownerId,
                String provider,
                String channelId,
                String name,
                Map<String, Object> credentials,
                Map<String, Object> config,
                Boolean enabled,
                String agentId,
                String runtimeNodeId) {
            this(
                    id,
                    tenantId,
                    ownerType,
                    ownerId,
                    provider,
                    channelId,
                    name,
                    credentials,
                    config,
                    enabled,
                    agentId,
                    null,
                    runtimeNodeId);
        }
    }

    public record View(
            String id,
            String tenantId,
            String ownerType,
            String ownerId,
            String provider,
            String channelId,
            String name,
            String desiredStatus,
            String runtimeStatus,
            String runtimeAccountId,
            String agentId,
            String agentVersionId,
            String runtimeNodeId,
            Map<String, Object> runtimeMetadata,
            boolean credentialsConfigured,
            String lastError,
            LocalDateTime lastTestedAt,
            long configVersion) {}

    /** Safe edit projection: write-only values are never returned to clients. */
    public record EditConfiguration(
            Map<String, Object> credentials, Map<String, Object> config, List<String> configuredSecretFields) {}

    public record Route(String connectionId, String tenantId, String ownerId, String agentId) {}

    public record Ownership(String connectionId, String tenantId, String ownerId, String runtimeNodeId) {}

    public record RoutingConnection(
            String connectionId,
            String tenantId,
            String ownerId,
            String explicitAgentId,
            String explicitAgentVersionId,
            boolean active) {
        public RoutingConnection(
                String connectionId, String tenantId, String ownerId, String explicitAgentId, boolean active) {
            this(connectionId, tenantId, ownerId, explicitAgentId, null, active);
        }
    }

    private final ChannelConnectionMapper mapper;
    private final TenantSecretCodec codec;
    private final ChannelRuntimeRegistry runtimes;

    public ChannelConnectionService(
            ChannelConnectionMapper mapper, TenantSecretCodec codec, ChannelRuntimeRegistry runtimes) {
        this.mapper = mapper;
        this.codec = codec;
        this.runtimes = runtimes;
    }

    public View save(SaveRequest request) {
        String tenantId = tenant(request.tenantId());
        ChannelConnectionEntity entity =
                request.id() == null ? new ChannelConnectionEntity() : requireOwned(request.id(), tenantId);
        boolean creating = entity.getId() == null;
        entity.setTenantId(tenantId);
        String ownerType = defaulted(request.ownerType(), "USER").toUpperCase(java.util.Locale.ROOT);
        entity.setOwnerType(ownerType);
        // Tenant accounts are owned by the authenticated tenant boundary, not by
        // the administrator who happened to create them. Keep owner_id non-null
        // for existing schemas and routing code by storing the trusted tenant id.
        entity.setOwnerId("TENANT".equals(ownerType) ? tenantId : required(request.ownerId(), "ownerId"));
        entity.setProvider(required(request.provider(), "provider"));
        entity.setChannelId(required(request.channelId(), "channelId"));
        if (creating) requireInstanceCapacity(tenantId, entity.getProvider(), entity.getChannelId());
        entity.setName(required(request.name(), "name"));
        // Native channel credentials belong to either an employee or the tenant. Applications and
        // workflows subscribe separately and must never be embedded into an account record.
        boolean nativeChannel = "native".equalsIgnoreCase(entity.getProvider());
        entity.setAgentId(nativeChannel ? null : trimToNull(request.agentId()));
        entity.setAgentVersionId(nativeChannel ? null : trimToNull(request.agentVersionId()));
        if (creating) entity.setRuntimeNodeId(selectNode(entity.getProvider(), request.runtimeNodeId()));
        if (request.credentials() != null) {
            Map<String, Object> existing =
                    creating ? Map.of() : codec.decode(tenantId, entity.getEncryptedCredentials());
            entity.setEncryptedCredentials(codec.encode(tenantId, mergeValues(existing, request.credentials())));
        }
        if (request.config() != null) {
            Map<String, Object> existing = creating ? Map.of() : codec.decode(tenantId, entity.getEncryptedConfig());
            entity.setEncryptedConfig(codec.encode(tenantId, mergeValues(existing, request.config())));
        }
        entity.setDesiredStatus(Boolean.FALSE.equals(request.enabled()) ? "DISABLED" : "ACTIVE");
        entity.setRuntimeStatus("PENDING");
        entity.setReconcileAttempts(0);
        entity.setNextReconcileAt(LocalDateTime.now());
        entity.setReconcileLeaseUntil(null);
        entity.setReconcileLeaseOwner(null);
        entity.setConfigVersion(entity.getConfigVersion() == null ? 1L : entity.getConfigVersion() + 1L);
        if (creating) {
            // Reserve the globally unique runtime identity in the ownership database before invoking
            // an external runtime. A crash can leave a reconcilable PENDING row, never an unowned
            // account.
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setRuntimeAccountId(entity.getId());
            mapper.insert(entity);
        } else {
            // Persist the desired state before touching the external runtime. If this process exits
            // during saveAccount, the reconciler can replay the exact encrypted configuration.
            updateOwned(entity);
        }
        try {
            ChannelAccount account = runtimes.require(entity.getProvider(), entity.getRuntimeNodeId())
                    .saveAccount(new SaveChannelAccountRequest(
                            entity.getChannelId(),
                            entity.getRuntimeAccountId(),
                            entity.getName(),
                            "ACTIVE".equals(entity.getDesiredStatus()),
                            runtimeConfig(entity)));
            requireStableRuntimeAccount(entity, account);
            entity.setRuntimeStatus(account.connected() ? "ONLINE" : account.running() ? "RUNNING" : "STARTING");
            entity.setRuntimeMetadataJson(metadataJson(account));
            entity.setLastError(account.lastError());
            entity.setNextReconcileAt(null);
        } catch (RuntimeException ex) {
            entity.setRuntimeStatus("ERROR");
            entity.setLastError(safeMessage(ex));
            entity.setReconcileAttempts(1);
            entity.setNextReconcileAt(LocalDateTime.now().plusSeconds(2));
        }
        updateOwned(entity);
        return view(entity);
    }

    private void requireInstanceCapacity(String tenantId, String provider, String channelId) {
        boolean single = runtimes.discover().stream()
                .filter(item -> provider.equals(item.provider()) && channelId.equals(item.channelId()))
                .map(ChannelDefinition::metadata)
                .map(metadata -> metadata.get("accountModel"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(model -> model.get("instancePolicy"))
                .anyMatch(value -> "SINGLE".equalsIgnoreCase(String.valueOf(value)));
        if (!single) return;
        Long count = mapper.selectCount(new LambdaQueryWrapper<ChannelConnectionEntity>()
                .eq(ChannelConnectionEntity::getTenantId, tenantId)
                .eq(ChannelConnectionEntity::getProvider, provider)
                .eq(ChannelConnectionEntity::getChannelId, channelId));
        if (count != null && count > 0)
            throw new ChannelException(
                    "channel_instance_limit", "Only one " + channelId + " account is allowed per tenant");
    }

    /** Cross-tenant scan is restricted to the trusted embedded reconciler. */
    public List<ReconcileCandidate> reconciliationCandidates(LocalDateTime now, int limit) {
        return TenantSqlScope.bypass(() -> mapper
                .selectList(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .in(ChannelConnectionEntity::getRuntimeStatus, "PENDING", "ERROR")
                        .and(q -> q.isNull(ChannelConnectionEntity::getNextReconcileAt)
                                .or()
                                .le(ChannelConnectionEntity::getNextReconcileAt, now))
                        .and(q -> q.isNull(ChannelConnectionEntity::getReconcileLeaseUntil)
                                .or()
                                .lt(ChannelConnectionEntity::getReconcileLeaseUntil, now))
                        .orderByAsc(ChannelConnectionEntity::getNextReconcileAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))))
                .stream()
                .map(row -> new ReconcileCandidate(row.getId(), row.getTenantId()))
                .toList());
    }

    public boolean reconcile(ReconcileCandidate candidate, String workerId, LocalDateTime now, Duration leaseDuration) {
        Duration lease = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofSeconds(30)
                : leaseDuration;
        int claimed = TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConnectionEntity>()
                        .set(ChannelConnectionEntity::getReconcileLeaseOwner, workerId)
                        .set(ChannelConnectionEntity::getReconcileLeaseUntil, now.plus(lease))
                        .eq(ChannelConnectionEntity::getTenantId, candidate.tenantId())
                        .eq(ChannelConnectionEntity::getId, candidate.id())
                        .in(ChannelConnectionEntity::getRuntimeStatus, "PENDING", "ERROR")
                        .and(q -> q.isNull(ChannelConnectionEntity::getNextReconcileAt)
                                .or()
                                .le(ChannelConnectionEntity::getNextReconcileAt, now))
                        .and(q -> q.isNull(ChannelConnectionEntity::getReconcileLeaseUntil)
                                .or()
                                .lt(ChannelConnectionEntity::getReconcileLeaseUntil, now))));
        if (claimed != 1) return false;
        ChannelConnectionEntity entity =
                TenantSqlScope.bypass(() -> mapper.selectOne(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getTenantId, candidate.tenantId())
                        .eq(ChannelConnectionEntity::getId, candidate.id())
                        .last("LIMIT 1")));
        if (entity == null) return false;
        try {
            ChannelAccount account = runtimes.require(entity.getProvider(), entity.getRuntimeNodeId())
                    .saveAccount(new SaveChannelAccountRequest(
                            entity.getChannelId(),
                            entity.getRuntimeAccountId(),
                            entity.getName(),
                            "ACTIVE".equals(entity.getDesiredStatus()),
                            runtimeConfig(entity)));
            requireStableRuntimeAccount(entity, account);
            TenantSqlScope.bypass(() -> mapper.update(
                    null,
                    new LambdaUpdateWrapper<ChannelConnectionEntity>()
                            .set(
                                    ChannelConnectionEntity::getRuntimeStatus,
                                    account.connected() ? "ONLINE" : account.running() ? "RUNNING" : "STARTING")
                            .set(ChannelConnectionEntity::getRuntimeMetadataJson, metadataJson(account))
                            .set(ChannelConnectionEntity::getLastError, account.lastError())
                            .set(ChannelConnectionEntity::getReconcileAttempts, 0)
                            .set(ChannelConnectionEntity::getNextReconcileAt, null)
                            .set(ChannelConnectionEntity::getReconcileLeaseUntil, null)
                            .set(ChannelConnectionEntity::getReconcileLeaseOwner, null)
                            .eq(ChannelConnectionEntity::getTenantId, entity.getTenantId())
                            .eq(ChannelConnectionEntity::getId, entity.getId())
                            .eq(ChannelConnectionEntity::getReconcileLeaseOwner, workerId)));
            return true;
        } catch (RuntimeException failure) {
            int attempts = (entity.getReconcileAttempts() == null ? 0 : entity.getReconcileAttempts()) + 1;
            long delay = Math.min(300, 1L << Math.min(8, attempts));
            TenantSqlScope.bypass(() -> mapper.update(
                    null,
                    new LambdaUpdateWrapper<ChannelConnectionEntity>()
                            .set(ChannelConnectionEntity::getRuntimeStatus, "ERROR")
                            .set(ChannelConnectionEntity::getLastError, safeMessage(failure))
                            .set(ChannelConnectionEntity::getReconcileAttempts, attempts)
                            .set(
                                    ChannelConnectionEntity::getNextReconcileAt,
                                    LocalDateTime.now().plusSeconds(delay))
                            .set(ChannelConnectionEntity::getReconcileLeaseUntil, null)
                            .set(ChannelConnectionEntity::getReconcileLeaseOwner, null)
                            .eq(ChannelConnectionEntity::getTenantId, entity.getTenantId())
                            .eq(ChannelConnectionEntity::getId, entity.getId())
                            .eq(ChannelConnectionEntity::getReconcileLeaseOwner, workerId)));
            return false;
        }
    }

    /**
     * Selects stale active connections without issuing runtime calls while a tenant query is open.
     */
    public List<HealthCandidate> healthCandidates(LocalDateTime staleBefore, int limit) {
        return TenantSqlScope.bypass(() -> mapper
                .selectList(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getDesiredStatus, "ACTIVE")
                        .notIn(ChannelConnectionEntity::getRuntimeStatus, "PENDING", "ERROR")
                        .and(q -> q.isNull(ChannelConnectionEntity::getLastTestedAt)
                                .or()
                                .le(ChannelConnectionEntity::getLastTestedAt, staleBefore))
                        .and(q -> q.isNull(ChannelConnectionEntity::getReconcileLeaseUntil)
                                .or()
                                .lt(ChannelConnectionEntity::getReconcileLeaseUntil, LocalDateTime.now()))
                        .orderByAsc(ChannelConnectionEntity::getLastTestedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))))
                .stream()
                .map(row -> new HealthCandidate(row.getId(), row.getTenantId()))
                .toList());
    }

    /**
     * Refreshes one runtime snapshot under a shared database lease, safe across embedded host nodes.
     */
    public boolean refreshHealth(
            HealthCandidate candidate, String workerId, LocalDateTime now, Duration leaseDuration) {
        Duration lease = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofSeconds(30)
                : leaseDuration;
        int claimed = TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConnectionEntity>()
                        .set(ChannelConnectionEntity::getReconcileLeaseOwner, workerId)
                        .set(ChannelConnectionEntity::getReconcileLeaseUntil, now.plus(lease))
                        .eq(ChannelConnectionEntity::getTenantId, candidate.tenantId())
                        .eq(ChannelConnectionEntity::getId, candidate.id())
                        .eq(ChannelConnectionEntity::getDesiredStatus, "ACTIVE")
                        .notIn(ChannelConnectionEntity::getRuntimeStatus, "PENDING", "ERROR")
                        .and(q -> q.isNull(ChannelConnectionEntity::getReconcileLeaseUntil)
                                .or()
                                .lt(ChannelConnectionEntity::getReconcileLeaseUntil, now))));
        if (claimed != 1) return false;
        ChannelConnectionEntity entity =
                TenantSqlScope.bypass(() -> mapper.selectOne(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getTenantId, candidate.tenantId())
                        .eq(ChannelConnectionEntity::getId, candidate.id())
                        .last("LIMIT 1")));
        if (entity == null) return false;
        String status;
        String error;
        String metadata = entity.getRuntimeMetadataJson();
        try {
            ChannelAccount account = runtimes.require(entity.getProvider(), entity.getRuntimeNodeId())
                    .testAccount(entity.getChannelId(), entity.getRuntimeAccountId());
            status = account.connected() ? "ONLINE" : account.running() ? "RUNNING" : "OFFLINE";
            error = account.lastError();
            metadata = metadataJson(account);
        } catch (RuntimeException failure) {
            status = "DEGRADED";
            error = safeMessage(failure);
        }
        String finalMetadata = metadata;
        String finalStatus = status;
        String finalError = error;
        int updated = TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConnectionEntity>()
                        .set(ChannelConnectionEntity::getRuntimeStatus, finalStatus)
                        .set(ChannelConnectionEntity::getRuntimeMetadataJson, finalMetadata)
                        .set(ChannelConnectionEntity::getLastError, finalError)
                        .set(ChannelConnectionEntity::getLastTestedAt, now)
                        .set(ChannelConnectionEntity::getReconcileLeaseUntil, null)
                        .set(ChannelConnectionEntity::getReconcileLeaseOwner, null)
                        .eq(ChannelConnectionEntity::getTenantId, entity.getTenantId())
                        .eq(ChannelConnectionEntity::getId, entity.getId())
                        .eq(ChannelConnectionEntity::getReconcileLeaseOwner, workerId)));
        return updated == 1 && !"DEGRADED".equals(status);
    }

    public List<View> list(String tenantId, String ownerId) {
        LambdaQueryWrapper<ChannelConnectionEntity> query = new LambdaQueryWrapper<ChannelConnectionEntity>()
                .eq(ChannelConnectionEntity::getTenantId, tenant(tenantId));
        if (ownerId != null && !ownerId.isBlank()) query.eq(ChannelConnectionEntity::getOwnerId, ownerId.trim());
        return mapper.selectList(query).stream()
                .map(ChannelConnectionService::view)
                .toList();
    }

    public View get(String id, String tenantId) {
        return view(requireOwned(id, tenant(tenantId)));
    }

    public EditConfiguration editConfiguration(String id, String tenantId) {
        ChannelConnectionEntity entity = requireOwned(id, tenant(tenantId));
        ChannelDefinition definition =
                runtimes.require(entity.getProvider(), entity.getRuntimeNodeId()).discoverChannels().stream()
                        .filter(item -> entity.getChannelId().equals(item.channelId()))
                        .findFirst()
                        .orElseThrow(() ->
                                new ChannelException("channel_definition_not_found", "Channel definition not found"));
        Map<String, Object> credentials = codec.decode(entity.getTenantId(), entity.getEncryptedCredentials());
        Map<String, Object> config = codec.decode(entity.getTenantId(), entity.getEncryptedConfig());
        List<String> secrets = new java.util.ArrayList<>();
        redactWriteOnly(credentials, definition.credentialSchema(), secrets);
        redactWriteOnly(config, definition.configurationSchema(), secrets);
        return new EditConfiguration(Map.copyOf(credentials), Map.copyOf(config), List.copyOf(secrets));
    }

    public View test(String id, String tenantId) {
        ChannelConnectionEntity entity = requireOwned(id, tenant(tenantId));
        try {
            ChannelAccount account = runtimes.require(entity.getProvider(), entity.getRuntimeNodeId())
                    .testAccount(entity.getChannelId(), entity.getRuntimeAccountId());
            entity.setRuntimeStatus(account.connected() ? "ONLINE" : account.running() ? "RUNNING" : "OFFLINE");
            entity.setRuntimeMetadataJson(metadataJson(account));
            entity.setLastError(account.lastError());
        } catch (RuntimeException ex) {
            entity.setRuntimeStatus("ERROR");
            entity.setLastError(safeMessage(ex));
        }
        entity.setLastTestedAt(LocalDateTime.now());
        updateOwned(entity);
        return view(entity);
    }

    public Route route(String provider, String channelId, String runtimeAccountId) {
        ChannelConnectionEntity entity = findRuntimeConnection(provider, channelId, runtimeAccountId);
        if (entity == null
                || !"ACTIVE".equals(entity.getDesiredStatus())
                || entity.getAgentId() == null
                || entity.getAgentId().isBlank()) return null;
        return new Route(entity.getId(), entity.getTenantId(), entity.getOwnerId(), entity.getAgentId());
    }

    public Ownership ownership(String provider, String channelId, String runtimeAccountId) {
        return ownership(provider, null, channelId, runtimeAccountId);
    }

    public Ownership ownership(String provider, String runtimeNodeId, String channelId, String runtimeAccountId) {
        ChannelConnectionEntity entity = findRuntimeConnection(provider, runtimeNodeId, channelId, runtimeAccountId);
        return entity == null
                ? null
                : new Ownership(entity.getId(), entity.getTenantId(), entity.getOwnerId(), entity.getRuntimeNodeId());
    }

    public RoutingConnection routingConnection(String provider, String channelId, String runtimeAccountId) {
        return routingConnection(provider, null, channelId, runtimeAccountId);
    }

    public RoutingConnection routingConnection(
            String provider, String runtimeNodeId, String channelId, String runtimeAccountId) {
        ChannelConnectionEntity entity = findRuntimeConnection(provider, runtimeNodeId, channelId, runtimeAccountId);
        return entity == null
                ? null
                : new RoutingConnection(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getOwnerId(),
                        trimToNull(entity.getAgentId()),
                        trimToNull(entity.getAgentVersionId()),
                        "ACTIVE".equals(entity.getDesiredStatus()));
    }

    private ChannelConnectionEntity findRuntimeConnection(String provider, String channelId, String runtimeAccountId) {
        return findRuntimeConnection(provider, null, channelId, runtimeAccountId);
    }

    private ChannelConnectionEntity findRuntimeConnection(
            String provider, String runtimeNodeId, String channelId, String runtimeAccountId) {
        List<ChannelConnectionEntity> matches =
                TenantSqlScope.bypass(() -> mapper.selectList(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getProvider, provider)
                        .eq(ChannelConnectionEntity::getChannelId, channelId)
                        .eq(ChannelConnectionEntity::getRuntimeAccountId, runtimeAccountId)
                        .eq(
                                runtimeNodeId != null && !runtimeNodeId.isBlank(),
                                ChannelConnectionEntity::getRuntimeNodeId,
                                runtimeNodeId)
                        .last("LIMIT 2")));
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    public void delete(String id, String tenantId) {
        ChannelConnectionEntity entity = requireOwned(id, tenant(tenantId));
        runtimes.require(entity.getProvider(), entity.getRuntimeNodeId())
                .deleteAccount(entity.getChannelId(), entity.getRuntimeAccountId());
        mapper.delete(new LambdaQueryWrapper<ChannelConnectionEntity>()
                .eq(ChannelConnectionEntity::getTenantId, entity.getTenantId())
                .eq(ChannelConnectionEntity::getId, entity.getId()));
    }

    private Map<String, Object> runtimeConfig(ChannelConnectionEntity entity) {
        Map<String, Object> values =
                new LinkedHashMap<>(codec.decode(entity.getTenantId(), entity.getEncryptedConfig()));
        values.putAll(codec.decode(entity.getTenantId(), entity.getEncryptedCredentials()));
        return values;
    }

    private static Map<String, Object> mergeValues(Map<String, Object> existing, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        patch.forEach((key, value) -> {
            if (value == null) merged.remove(key);
            else if (!(value instanceof String text) || !text.isBlank()) merged.put(key, value);
        });
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void redactWriteOnly(Map<String, Object> values, String schemaJson, List<String> configuredSecrets) {
        Map<String, Object> schema = JsonUtils.parseMap(schemaJson);
        Object rawProperties = schema.get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties)) return;
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof Map<?, ?> field)) continue;
            if (Boolean.TRUE.equals(field.get("writeOnly")) && values.remove(name) != null) configuredSecrets.add(name);
        }
    }

    private ChannelConnectionEntity requireOwned(String id, String tenantId) {
        ChannelConnectionEntity entity = mapper.selectOne(new LambdaQueryWrapper<ChannelConnectionEntity>()
                .eq(ChannelConnectionEntity::getTenantId, tenantId)
                .eq(ChannelConnectionEntity::getId, id)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ChannelException("channel_connection_not_found", "Channel connection not found");
        }
        return entity;
    }

    private void updateOwned(ChannelConnectionEntity entity) {
        mapper.update(
                entity,
                new LambdaUpdateWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getTenantId, entity.getTenantId())
                        .eq(ChannelConnectionEntity::getId, entity.getId()));
    }

    private static View view(ChannelConnectionEntity entity) {
        return new View(
                entity.getId(),
                entity.getTenantId(),
                entity.getOwnerType(),
                entity.getOwnerId(),
                entity.getProvider(),
                entity.getChannelId(),
                entity.getName(),
                entity.getDesiredStatus(),
                entity.getRuntimeStatus(),
                entity.getRuntimeAccountId(),
                entity.getAgentId(),
                entity.getAgentVersionId(),
                entity.getRuntimeNodeId(),
                JsonUtils.parseMap(entity.getRuntimeMetadataJson()),
                entity.getEncryptedCredentials() != null,
                entity.getLastError(),
                entity.getLastTestedAt(),
                entity.getConfigVersion() == null ? 0 : entity.getConfigVersion());
    }

    private static String metadataJson(ChannelAccount account) {
        return account.metadata() == null || account.metadata().isEmpty() ? null : JsonUtils.toJson(account.metadata());
    }

    private static String tenant(String value) {
        return defaulted(value, "default");
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String selectNode(String provider, String requested) {
        if (requested != null && !requested.isBlank())
            return runtimes.require(provider, requested.trim()).nodeId();
        List<String> nodes = runtimes.nodeIds(provider);
        if (nodes.isEmpty())
            throw new ChannelException("channel_runtime_not_found", "Channel runtime provider not found: " + provider);
        return nodes.getFirst();
    }

    private static String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static void requireStableRuntimeAccount(ChannelConnectionEntity entity, ChannelAccount account) {
        if (account.accountId() != null
                && !account.accountId().isBlank()
                && !entity.getRuntimeAccountId().equals(account.accountId())) {
            throw new ChannelException(
                    "channel_runtime_account_remapped",
                    "Runtime changed the reserved account id; reconciliation was stopped");
        }
    }
}
