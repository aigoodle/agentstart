package io.aigoodle.common.directory;

/** 宿主部门目录的只读视图；组织树及其生命周期仍由宿主系统管理。 */
public record DepartmentInfo(
        String id,
        String tenantId,
        String parentId,
        String code,
        String name,
        String fullName,
        String path,
        Integer sort,
        String leaderUserId,
        String status,
        boolean leaf) {}
