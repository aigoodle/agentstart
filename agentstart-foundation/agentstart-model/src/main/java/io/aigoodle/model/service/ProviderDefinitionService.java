package io.aigoodle.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.mapper.PredefinedModelMapper;
import io.aigoodle.model.mapper.ProviderDefinitionMapper;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.PredefinedModel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dify-parity DB-backed provider metadata layer. Replaces the previous
 * "hard-coded Java bean is the source of truth" flow with:
 * <ul>
 *   <li>{@code agent_model_provider}   — provider definitions (label / icon /
 *       credential schema / parameter rules / etc). Java built-ins auto-seed
 *       {@code source='builtin'} rows on startup; external modules or admin UI
 *       can add {@code source='external'|'custom'} rows without touching Java
 *       code.</li>
 *   <li>{@code agent_predefined_model} — provider's shipped model catalog. Also
 *       seeded from Java on first boot; editable afterwards so adding a new
 *       model to an existing provider does not require redeploy.</li>
 * </ul>
 * The Java {@link io.aigoodle.model.provider.ModelProvider} bean
 * referenced by {@link ProviderDefinitionEntity#getImplementationKey()} still
 * supplies the wire code (build ChatModel / EmbeddingModel / list-remote) —
 * this service owns metadata only.
 */
public class ProviderDefinitionService {

    /** Sentinel tenant id for "global" definitions (visible to every tenant). */
    public static final String SYSTEM_TENANT = "system";

    private final ProviderDefinitionMapper providerMapper;
    private final PredefinedModelMapper predefinedMapper;

    public ProviderDefinitionService(ProviderDefinitionMapper providerMapper, PredefinedModelMapper predefinedMapper) {
        this.providerMapper = providerMapper;
        this.predefinedMapper = predefinedMapper;
    }

    // ---------------------------------------------------------- definitions

    /**
     * Return every provider definition visible to {@code tenantId}: global
     * ({@code tenant_id='system'}) rows AND any {@code tenant_id=tenantId} rows.
     * Sorted by {@code sortOrder} then name.
     */
    public List<ProviderDefinitionEntity> list(String tenantId) {
        LambdaQueryWrapper<ProviderDefinitionEntity> query = new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getEnabled, Boolean.TRUE)
                .in(ProviderDefinitionEntity::getTenantId, effectiveTenants(tenantId))
                .orderByAsc(ProviderDefinitionEntity::getSortOrder)
                .orderByAsc(ProviderDefinitionEntity::getName);
        return providerMapper.selectList(query);
    }

    /** Find a definition by provider {@code name}, respecting tenant scoping. */
    public ProviderDefinitionEntity findByName(String tenantId, String name) {
        if (name == null) {
            return null;
        }
        return providerMapper.selectOne(new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getName, name)
                .in(ProviderDefinitionEntity::getTenantId, effectiveTenants(tenantId))
                .last("limit 1"));
    }

    public ProviderDefinitionEntity requireByName(String tenantId, String name) {
        ProviderDefinitionEntity entity = findByName(tenantId, name);
        if (entity == null) {
            throw new PlatformException(
                    "provider_not_found", "No provider definition '" + name + "' for tenant '" + tenantId + "'", null);
        }
        return entity;
    }

    /** Exact-tenant catalog snapshot used by the startup seeder. */
    List<ProviderDefinitionEntity> listOwnedDefinitions(String tenantId) {
        return providerMapper.selectList(new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getTenantId, normalizedTenant(tenantId)));
    }

    /** Add runtime capability to built-in metadata without rewriting user-owned provider configuration. */
    @Transactional
    public void addBuiltinModelType(String providerName, ModelType type) {
        var existing = listOwnedDefinitions(SYSTEM_TENANT).stream()
                .filter(row -> providerName.equals(row.getName()) && "builtin".equals(row.getSource()))
                .findFirst()
                .orElse(null);
        if (existing == null) return;
        var types = new java.util.HashSet<>(deserializeModelTypes(existing.getSupportedModelTypes()));
        if (types.add(type))
            providerMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProviderDefinitionEntity>()
                            .eq(ProviderDefinitionEntity::getId, existing.getId())
                            .eq(ProviderDefinitionEntity::getTenantId, SYSTEM_TENANT)
                            .set(ProviderDefinitionEntity::getSupportedModelTypes, JsonUtils.toJson(types)));
    }

    /**
     * Upsert a definition by (tenant_id, name). Existing rows with the same
     * key have their metadata refreshed <em>except</em> {@code enabled} and
     * {@code sortOrder} — these are user-owned and must not be reset by a
     * seed pass.
     */
    @Transactional
    public ProviderDefinitionEntity upsert(ProviderDefinitionEntity next) {
        if (next.getTenantId() == null) next.setTenantId(SYSTEM_TENANT);
        if (next.getSource() == null) next.setSource("custom");
        if (next.getEnabled() == null) next.setEnabled(Boolean.TRUE);
        if (next.getSortOrder() == null) next.setSortOrder(100);
        if (next.getSupportsRemoteModelListing() == null) {
            next.setSupportsRemoteModelListing(Boolean.FALSE);
        }
        ProviderDefinitionEntity existing = providerMapper.selectOne(new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getTenantId, next.getTenantId())
                .eq(ProviderDefinitionEntity::getName, next.getName())
                .last("limit 1"));
        if (existing == null) {
            providerMapper.insert(next);
            return next;
        }
        // Refresh mutable metadata; keep user-owned enabled / sort_order.
        existing.setLabel(next.getLabel());
        existing.setDescription(next.getDescription());
        existing.setIcon(next.getIcon());
        existing.setSvgIcon(next.getSvgIcon());
        existing.setSupportedModelTypes(next.getSupportedModelTypes());
        existing.setCredentialSchema(next.getCredentialSchema());
        existing.setDefaultParameterRules(next.getDefaultParameterRules());
        existing.setImplementationKey(next.getImplementationKey());
        existing.setDefaultBaseUrl(next.getDefaultBaseUrl());
        existing.setSource(next.getSource());
        existing.setSupportsRemoteModelListing(next.getSupportsRemoteModelListing());
        providerMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void updatePartial(String id, Map<String, Object> patch) {
        ProviderDefinitionEntity entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new PlatformException("provider_not_found", "No provider definition with id " + id, null);
        }
        ProviderDefinitionPatch.apply(entity, patch);
        providerMapper.updateById(entity);
    }

    @Transactional
    public void updatePartial(String tenantId, String id, Map<String, Object> patch) {
        ProviderDefinitionEntity entity = requireOwnedDefinition(tenantId, id);
        ProviderDefinitionPatch.apply(entity, patch);
        providerMapper.update(
                entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProviderDefinitionEntity>()
                        .eq(ProviderDefinitionEntity::getTenantId, entity.getTenantId())
                        .eq(ProviderDefinitionEntity::getId, entity.getId()));
    }

    @Transactional
    public void delete(String id) {
        ProviderDefinitionEntity entity = providerMapper.selectById(id);
        if (entity == null) return;
        // Refuse to delete a builtin — deleting metadata would leave the Java bean
        // dangling. Callers should disable it via `enabled=false` instead.
        if ("builtin".equalsIgnoreCase(entity.getSource())) {
            throw new PlatformException(
                    "provider_immutable",
                    "Built-in providers cannot be deleted — set enabled=false to hide them",
                    null);
        }
        providerMapper.deleteById(id);
        // Cascade the shipped catalog rows too — they're only meaningful for a
        // present provider.
        predefinedMapper.delete(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getProviderName, entity.getName()));
    }

    @Transactional
    public void delete(String tenantId, String id) {
        ProviderDefinitionEntity entity = requireOwnedDefinition(tenantId, id);
        if ("builtin".equalsIgnoreCase(entity.getSource())) {
            throw new PlatformException("provider_immutable", "Built-in providers cannot be deleted", null);
        }
        providerMapper.delete(new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getTenantId, entity.getTenantId())
                .eq(ProviderDefinitionEntity::getId, entity.getId()));
        predefinedMapper.delete(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, entity.getTenantId())
                .eq(PredefinedModelEntity::getProviderName, entity.getName()));
    }

    // ---------------------------------------------------------- predefined models

    public List<PredefinedModelEntity> listPredefined(String providerName) {
        return predefinedMapper.selectList(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getProviderName, providerName)
                .orderByAsc(PredefinedModelEntity::getSortOrder)
                .orderByAsc(PredefinedModelEntity::getModel));
    }

    public List<PredefinedModelEntity> listPredefined(String tenantId, String providerName) {
        return predefinedMapper.selectList(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getProviderName, providerName)
                .in(PredefinedModelEntity::getTenantId, effectiveTenants(tenantId))
                .orderByAsc(PredefinedModelEntity::getSortOrder)
                .orderByAsc(PredefinedModelEntity::getModel));
    }

    public PredefinedModelEntity findPredefined(String providerName, String model, ModelType type) {
        return predefinedMapper.selectOne(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getProviderName, providerName)
                .eq(PredefinedModelEntity::getModel, model)
                .eq(PredefinedModelEntity::getModelType, type)
                .last("limit 1"));
    }

    public PredefinedModelEntity findPredefined(String tenantId, String providerName, String model, ModelType type) {
        return predefinedMapper.selectOne(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getProviderName, providerName)
                .eq(PredefinedModelEntity::getModel, model)
                .eq(PredefinedModelEntity::getModelType, type)
                .in(PredefinedModelEntity::getTenantId, effectiveTenants(tenantId))
                .orderByDesc(PredefinedModelEntity::getTenantId)
                .last("LIMIT 1"));
    }

    /** Exact-tenant predefined-model snapshot used by the startup seeder. */
    List<PredefinedModelEntity> listOwnedPredefined(String tenantId) {
        return predefinedMapper.selectList(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, normalizedTenant(tenantId)));
    }

    /**
     * Upsert a predefined model entry. Existing rows keyed by (provider, model,
     * model_type) are refreshed. Called by the seeder AND the admin API — same
     * idempotent path.
     * <p>
     * {@code tenant_id} defaults to {@link #SYSTEM_TENANT} — the shared global
     * catalog. Pass a specific tenant when adding a per-tenant custom entry.
     */
    @Transactional
    public PredefinedModelEntity upsertPredefined(PredefinedModelEntity next) {
        if (next.getTenantId() == null || next.getTenantId().isBlank()) {
            next.setTenantId(SYSTEM_TENANT);
        }
        PredefinedModelEntity existing = predefinedMapper.selectOne(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, next.getTenantId())
                .eq(PredefinedModelEntity::getProviderName, next.getProviderName())
                .eq(PredefinedModelEntity::getModel, next.getModel())
                .eq(PredefinedModelEntity::getModelType, next.getModelType())
                .last("LIMIT 1"));
        if (existing == null) {
            if (next.getSortOrder() == null) next.setSortOrder(100);
            predefinedMapper.insert(next);
            return next;
        }
        existing.setLabel(next.getLabel());
        existing.setFeatures(next.getFeatures());
        existing.setContextLength(next.getContextLength());
        existing.setDimensions(next.getDimensions());
        existing.setParameterRules(next.getParameterRules());
        if (next.getSortOrder() != null) existing.setSortOrder(next.getSortOrder());
        predefinedMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void deletePredefined(String id) {
        predefinedMapper.deleteById(id);
    }

    @Transactional
    public void deletePredefined(String tenantId, String id) {
        PredefinedModelEntity entity = predefinedMapper.selectOne(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, normalizedTenant(tenantId))
                .eq(PredefinedModelEntity::getId, id)
                .last("LIMIT 1"));
        if (entity == null) throw new PlatformException("predefined_model_not_found", "Model not found", null);
        predefinedMapper.delete(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, entity.getTenantId())
                .eq(PredefinedModelEntity::getId, entity.getId()));
    }

    private ProviderDefinitionEntity requireOwnedDefinition(String tenantId, String id) {
        ProviderDefinitionEntity entity = providerMapper.selectOne(new LambdaQueryWrapper<ProviderDefinitionEntity>()
                .eq(ProviderDefinitionEntity::getTenantId, normalizedTenant(tenantId))
                .eq(ProviderDefinitionEntity::getId, id)
                .last("LIMIT 1"));
        if (entity == null) throw new PlatformException("provider_not_found", "Provider not found", null);
        return entity;
    }

    private static String normalizedTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    /**
     * Delete every tenant-scoped predefined row for {@code providerName} — those
     * were materialized on the fly by the "启用一个远程模型" flow (see
     * {@code ModelController#setModelEnabled}). Global rows (tenant_id={@link
     * #SYSTEM_TENANT}) are untouched — they are the provider's shipped catalog
     * and belong to the metadata layer, not the credential.
     * <p>
     * Called from the credential-delete cascade so re-adding an API key doesn't
     * resurrect a stale materialized catalog the user thought was removed.
     */
    @Transactional
    public int deleteTenantPredefined(String tenantId, String providerName) {
        String tenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        if (SYSTEM_TENANT.equals(tenant)) return 0;
        return predefinedMapper.delete(new LambdaQueryWrapper<PredefinedModelEntity>()
                .eq(PredefinedModelEntity::getTenantId, tenant)
                .eq(PredefinedModelEntity::getProviderName, providerName));
    }

    // ---------------------------------------------------------- JSON codecs

    public Set<ModelType> deserializeModelTypes(String json) {
        return ProviderMetadataCodec.modelTypes(json);
    }

    public List<CredentialField> deserializeCredentialSchema(String json) {
        return ProviderMetadataCodec.credentialSchema(json);
    }

    public Map<ModelType, List<ModelParameterRule>> deserializeParameterRules(String json) {
        return ProviderMetadataCodec.parameterRules(json);
    }

    public List<ModelParameterRule> deserializeRuleList(String json) {
        return ProviderMetadataCodec.ruleList(json);
    }

    public Set<ModelFeature> deserializeFeatures(String json) {
        return ProviderMetadataCodec.features(json);
    }

    // ---------------------------------------------------------- helpers

    /**
     * Build a {@link PredefinedModelEntity} from an in-memory {@link PredefinedModel}
     * as shipped by a Java built-in provider. Used by the seeder.
     */
    public PredefinedModelEntity fromMemory(String providerName, PredefinedModel model, int sortOrder) {
        return ProviderMetadataCodec.toEntity(providerName, model, sortOrder, SYSTEM_TENANT);
    }

    // ---------------------------------------------------------- private

    private static Collection<String> effectiveTenants(String tenantId) {
        String tenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        Map<String, Boolean> tenants = new LinkedHashMap<>();
        tenants.put(SYSTEM_TENANT, true);
        tenants.put(tenant, true);
        return tenants.keySet();
    }
}
