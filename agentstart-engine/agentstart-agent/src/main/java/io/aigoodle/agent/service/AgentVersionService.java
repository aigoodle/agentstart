package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.entity.AgentVersionEntity;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.mapper.AgentVersionMapper;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Publication boundary for immutable Agent runtime definitions. */
public class AgentVersionService {
    private AppPermissionService appPermissions;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAppPermissions(AppPermissionService appPermissions) {
        this.appPermissions = appPermissions;
    }

    private final AgentVersionMapper versions;
    private final AppMapper apps;
    private final AgentDefinitionFactory definitions;
    private final AgentRuntimeRegistry runtimes;

    public AgentVersionService(AgentVersionMapper versions, AppMapper apps, AppModelConfigService modelConfigs) {
        this(versions, apps, modelConfigs, null);
    }

    public AgentVersionService(
            AgentVersionMapper versions,
            AppMapper apps,
            AppModelConfigService modelConfigs,
            AgentRuntimeRegistry runtimes) {
        this.versions = versions;
        this.apps = apps;
        this.definitions = new AgentDefinitionFactory(modelConfigs);
        this.runtimes = runtimes;
    }

    @Transactional
    public AgentVersionEntity publish(String tenantId, String appId, String actorId, String summary) {
        AppEntity app = requireOwnedAppForUpdate(tenantId, appId);
        if (appPermissions != null) appPermissions.requireWrite(app);
        AgentDefinition definition = definitions.create(app);
        return createVersion(app, definition, actorId, summary, null);
    }

    @Transactional
    public AgentVersionEntity rollback(
            String tenantId, String appId, String targetVersionId, String actorId, String summary) {
        AppEntity app = requireOwnedAppForUpdate(tenantId, appId);
        if (appPermissions != null) appPermissions.requireWrite(app);
        AgentVersionEntity target = requireOwnedVersion(tenantId, appId, targetVersionId);
        AgentDefinition definition = JsonUtils.parse(target.getDefinitionJson(), AgentDefinition.class);
        return createVersion(
                app,
                definition,
                actorId,
                text(summary) == null ? "Rollback to v" + target.getVersionNumber() : summary.trim(),
                target.getId());
    }

    @Transactional
    public AgentVersionEntity disable(String tenantId, String appId, String versionId) {
        AppEntity ownedApp = requireOwnedApp(tenantId, appId);
        if (appPermissions != null) appPermissions.requireWrite(ownedApp);
        AgentVersionEntity version = requireOwnedVersion(tenantId, appId, versionId);
        boolean wasCurrent = "ACTIVE".equals(version.getStatus());
        version.setStatus("DISABLED");
        versions.update(version, owned(version));
        if (wasCurrent) {
            AppEntity app = requireOwnedApp(tenantId, appId);
            app.setStatus("disabled");
            app.setPublished(false);
            updateOwnedApp(app);
        }
        return version;
    }

    public List<AgentVersionEntity> list(String tenantId, String appId) {
        requireOwnedApp(tenantId, appId);
        return versions.selectList(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(AgentVersionEntity::getAppId, required(appId, "appId"))
                .orderByDesc(AgentVersionEntity::getVersionNumber));
    }

    public AgentVersionEntity requireRunnable(String tenantId, String appId, String versionId) {
        if (appPermissions != null) requireOwnedApp(tenantId, appId);
        boolean explicitlyPinned = versionId != null && !versionId.isBlank();
        AgentVersionEntity version =
                explicitlyPinned ? requireOwnedVersion(tenantId, appId, versionId) : current(tenantId, appId);
        // Publishing a newer definition supersedes the old one for new conversations, but an
        // already pinned conversation must remain reproducible. DISABLED is the explicit kill switch.
        if (version == null
                || "DISABLED".equals(version.getStatus())
                || (!explicitlyPinned && !"ACTIVE".equals(version.getStatus()))) {
            throw new PlatformException("agent_version_unavailable", "Agent version is not active", null);
        }
        return version;
    }

    public AgentDefinition definition(String tenantId, String appId, String versionId) {
        return JsonUtils.parse(requireRunnable(tenantId, appId, versionId).getDefinitionJson(), AgentDefinition.class);
    }

    public AgentVersionEntity current(String tenantId, String appId) {
        if (appPermissions != null) requireOwnedApp(tenantId, appId);
        return versions.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(AgentVersionEntity::getAppId, required(appId, "appId"))
                .eq(AgentVersionEntity::getStatus, "ACTIVE")
                .orderByDesc(AgentVersionEntity::getVersionNumber)
                .last("LIMIT 1"));
    }

    private AgentVersionEntity createVersion(
            AppEntity app, AgentDefinition definition, String actorId, String summary, String rollbackFrom) {
        if (runtimes != null && !runtimes.available(definition.getRuntimeType())) {
            throw new PlatformException(
                    "agent_runtime_unavailable",
                    "Cannot publish Agent because runtime is not installed: " + definition.getRuntimeType(),
                    null);
        }
        AgentVersionEntity latest = versions.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getTenantId, app.getTenantId())
                .eq(AgentVersionEntity::getAppId, app.getId())
                .orderByDesc(AgentVersionEntity::getVersionNumber)
                .last("LIMIT 1"));
        versions.update(
                null,
                new LambdaUpdateWrapper<AgentVersionEntity>()
                        .set(AgentVersionEntity::getStatus, "SUPERSEDED")
                        .eq(AgentVersionEntity::getTenantId, app.getTenantId())
                        .eq(AgentVersionEntity::getAppId, app.getId())
                        .eq(AgentVersionEntity::getStatus, "ACTIVE"));
        AgentVersionEntity version = new AgentVersionEntity();
        version.setTenantId(app.getTenantId());
        version.setAppId(app.getId());
        version.setVersionNumber(
                latest == null || latest.getVersionNumber() == null ? 1 : latest.getVersionNumber() + 1);
        version.setStatus("ACTIVE");
        version.setDefinitionJson(JsonUtils.toJson(definition));
        version.setChangeSummary(text(summary));
        version.setPublishedBy(text(actorId));
        version.setPublishedAt(LocalDateTime.now());
        version.setRollbackFromVersionId(rollbackFrom);
        versions.insert(version);
        app.setPublished(true);
        app.setStatus("normal");
        updateOwnedApp(app);
        return version;
    }

    private AppEntity requireOwnedApp(String tenantId, String appId) {
        AppEntity app = apps.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(AppEntity::getId, required(appId, "appId"))
                .last("LIMIT 1"));
        if (app == null) throw new PlatformException("app_not_found", "Application not found", null);
        if (appPermissions != null) appPermissions.requireRead(app);
        return app;
    }

    private void updateOwnedApp(AppEntity app) {
        apps.update(
                app,
                new LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, app.getTenantId())
                        .eq(AppEntity::getId, app.getId()));
    }

    /** Serializes publication for one Agent so concurrent releases cannot allocate the same version number. */
    private AppEntity requireOwnedAppForUpdate(String tenantId, String appId) {
        AppEntity app = apps.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(AppEntity::getId, required(appId, "appId"))
                .last("LIMIT 1 FOR UPDATE"));
        if (app == null) throw new PlatformException("app_not_found", "Application not found", null);
        if (appPermissions != null) appPermissions.requireRead(app);
        return app;
    }

    private AgentVersionEntity requireOwnedVersion(String tenantId, String appId, String versionId) {
        AgentVersionEntity version = versions.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(AgentVersionEntity::getAppId, required(appId, "appId"))
                .eq(AgentVersionEntity::getId, required(versionId, "versionId"))
                .last("LIMIT 1"));
        if (version == null) throw new PlatformException("agent_version_not_found", "Agent version not found", null);
        return version;
    }

    private static LambdaUpdateWrapper<AgentVersionEntity> owned(AgentVersionEntity version) {
        return new LambdaUpdateWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getTenantId, version.getTenantId())
                .eq(AgentVersionEntity::getId, version.getId());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
