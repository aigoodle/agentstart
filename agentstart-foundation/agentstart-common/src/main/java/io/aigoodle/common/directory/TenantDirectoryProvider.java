package io.aigoodle.common.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 宿主注册为 Spring Bean 的只读租户目录 SPI。 */
public interface TenantDirectoryProvider {

    Optional<TenantInfo> findTenant(String tenantId);

    /** 批量回显入口；返回结果以租户 ID 为 key，不存在或不可见的 ID 不放入结果。 */
    Map<String, TenantInfo> findTenants(Collection<String> tenantIds);
}
