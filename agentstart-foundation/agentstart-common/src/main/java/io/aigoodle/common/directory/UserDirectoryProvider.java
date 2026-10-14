package io.aigoodle.common.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 宿主注册为 Spring Bean 的只读人员目录 SPI。 */
public interface UserDirectoryProvider {

    Optional<UserInfo> findUser(String tenantId, String userId);

    /** 批量回显入口；返回结果以人员 ID 为 key，避免列表页面产生 N+1 查询。 */
    Map<String, UserInfo> findUsers(String tenantId, Collection<String> userIds);

    /**
     * 查询一个或多个部门的人员。includeDescendants=true 时由宿主按自己的组织树展开下级部门。
     */
    UserPage findUsersByDepartment(
            String tenantId, Collection<String> departmentIds, boolean includeDescendants, UserQuery query);

    /** 在当前租户范围内搜索人员，用于人员选择器等场景。 */
    UserPage searchUsers(String tenantId, UserQuery query);
}
