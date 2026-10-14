package io.aigoodle.common.directory;

import java.util.Set;

/**
 * 宿主人员目录的只读视图。手机号、邮箱属于可选敏感字段，宿主可以按调用方权限返回空值。
 */
public record UserInfo(
        String id,
        String tenantId,
        String username,
        String displayName,
        String avatarUrl,
        String employeeNo,
        String email,
        String mobile,
        String primaryDepartmentId,
        Set<String> departmentIds,
        Set<String> roleIds,
        String status) {

    public UserInfo {
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
    }
}
