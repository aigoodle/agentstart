package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppPermissionEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.mapper.AppPermissionMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.DepartmentHierarchyProvider;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/** 应用查看权限；租户可见性检查在调用方执行，授权不能扩大租户范围。 */
public class AppPermissionService {
    private final AppMapper apps;
    private final AppPermissionMapper permissions;
    private final DepartmentHierarchyProvider departments;

    public AppPermissionService(
            AppMapper apps, AppPermissionMapper permissions, DepartmentHierarchyProvider departments) {
        this.apps = apps;
        this.permissions = permissions;
        this.departments = departments;
    }

    /** 独立工作流可以没有应用目录记录；存在关联应用时必须复用其数据权限。 */
    public void requireLinkedApp(String tenantId, String appId, boolean write) {
        if (appId == null || appId.isBlank()) return;
        AppEntity app = apps.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
                .eq(AppEntity::getId, appId));
        if (app != null) {
            requireRead(app);
            if (write) requireWrite(app);
        }
    }

    public Set<String> unreadableAppIds(String tenantId, Collection<String> appIds) {
        if (appIds.isEmpty()) return Set.of();
        List<AppEntity> linked = apps.selectList(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
                .in(AppEntity::getId, appIds));
        Set<String> readable =
                filterReadable(linked).stream().map(AppEntity::getId).collect(Collectors.toSet());
        return linked.stream()
                .map(AppEntity::getId)
                .filter(id -> !readable.contains(id))
                .collect(Collectors.toSet());
    }

    public void requireRead(AppEntity app) {
        if (!"RESTRICTED".equals(app.getDataAccessMode())) return;
        // Tenant-specific grants cannot authorize an identity from another tenant.
        if (!Objects.equals(app.getTenantId(), UserContextHolder.currentTenantId())) denied();
        if (!canRead(app, rows(app.getTenantId(), app.getId()), new HashMap<>())) denied();
    }

    /** 批量读取授权；同一部门子树在本次列表请求内只解析一次。 */
    public List<AppEntity> filterReadable(List<AppEntity> candidates) {
        if (candidates.isEmpty()) return candidates;
        List<String> restricted = candidates.stream()
                .filter(a -> "RESTRICTED".equals(a.getDataAccessMode()))
                .map(AppEntity::getId)
                .toList();
        if (restricted.isEmpty()) return candidates;
        Map<String, List<AppPermissionEntity>> grants = permissions
                .selectList(new LambdaQueryWrapper<AppPermissionEntity>()
                        .eq(AppPermissionEntity::getTenantId, UserContextHolder.currentTenantId())
                        .in(AppPermissionEntity::getAppId, restricted))
                .stream()
                .collect(Collectors.groupingBy(AppPermissionEntity::getAppId));
        Map<String, Set<String>> trees = new HashMap<>();
        return candidates.stream()
                .filter(a -> canRead(a, grants.getOrDefault(a.getId(), List.of()), trees))
                .toList();
    }

    private boolean canRead(AppEntity app, List<AppPermissionEntity> grants, Map<String, Set<String>> trees) {
        if (!"RESTRICTED".equals(app.getDataAccessMode())) return true;
        CurrentUser user = UserContextHolder.get();
        if (user == null || !Objects.equals(app.getTenantId(), UserContextHolder.currentTenantId())) return false;
        if (user.getPrincipalType() == PrincipalType.ANONYMOUS) return false;
        if (isManager()) return true;
        // 经宿主验证的应用密钥是独立能力，只允许其绑定的应用；客户端输入不构成此身份。
        if (user.getPrincipalType() == PrincipalType.APP_API_KEY) return Objects.equals(app.getId(), user.getAppId());
        for (AppPermissionEntity grant : grants) {
            String id = grant.getSubjectId();
            if (id == null || id.isBlank()) continue;
            if ("USER".equals(grant.getSubjectType()) && id.equals(user.getUserId())) return true;
            if ("ROLE".equals(grant.getSubjectType())
                    && user.getRoleIds() != null
                    && user.getRoleIds().contains(id)) return true;
            if ("DEPARTMENT".equals(grant.getSubjectType()) && user.getDepartmentId() != null) {
                if (id.equals(user.getDepartmentId())) return true;
                if (Boolean.TRUE.equals(grant.getIncludeDescendants())) {
                    Set<String> children = trees.computeIfAbsent(id, key -> {
                        Set<String> result = departments.descendantDepartmentIds(app.getTenantId(), key);
                        return result == null ? Set.of() : result;
                    });
                    if (children.contains(user.getDepartmentId())) return true;
                }
            }
        }
        return false;
    }

    public void requireManage(String tenantId) {
        if (!Objects.equals(tenantId, UserContextHolder.currentTenantId()) || !isManager()) denied();
    }

    /** 查看授权不能变成编辑应用的授权。旧应用 ALL 模式保留原宿主管理策略。 */
    public void requireWrite(AppEntity app) {
        if ("RESTRICTED".equals(app.getDataAccessMode())) requireManage(app.getTenantId());
    }

    protected boolean isManager() {
        CurrentUser user = UserContextHolder.get();
        return user != null
                && (UserContextHolder.hasScope("apps:permissions:write")
                        || (user.getRoles() != null
                                && user.getRoles().stream()
                                        .filter(Objects::nonNull)
                                        .anyMatch(r ->
                                                "ADMIN".equalsIgnoreCase(r) || "TENANT_ADMIN".equalsIgnoreCase(r))));
    }

    public AppPermissionSettings get(String tenantId, String appId) {
        requireManage(tenantId);
        AppEntity app = requireApp(tenantId, appId, false);
        return new AppPermissionSettings(
                app.getDataAccessMode() == null ? "ALL" : app.getDataAccessMode(),
                rows(tenantId, appId).stream()
                        .map(r -> new AppPermissionSettings.Grant(
                                r.getSubjectType(), r.getSubjectId(), Boolean.TRUE.equals(r.getIncludeDescendants())))
                        .toList());
    }

    @Transactional
    public AppPermissionSettings replace(String tenantId, String appId, AppPermissionSettings request) {
        requireManage(tenantId);
        if (request == null
                || !Set.of("ALL", "RESTRICTED").contains(Objects.toString(request.mode(), ""))
                || request.grants() == null
                || request.grants().size() > 1000)
            throw new IllegalArgumentException("mode 必须为 ALL/RESTRICTED，grants 必填且不超过 1000 项");
        Set<String> keys = new HashSet<>();
        for (var grant : request.grants()) {
            if (grant == null
                    || !Set.of("USER", "ROLE", "DEPARTMENT").contains(Objects.toString(grant.type(), ""))
                    || grant.subjectId() == null
                    || grant.subjectId().isBlank()
                    || grant.subjectId().length() > 128
                    || !grant.subjectId().equals(grant.subjectId().trim())
                    || (grant.includeDescendants() && !"DEPARTMENT".equals(grant.type()))
                    || !keys.add(grant.type() + ":" + grant.subjectId()))
                throw new IllegalArgumentException("授权类型/业务 ID 无效或重复；仅部门支持包含下级");
        }
        requireApp(tenantId, appId, true); // 串行化同一应用的全量替换
        deleteForApp(tenantId, appId);
        for (var grant : request.grants()) {
            AppPermissionEntity row = new AppPermissionEntity();
            row.setTenantId(tenantId);
            row.setAppId(appId);
            row.setSubjectType(grant.type());
            row.setSubjectId(grant.subjectId());
            row.setIncludeDescendants(grant.includeDescendants());
            permissions.insert(row);
        }
        apps.update(
                null,
                new LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, tenantId)
                        .eq(AppEntity::getId, appId)
                        .set(AppEntity::getDataAccessMode, request.mode()));
        return get(tenantId, appId);
    }

    public void deleteForApp(String tenantId, String appId) {
        permissions.delete(new LambdaQueryWrapper<AppPermissionEntity>()
                .eq(AppPermissionEntity::getTenantId, tenantId)
                .eq(AppPermissionEntity::getAppId, appId));
    }

    private List<AppPermissionEntity> rows(String tenantId, String appId) {
        return permissions.selectList(new LambdaQueryWrapper<AppPermissionEntity>()
                .eq(AppPermissionEntity::getTenantId, tenantId)
                .eq(AppPermissionEntity::getAppId, appId));
    }

    private AppEntity requireApp(String tenantId, String appId, boolean lock) {
        AppEntity app = apps.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
                .eq(AppEntity::getId, appId)
                .last(lock ? "FOR UPDATE" : "LIMIT 1"));
        if (app == null) throw new PlatformException("app_not_found", "应用不存在", null);
        return app;
    }

    private static void denied() {
        throw new PlatformException("forbidden", "无权访问此应用或修改应用授权", null);
    }
}
