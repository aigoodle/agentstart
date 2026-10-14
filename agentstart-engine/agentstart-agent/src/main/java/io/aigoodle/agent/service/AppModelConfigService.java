package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.mapper.AppModelConfigMapper;
import io.aigoodle.common.context.UserContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the one-to-one model-configuration sidecar of every application.
 *
 * <p>The sidecar primary key is the application id. Keeping inserts, patch updates,
 * and deletes behind this service makes that invariant visible in one place.</p>
 */
public class AppModelConfigService {

    private static final String DEFAULT_TENANT_ID = "default";

    private final AppModelConfigMapper configMapper;

    public AppModelConfigService(AppModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /** Load a sidecar by application id, or return {@code null} when none exists. */
    public AppModelConfigEntity findByAppId(String appId) {
        return findByAppId(UserContextHolder.currentTenantId(), appId);
    }

    /** Load only the sidecar owned by the supplied tenant and application. */
    public AppModelConfigEntity findByAppId(String tenantId, String appId) {
        if (appId == null || appId.isBlank()) {
            return null;
        }
        return configMapper.selectOne(new LambdaQueryWrapper<AppModelConfigEntity>()
                .eq(AppModelConfigEntity::getTenantId, effectiveTenant(tenantId))
                .eq(AppModelConfigEntity::getAppId, appId)
                .last("LIMIT 1"));
    }

    /** Insert a new sidecar or apply the supplied non-null fields to the existing one. */
    @Transactional
    public AppModelConfigEntity upsert(AppModelConfigRegistration registration) {
        AppModelConfigEntity configuration = registration.configuration();
        if (configuration == null) {
            return null;
        }

        prepareIdentity(configuration, registration);
        String appId = registration.appId();
        String tenantId = configuration.getTenantId();
        AppModelConfigEntity existingConfiguration = findByAppId(tenantId, appId);
        if (existingConfiguration == null) {
            configMapper.insert(configuration);
            return configuration;
        }

        AppModelConfigPatch.apply(existingConfiguration, configuration);
        configMapper.update(
                existingConfiguration,
                new LambdaUpdateWrapper<AppModelConfigEntity>()
                        .eq(AppModelConfigEntity::getTenantId, tenantId)
                        .eq(AppModelConfigEntity::getAppId, appId));
        return existingConfiguration;
    }

    /** @deprecated Use {@link #upsert(AppModelConfigRegistration)}. */
    @Deprecated(forRemoval = false)
    public AppModelConfigEntity upsert(String appId, String tenantId, AppModelConfigEntity configuration) {
        return upsert(new AppModelConfigRegistration(appId, tenantId, configuration));
    }

    /** Delete the sidecar owned by an application. */
    @Transactional
    public void deleteByAppId(String appId) {
        deleteByAppId(UserContextHolder.currentTenantId(), appId);
    }

    /** Delete only the sidecar owned by the supplied tenant and application. */
    @Transactional
    public void deleteByAppId(String tenantId, String appId) {
        if (appId == null || appId.isBlank()) {
            return;
        }
        configMapper.delete(new LambdaQueryWrapper<AppModelConfigEntity>()
                .eq(AppModelConfigEntity::getTenantId, effectiveTenant(tenantId))
                .eq(AppModelConfigEntity::getAppId, appId));
    }

    /** Translate the flat agent-editor request into its persistence sidecar. */
    public static AppModelConfigEntity fromRequest(SaveAppRequest request) {
        return AppModelConfigFactory.from(request);
    }

    private static void prepareIdentity(AppModelConfigEntity configuration, AppModelConfigRegistration registration) {
        configuration.setAppId(registration.appId());
        configuration.setId(registration.appId());
        configuration.setTenantId(effectiveTenant(registration.tenantId()));
    }

    private static String effectiveTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId;
    }
}
