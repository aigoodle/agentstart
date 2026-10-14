package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GovernedAgentScopeToolTest {
    @Test
    void sdkInvocationUsesGatewayAndCapturedIdentityOnWorker() {
        var caller = CurrentUser.builder().tenantId("tenant-a").userId("user-a").build();
        var context = new ToolExecutionContext("run-a", "tenant-a", "user-a", "conversation-a", Map.of());
        var count = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry(
                List.of(tool()),
                List.of(),
                (tool, arguments, actual) -> {
                    assertSame(context, actual);
                    assertSame(caller, UserContextHolder.get());
                    assertTrue(Thread.currentThread().getName().startsWith("boundedElastic"));
                    count.incrementAndGet();
                    return Map.of("answer", arguments.get("value"));
                },
                null);
        var adapter = new GovernedAgentScopeTool(registry, "echo", caller, context);
        var parameter = ToolCallParam.builder()
                .toolUseBlock(new ToolUseBlock("call-a", "echo", Map.of("value", 42)))
                .input(Map.of("value", 42))
                .build();
        assertEquals(0, count.get());
        var result = adapter.callAsync(parameter).block();
        assertEquals(1, count.get());
        assertEquals("call-a", result.getId());
        assertEquals("echo", result.getName());
        assertEquals("{\"answer\":42}", ((TextBlock) result.getOutput().getFirst()).getText());
        assertNull(UserContextHolder.get());
    }

    @Test
    void gatewayDenialAndTenantMismatchCannotBypassGovernance() {
        var context = new ToolExecutionContext("run", "tenant-a", "user", "conversation", Map.of());
        var registry = new ToolRegistry(
                List.of(tool()),
                List.of(),
                (tool, args, identity) -> {
                    throw new SecurityException("denied");
                },
                null);
        assertThrows(
                SecurityException.class,
                () -> new GovernedAgentScopeTool(
                        registry,
                        "echo",
                        CurrentUser.builder().tenantId("tenant-b").build(),
                        context));
        var adapter = new GovernedAgentScopeTool(registry, "echo", null, context);
        assertThrows(SecurityException.class, () -> adapter.callAsync(ToolCallParam.builder()
                        .toolUseBlock(new ToolUseBlock("id", "echo", Map.of()))
                        .input(Map.of())
                        .build())
                .block());
    }

    private static ToolDefinition tool() {
        return new ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "Echo a value";
            }

            public Object execute(Map<String, Object> args) {
                fail("SDK must use the execution gateway");
                return null;
            }
        };
    }
}
