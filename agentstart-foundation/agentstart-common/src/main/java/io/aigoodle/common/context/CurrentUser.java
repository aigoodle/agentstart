package io.aigoodle.common.context;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户的运行时视图 —— 由使用方（引入 spring-agent-start 模块的宿主项目）
 * 在自己的登录 / 鉴权拦截器里构造并塞入 {@link UserContextHolder}，随后
 * spring-agent-start 各模块通过 {@code UserContextHolder.get()} / {@code
 * currentTenantId()} 消费。
 *
 * <p>本类是 <b>纯值对象</b>，不做任何鉴权 / 校验 —— 由宿主项目负责保证塞进来的
 * 数据可信。字段全部可空，取值方需自行处理 {@code null}（{@link
 * UserContextHolder#currentTenantId()} 已内建 {@code "default"} 兜底）。
 *
 * <h3>常见构造方式</h3>
 * <pre>{@code
 * CurrentUser me = CurrentUser.builder()
 *         .userId("u-1001")
 *         .username("alice")
 *         .tenantId("acme")
 *         .roles(Set.of("ADMIN"))
 *         .build();
 * UserContextHolder.set(me);
 * }</pre>
 *
 * <p>如需在 {@link #extra} 上挂业务方私有字段（部门、员工工号、外部会员 id 等），
 * 直接 {@code me.put("dept", "R&D")} 即可 —— {@link #extra} 由 builder 默认
 * 初始化为空 map，读取端不会 NPE。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser implements Serializable {

    /** 用户主键（宿主项目的用户表 id）。 */
    private String userId;

    /** 展示用用户名 / 登录名。用于日志、审计等展示场景，不参与鉴权。 */
    private String username;

    /** 多租户维度键。为空时 {@link UserContextHolder#currentTenantId()} 兜底为 {@code "default"}。 */
    private String tenantId;

    /** 可选：来源应用 id（api-key 兑换出的上下文场景才有值）。 */
    private String appId;

    /** Authentication mechanism used to establish this trusted context. */
    private PrincipalType principalType;

    /** Fine-grained permissions already granted by the host authentication layer. */
    private Set<String> scopes;

    /** 可选：角色列表 —— 使用方鉴权后的原始角色标记，spring-agent-start 内部不解释含义。 */
    private Set<String> roles;

    /** 宿主业务系统的角色 ID；与展示用角色标记 roles 分开。 */
    private Set<String> roleIds;

    /** 当前所属部门的业务 ID。 */
    private String departmentId;

    /** 当前部门及其下级部门的数据范围，由宿主通过 DepartmentHierarchyProvider 填充。
     * 不能用此集合反向推导当前用户属于上级部门。 */
    private Set<String> departmentIds;

    /** 逃生舱：宿主项目私有字段挂这里，避免为每个新维度改本类。 */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();

    /** 保留新增部门字段之前的全参数构造入口。新接入建议使用 builder。 */
    public CurrentUser(
            String userId,
            String username,
            String tenantId,
            String appId,
            PrincipalType principalType,
            Set<String> scopes,
            Set<String> roles,
            Map<String, Object> extra) {
        this(userId, username, tenantId, appId, principalType, scopes, roles, null, null, null, extra);
    }

    /** 便捷 put —— 免去调用方判空。 */
    public CurrentUser put(String key, Object value) {
        if (this.extra == null) this.extra = new HashMap<>();
        this.extra.put(key, value);
        return this;
    }

    /** 便捷 get —— extra 为空返回 {@code null}，不抛。 */
    public Object attr(String key) {
        return this.extra == null ? null : this.extra.get(key);
    }
}
