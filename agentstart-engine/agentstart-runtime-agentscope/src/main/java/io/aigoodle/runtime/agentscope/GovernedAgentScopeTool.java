package io.aigoodle.runtime.agentscope;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Sends SDK tool calls through the platform gateway with the captured caller identity. */
public final class GovernedAgentScopeTool extends ToolBase {
    private final ToolRegistry registry;
    private final CurrentUser principal;
    private final ToolExecutionContext context;
    private final Runnable beforeExecution;

    public GovernedAgentScopeTool(
            ToolRegistry registry, String name, CurrentUser principal, ToolExecutionContext context) {
        this(registry, name, principal, context, () -> {});
    }

    GovernedAgentScopeTool(
            ToolRegistry registry,
            String name,
            CurrentUser principal,
            ToolExecutionContext context,
            Runnable beforeExecution) {
        super(ToolBase.builder()
                .name(name)
                .description(registry.get(name).description())
                .inputSchema(JsonUtils.parseMap(registry.get(name).inputSchema()))
                .concurrencySafe(false));
        this.registry = Objects.requireNonNull(registry, "registry");
        this.principal = principal;
        this.context = Objects.requireNonNull(context, "context");
        this.beforeExecution = Objects.requireNonNull(beforeExecution, "beforeExecution");
        if (principal != null && !Objects.equals(principal.getTenantId(), context.tenantId())) {
            throw new SecurityException("Tool tenant does not match the captured caller");
        }
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam parameter) {
        return Mono.fromCallable(() -> UserContextHolder.callAs(principal, () -> {
                    beforeExecution.run();
                    Object result = registry.execute(getName(), parameter.getInput(), context);
                    String text = result instanceof String value ? value : JsonUtils.toJson(result);
                    return ToolResultBlock.of(
                            parameter.getToolUseBlock().getId(),
                            getName(),
                            TextBlock.builder()
                                    .text(text == null ? "null" : text)
                                    .build());
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
