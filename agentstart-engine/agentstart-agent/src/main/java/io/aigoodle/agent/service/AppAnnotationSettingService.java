package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppAnnotationSettingEntity;
import io.aigoodle.agent.mapper.AppAnnotationSettingMapper;
import io.aigoodle.common.context.UserContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Manages the single annotation-retrieval configuration owned by an application. */
public class AppAnnotationSettingService {

    private static final float DEFAULT_SCORE_THRESHOLD = 0.8f;

    private final AppAnnotationSettingMapper settingMapper;

    public AppAnnotationSettingService(AppAnnotationSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public AppAnnotationSettingEntity getByApp(String appId) {
        return getByApp(UserContextHolder.currentTenantId(), appId);
    }

    public AppAnnotationSettingEntity getByApp(String tenantId, String appId) {
        AppAnnotationSettingEntity setting = findByApp(tenantId, appId);
        return setting != null ? setting : defaultSetting(normalizedTenant(tenantId), appId);
    }

    @Transactional
    public AppAnnotationSettingEntity save(String appId, AppAnnotationSettingEntity updates) {
        return save(UserContextHolder.currentTenantId(), appId, updates);
    }

    @Transactional
    public AppAnnotationSettingEntity save(String tenantId, String appId, AppAnnotationSettingEntity updates) {
        String tenant = normalizedTenant(tenantId);
        AppAnnotationSettingEntity setting = findByApp(tenant, appId);
        if (setting == null) {
            setting = defaultSetting(tenant, appId);
            applyUpdates(setting, updates);
            settingMapper.insert(setting);
            return setting;
        }
        applyUpdates(setting, updates);
        updateOwned(tenant, appId, setting);
        return setting;
    }

    @Transactional
    public AppAnnotationSettingEntity setEnabled(String appId, boolean enabled) {
        return setEnabled(UserContextHolder.currentTenantId(), appId, enabled);
    }

    @Transactional
    public AppAnnotationSettingEntity setEnabled(String tenantId, String appId, boolean enabled) {
        String tenant = normalizedTenant(tenantId);
        AppAnnotationSettingEntity setting = findByApp(tenant, appId);
        if (setting == null) {
            setting = defaultSetting(tenant, appId);
            setting.setEnabled(enabled);
            settingMapper.insert(setting);
            return setting;
        }
        setting.setEnabled(enabled);
        updateOwned(tenant, appId, setting);
        return setting;
    }

    private AppAnnotationSettingEntity findByApp(String appId) {
        return findByApp(UserContextHolder.currentTenantId(), appId);
    }

    private AppAnnotationSettingEntity findByApp(String tenantId, String appId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<AppAnnotationSettingEntity>()
                .eq(AppAnnotationSettingEntity::getTenantId, normalizedTenant(tenantId))
                .eq(AppAnnotationSettingEntity::getAppId, appId)
                .last("LIMIT 1"));
    }

    private static AppAnnotationSettingEntity defaultSetting(String appId) {
        AppAnnotationSettingEntity setting = new AppAnnotationSettingEntity();
        setting.setAppId(appId);
        setting.setEnabled(false);
        setting.setScoreThreshold(DEFAULT_SCORE_THRESHOLD);
        return setting;
    }

    private static AppAnnotationSettingEntity defaultSetting(String tenantId, String appId) {
        AppAnnotationSettingEntity setting = defaultSetting(appId);
        setting.setTenantId(normalizedTenant(tenantId));
        return setting;
    }

    private void updateOwned(String tenantId, String appId, AppAnnotationSettingEntity setting) {
        settingMapper.update(
                setting,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppAnnotationSettingEntity>()
                        .eq(AppAnnotationSettingEntity::getTenantId, normalizedTenant(tenantId))
                        .eq(AppAnnotationSettingEntity::getAppId, appId)
                        .eq(AppAnnotationSettingEntity::getId, setting.getId()));
    }

    private static String normalizedTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private static void applyUpdates(AppAnnotationSettingEntity setting, AppAnnotationSettingEntity updates) {
        if (updates.getScoreThreshold() != null) {
            setting.setScoreThreshold(updates.getScoreThreshold());
        }
        if (updates.getEmbeddingModelId() != null) {
            setting.setEmbeddingModelId(updates.getEmbeddingModelId());
        }
        if (updates.getEnabled() != null) {
            setting.setEnabled(updates.getEnabled());
        }
    }
}
