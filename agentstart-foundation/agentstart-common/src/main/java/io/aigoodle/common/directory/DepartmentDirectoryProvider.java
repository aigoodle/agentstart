package io.aigoodle.common.directory;

import io.aigoodle.common.context.DepartmentHierarchyProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 宿主注册为 Spring Bean 的只读部门目录 SPI。
 *
 * <p>继承现有层级 SPI，因此同一个实现可直接供应用部门授权使用。</p>
 */
public interface DepartmentDirectoryProvider extends DepartmentHierarchyProvider {

    Optional<DepartmentInfo> findDepartment(String tenantId, String departmentId);

    /** 批量回显入口；返回结果以部门 ID 为 key。 */
    Map<String, DepartmentInfo> findDepartments(String tenantId, Collection<String> departmentIds);

    /** 只返回直接下级。 */
    List<DepartmentInfo> findChildren(String tenantId, String parentDepartmentId);

    /** 返回所有层级下级；是否包含自身必须为 false。 */
    List<DepartmentInfo> findDescendants(String tenantId, String departmentId);

    @Override
    default java.util.Set<String> descendantDepartmentIds(String tenantId, String departmentId) {
        List<DepartmentInfo> descendants = findDescendants(tenantId, departmentId);
        if (descendants == null || descendants.isEmpty()) return java.util.Set.of();
        return descendants.stream()
                .map(DepartmentInfo::id)
                .filter(id -> id != null && !id.isBlank() && !id.equals(departmentId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
