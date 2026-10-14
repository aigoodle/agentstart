package io.aigoodle.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.entity.ProviderModelSettingEntity;
import io.aigoodle.model.entity.TenantDefaultModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.mapper.ProviderModelSettingMapper;
import io.aigoodle.model.mapper.TenantDefaultModelMapper;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dify-parity per-tenant settings for models:
 * <ul>
 *   <li>{@code agent_provider_model_setting} — opt-in enablement and load-balancing settings.
 *       A missing row means the model is disabled.</li>
 *   <li>{@code agent_tenant_default_model} — one default per model type.</li>
 * </ul>
 * <p>
 * Keyed by (tenant, provider_name, model_name, model_type) rather than by row
 * id — because the same "model" can be represented by a predefined-catalog row
 * OR a custom {@code agent_model} row, and settings apply the same way to
 * either.
 */
public class ProviderModelSettingsService {

    private static final String DEFAULT_TENANT = "default";

    private final ProviderModelSettingMapper settingMapper;
    private final TenantDefaultModelMapper defaultMapper;

    public ProviderModelSettingsService(
            ProviderModelSettingMapper settingMapper, TenantDefaultModelMapper defaultMapper) {
        this.settingMapper = settingMapper;
        this.defaultMapper = defaultMapper;
    }

    // -------------------------------------------------- enable / load-balance

    /**
     * True only when an explicit {@code enabled=true} row exists — <em>opt-in</em>
     * semantics. A freshly-configured provider shows every model as disabled
     * so each tenant explicitly selects the models it intends to use.
     */
    public List<ProviderModelSettingEntity> listForTenant(String tenantId) {
        return settingMapper.selectList(new LambdaQueryWrapper<ProviderModelSettingEntity>()
                .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId)));
    }

    public boolean isEnabled(String tenantId, String providerName, String modelName, ModelType modelType) {
        ProviderModelSettingEntity setting = findSetting(tenantId, providerName, modelName, modelType);
        return setting != null && Boolean.TRUE.equals(setting.getEnabled());
    }

    public ProviderModelSettingEntity findSetting(
            String tenantId, String providerName, String modelName, ModelType modelType) {
        return settingMapper.selectOne(new LambdaQueryWrapper<ProviderModelSettingEntity>()
                .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ProviderModelSettingEntity::getProviderName, providerName)
                .eq(ProviderModelSettingEntity::getModelName, modelName)
                .eq(ProviderModelSettingEntity::getModelType, modelType)
                .last("limit 1"));
    }

    /**
     * Batch-read: setting rows for {@code providerName} within {@code tenantId},
     * keyed by "{modelName}::{modelType}" for cheap in-memory joins.
     */
    public Map<String, ProviderModelSettingEntity> settingIndex(String tenantId, String providerName) {
        List<ProviderModelSettingEntity> settings =
                settingMapper.selectList(new LambdaQueryWrapper<ProviderModelSettingEntity>()
                        .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId))
                        .eq(ProviderModelSettingEntity::getProviderName, providerName));
        Map<String, ProviderModelSettingEntity> settingsByModel = new LinkedHashMap<>();
        for (ProviderModelSettingEntity setting : settings) {
            settingsByModel.put(indexKey(setting.getModelName(), setting.getModelType()), setting);
        }
        return settingsByModel;
    }

    /**
     * Opt-in enable: default is disabled, so we persist an explicit row every
     * time the user diverges. Missing row = disabled. Row with enabled=true =
     * user turned it on. Row with enabled=false = also disabled (equivalent to
     * missing, but we keep it around if load-balancing is configured).
     */
    @Transactional
    public ProviderModelSettingEntity setEnabled(
            String tenantId, String providerName, String modelName, ModelType modelType, boolean enabled) {
        ProviderModelSettingEntity existing = findSetting(tenantId, providerName, modelName, modelType);
        if (existing == null) {
            // No row — persist an explicit setting only when turning ON. Turning OFF
            // when there's no row is a no-op (already disabled by default).
            if (!enabled) {
                return null;
            }
            ProviderModelReference model = new ProviderModelReference(providerName, modelName, modelType);
            ProviderModelSettingEntity setting = model.newEnabledSetting(normalizeTenantId(tenantId));
            settingMapper.insert(setting);
            return setting;
        }
        // Setting exists — flip flag, or drop the row when turning OFF a plain row
        // (load-balancing rows are kept so their config isn't lost).
        if (!enabled && Boolean.FALSE.equals(existing.getLoadBalancingEnabled())) {
            settingMapper.delete(new LambdaQueryWrapper<ProviderModelSettingEntity>()
                    .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId))
                    .eq(ProviderModelSettingEntity::getId, existing.getId()));
            return null;
        }
        existing.setEnabled(enabled);
        settingMapper.update(
                existing,
                new LambdaUpdateWrapper<ProviderModelSettingEntity>()
                        .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId))
                        .eq(ProviderModelSettingEntity::getId, existing.getId()));
        return existing;
    }

    // ---------------------------------------------------------- defaults

    public TenantDefaultModelEntity getDefault(String tenantId, ModelType modelType) {
        return defaultMapper.selectOne(new LambdaQueryWrapper<TenantDefaultModelEntity>()
                .eq(TenantDefaultModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(TenantDefaultModelEntity::getModelType, modelType)
                .last("limit 1"));
    }

    public Map<ModelType, TenantDefaultModelEntity> listDefaults(String tenantId) {
        List<TenantDefaultModelEntity> defaults =
                defaultMapper.selectList(new LambdaQueryWrapper<TenantDefaultModelEntity>()
                        .eq(TenantDefaultModelEntity::getTenantId, normalizeTenantId(tenantId)));
        Map<ModelType, TenantDefaultModelEntity> defaultsByType = new EnumMap<>(ModelType.class);
        for (TenantDefaultModelEntity defaultModel : defaults) {
            defaultsByType.put(defaultModel.getModelType(), defaultModel);
        }
        return defaultsByType;
    }

    /**
     * Set the default model for {@code modelType}, replacing any previous
     * default of the same type for that tenant.
     */
    @Transactional
    public TenantDefaultModelEntity setDefault(
            String tenantId, String providerName, String modelName, ModelType modelType) {
        if (providerName == null || modelName == null || modelType == null) {
            throw new PlatformException(
                    "invalid_default", "provider_name / model_name / model_type must not be null", null);
        }
        String effectiveTenantId = normalizeTenantId(tenantId);
        defaultMapper.delete(new LambdaQueryWrapper<TenantDefaultModelEntity>()
                .eq(TenantDefaultModelEntity::getTenantId, effectiveTenantId)
                .eq(TenantDefaultModelEntity::getModelType, modelType));
        ProviderModelReference model = new ProviderModelReference(providerName, modelName, modelType);
        TenantDefaultModelEntity defaultModel = model.newDefault(effectiveTenantId);
        defaultMapper.insert(defaultModel);
        return defaultModel;
    }

    @Transactional
    public void clearDefault(String tenantId, ModelType modelType) {
        defaultMapper.delete(new LambdaQueryWrapper<TenantDefaultModelEntity>()
                .eq(TenantDefaultModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(TenantDefaultModelEntity::getModelType, modelType));
    }

    /**
     * Nuke every enable-setting row for (tenant, provider). Called from the
     * credential-delete cascade so re-adding the API key starts from a clean,
     * explicitly disabled state.
     */
    @Transactional
    public int clearProviderSettings(String tenantId, String providerName) {
        return settingMapper.delete(new LambdaQueryWrapper<ProviderModelSettingEntity>()
                .eq(ProviderModelSettingEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ProviderModelSettingEntity::getProviderName, providerName));
    }

    /**
     * Drop every tenant-default row that pointed at {@code providerName}. Same
     * motivation as {@link #clearProviderSettings}: on credential removal the
     * "系统默认模型" for this provider is no longer resolvable, so we must not
     * keep pointing at a dangling (provider, model) pair.
     */
    @Transactional
    public int clearProviderDefaults(String tenantId, String providerName) {
        return defaultMapper.delete(new LambdaQueryWrapper<TenantDefaultModelEntity>()
                .eq(TenantDefaultModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(TenantDefaultModelEntity::getProviderName, providerName));
    }

    private static String normalizeTenantId(String tenantId) {
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
        if (UserContextHolder.isAuthenticated()
                && !UserContextHolder.currentTenantId().equals(effectiveTenant)) {
            throw new SecurityException("Requested tenant does not match the authenticated tenant");
        }
        return effectiveTenant;
    }

    private static String indexKey(String modelName, ModelType modelType) {
        return modelName + "::" + (modelType == null ? "" : modelType.name());
    }
}
