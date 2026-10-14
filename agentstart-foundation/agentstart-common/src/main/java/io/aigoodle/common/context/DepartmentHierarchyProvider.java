package io.aigoodle.common.context;

import java.util.Set;

/** 宿主注册 Spring Bean 实现；返回指定租户、部门的所有层级下级部门 ID。
 * 不包含自身也可。应读取可信组织数据，异常必须向上传递，不能降级放行。
 * 无实现时只支持部门精确匹配。 */
@FunctionalInterface
public interface DepartmentHierarchyProvider {
    Set<String> descendantDepartmentIds(String tenantId, String departmentId);

    /** 登录时可用同一 SPI 填充全局数据范围；应用授权会对被授权部门另行查询。 */
    default void populateDepartmentIds(CurrentUser user) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        if (user.getDepartmentId() != null && !user.getDepartmentId().isBlank()) {
            ids.add(user.getDepartmentId());
            String tenant = user.getTenantId() == null || user.getTenantId().isBlank()
                    ? UserContextHolder.DEFAULT_TENANT
                    : user.getTenantId();
            Set<String> descendants = descendantDepartmentIds(tenant, user.getDepartmentId());
            if (descendants != null) ids.addAll(descendants);
        }
        ids.removeIf(id -> id == null || id.isBlank());
        user.setDepartmentIds(Set.copyOf(ids));
    }
}
