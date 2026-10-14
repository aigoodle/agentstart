package io.aigoodle.common.context;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 当前登录用户的线程级持有器 —— spring-agent-start 各模块获取"当前是谁在调用"的
 * <b>唯一入口</b>。本模块本身不提供登录 / 鉴权能力：由宿主项目在自己的拦截器里
 * {@link #set(CurrentUser)}，请求结束务必 {@link #clear()}，中间任何位置（Service /
 * Mapper / 内部工具）都可通过 {@link #get()} / {@link #currentTenantId()} 取用。
 *
 * <h3>宿主项目接入 —— Spring MVC 场景（Servlet）</h3>
 * <pre>{@code
 * // 1. 在你自己的登录拦截器里
 * public class LoginInterceptor implements HandlerInterceptor {
 *     @Override
 *     public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
 *         MyLoginUser me = SecurityUtils.currentLoginUser();   // 宿主自有登录体系
 *         if (me != null) {
 *             UserContextHolder.set(CurrentUser.builder()
 *                     .userId(me.getId())
 *                     .username(me.getName())
 *                     .tenantId(me.getTenantCode())
 *                     .roles(me.getRoles())
 *                     .build());
 *         }
 *         return true;
 *     }
 *     @Override
 *     public void afterCompletion(HttpServletRequest req, HttpServletResponse resp,
 *                                 Object handler, Exception ex) {
 *         UserContextHolder.clear();   // ★ 必做,防止线程复用串号
 *     }
 * }
 * }</pre>
 *
 * <h3>宿主项目接入 —— Spring WebFlux 场景</h3>
 * <p>WebFlux 每一步调度都可能换线程，ThreadLocal 出了 WebFilter 就不保证还在。
 * 将身份写入 Reactor Context；HTTP 接入层通过上下文传播桥接同步调用。
 * 不要依赖订阅线程与完成线程相同，也不要在整个异步请求期间持有 ThreadLocal。使用
 * {@link reactor.core.publisher.Mono#contextWrite(java.util.function.Function)}
 * 把用户挂到 Reactor Context 上，下游通过 {@link #getReactive(reactor.util.context.ContextView)}
 * 或 {@code Mono.deferContextual} 显式拉取：
 * <pre>{@code
 * @Component
 * @Order(-100)
 * public class UserContextWebFilter implements WebFilter {
 *     public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
 *         CurrentUser me = resolveFromHeaders(exchange);       // 宿主自己实现
 *         if (me == null) return chain.filter(exchange);
 *         return chain.filter(exchange)
 *                 .contextWrite(ctx -> ctx.put(UserContextHolder.CONTEXT_KEY, me));
 *     }
 * }
 * }</pre>
 *
 * <h3>spring-agent-start 内部用法</h3>
 * <pre>{@code
 * public List<DatasetEntity> list() {
 *     String tenant = UserContextHolder.currentTenantId();
 *     return datasetMapper.selectList(new LambdaQueryWrapper<DatasetEntity>()
 *             .eq(DatasetEntity::getTenantId, tenant));
 * }
 * }</pre>
 *
 * <p>本类是 {@code final} + 私有构造，避免被继承或实例化 —— 全部 API 通过静态方法暴露。
 */
public final class UserContextHolder {

    /**
     * Reactor Context 里存放 {@link CurrentUser} 用的 key。WebFlux 场景下宿主项目
     * 用 {@code ctx.put(UserContextHolder.CONTEXT_KEY, user)} 挂入，下游通过
     * {@link #getReactive(reactor.util.context.ContextView)} 取回。
     */
    public static final String CONTEXT_KEY = "spring-agent.current-user";

    /** {@code tenantId} 为空 / 未登录时的租户兜底值，全项目约定。 */
    public static final String DEFAULT_TENANT = "default";

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
        // no instances
    }

    // ============================================================ 写入 / 清理

    /**
     * 塞入当前线程的用户上下文。宿主项目的拦截器在请求进入时调用。
     * {@code null} 值等价于 {@link #clear()}，方便"未登录 / 匿名"分支复用同一入口。
     */
    public static void set(CurrentUser user) {
        if (user == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(user);
        }
    }

    /**
     * 清空当前线程的用户上下文 —— <b>请求结束务必调用</b>，否则线程池复用时会串号。
     * Spring MVC 建议放在 {@code HandlerInterceptor#afterCompletion}；WebFlux 建议
     * 放在 {@code Mono#doFinally}。
     */
    public static void clear() {
        HOLDER.remove();
    }

    // ============================================================ 同步读取

    /**
     * 取当前线程的用户；未登录 / 未 set 时返回 {@code null}。
     * 需要"必须登录"语义的调用点请用 {@link #require()}。
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 与 {@link #get()} 等价，但用 {@link Optional} 包装。 */
    public static Optional<CurrentUser> tryGet() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 取当前用户；未登录时抛 {@link IllegalStateException} —— 用于确定"没有匿名分支"
     * 的调用点，避免调用方漏判 null。
     */
    public static CurrentUser require() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null) {
            throw new IllegalStateException(
                    "No CurrentUser in context — host application must set it in its login filter before invoking spring-agent-start APIs");
        }
        return currentUser;
    }

    // ============================================================ 便捷取值

    /**
     * 取当前用户的 tenantId；未登录或 tenantId 为空时返回 {@link #DEFAULT_TENANT}。
     * spring-agent-start 内部所有"按租户过滤"的查询都应通过此方法拿租户键。
     */
    public static String currentTenantId() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null) {
            return DEFAULT_TENANT;
        }
        String tenantId = currentUser.getTenantId();
        return (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId;
    }

    /** 取当前用户 id；未登录返回 {@code null}。 */
    public static String currentUserId() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.getUserId();
    }

    public static String currentDepartmentId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getDepartmentId();
    }

    public static java.util.Set<String> currentDepartmentIds() {
        CurrentUser user = HOLDER.get();
        return user == null || user.getDepartmentIds() == null
                ? java.util.Set.of()
                : java.util.Collections.unmodifiableSet(user.getDepartmentIds());
    }

    public static java.util.Set<String> currentRoleIds() {
        CurrentUser user = HOLDER.get();
        return user == null || user.getRoleIds() == null
                ? java.util.Set.of()
                : java.util.Collections.unmodifiableSet(user.getRoleIds());
    }

    /** 取当前用户名；未登录返回 {@code null}。 */
    public static String currentUsername() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.getUsername();
    }

    /** Application resolved by the trusted authentication layer, or {@code null}. */
    public static String currentAppId() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null) {
            return null;
        }
        return trimToNull(currentUser.getAppId());
    }

    /** Require an application-bound context. */
    public static String requireAppId() {
        String appId = currentAppId();
        if (appId == null) {
            throw new IllegalStateException("No appId in current user context");
        }
        return appId;
    }

    public static PrincipalType currentPrincipalType() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.getPrincipalType();
    }

    public static boolean hasScope(String scope) {
        CurrentUser currentUser = HOLDER.get();
        return currentUser != null
                && currentUser.getScopes() != null
                && scope != null
                && currentUser.getScopes().contains(scope);
    }

    /** 是否已登录（== ThreadLocal 里有值）。 */
    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ============================================================ 反应式桥接

    /**
     * WebFlux 场景下从 Reactor {@link reactor.util.context.ContextView} 里取当前用户。
     * <pre>{@code
     * Mono.deferContextual(ctx -> {
     *     CurrentUser me = UserContextHolder.getReactive(ctx).orElse(null);
     *     ...
     * });
     * }</pre>
     * 依赖 reactor-core：仅在调用方类路径已有 reactor 时才会触发 —— common 模块没有硬依赖。
     */
    public static Optional<CurrentUser> getReactive(reactor.util.context.ContextView context) {
        if (context == null || !context.hasKey(CONTEXT_KEY)) {
            return Optional.empty();
        }
        Object contextValue = context.get(CONTEXT_KEY);
        return contextValue instanceof CurrentUser currentUser ? Optional.of(currentUser) : Optional.empty();
    }

    // ============================================================ 测试 / 临时切换

    /**
     * 在给定用户上下文中运行一段代码，无论成功失败都恢复到原上下文。
     * 主要给单元测试和内部"切租户跑一次"的场景使用。
     * <pre>{@code
     * UserContextHolder.runAs(mockAdmin, () -> service.deleteById(id));
     * }</pre>
     */
    public static void runAs(CurrentUser user, Runnable action) {
        try (ContextScope ignored = openScope(user)) {
            action.run();
        }
    }

    /**
     * Run a value-producing operation with the supplied user and restore the
     * previous context afterwards.
     */
    public static <T> T callAs(CurrentUser user, Supplier<T> action) {
        try (ContextScope ignored = openScope(user)) {
            return action.get();
        }
    }

    /**
     * Bind a user until the returned scope is closed. Intended for explicit
     * try-with-resources blocks and nested context switching.
     */
    public static ContextScope openScope(CurrentUser user) {
        CurrentUser previousUser = HOLDER.get();
        set(user);
        return new ContextScope(previousUser);
    }

    public static final class ContextScope implements AutoCloseable {

        private final CurrentUser previousUser;
        private boolean closed;

        private ContextScope(CurrentUser previousUser) {
            this.previousUser = previousUser;
        }

        @Override
        public void close() {
            if (!closed) {
                set(previousUser);
                closed = true;
            }
        }
    }
}
