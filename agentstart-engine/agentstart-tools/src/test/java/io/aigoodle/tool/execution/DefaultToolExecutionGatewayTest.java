package io.aigoodle.tool.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.tool.ToolDefinition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultToolExecutionGatewayTest {
    private DefaultToolExecutionGateway gateway;

    @Test
    void carriesCallerIntoWorkerAndRestoresCallingThread() {
        gateway = new DefaultToolExecutionGateway(properties(), List.of(), List.of());
        var caller = CurrentUser.builder().tenantId("tenant-a").userId("user-a").build();
        ToolDefinition identity = new ToolDefinition() {
            public String name() {
                return "identity";
            }

            public String description() {
                return "Checks worker identity";
            }

            public Object execute(Map<String, Object> arguments) {
                return UserContextHolder.require();
            }
        };
        Object result = UserContextHolder.callAs(
                caller, () -> gateway.execute(identity, Map.of(), ToolExecutionContext.anonymous()));
        assertThat(result).isSameAs(caller);
        assertThat(UserContextHolder.get()).isNull();
        assertThat(gateway.execute(
                        new ToolDefinition() {
                            public String name() {
                                return "anonymous";
                            }

                            public String description() {
                                return "Checks anonymous worker";
                            }

                            public Object execute(Map<String, Object> arguments) {
                                return UserContextHolder.get();
                            }
                        },
                        Map.of(),
                        ToolExecutionContext.anonymous()))
                .isNull();
    }

    @AfterEach
    void close() {
        if (gateway != null) gateway.close();
    }

    @Test
    void rejectsInvocationBeforeTheToolRuns() {
        AtomicInteger calls = new AtomicInteger();
        List<ToolExecutionRecord> records = new ArrayList<>();
        gateway = new DefaultToolExecutionGateway(
                properties(),
                List.of((tool, args, context) -> ToolExecutionPolicy.Decision.deny("blocked")),
                List.of(records::add));

        assertThatThrownBy(() -> gateway.execute(
                        tool("danger", false, calls, "never"), Map.of(), ToolExecutionContext.anonymous()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("blocked");
        assertThat(calls).hasValue(0);
        assertThat(records)
                .singleElement()
                .extracting(ToolExecutionRecord::status)
                .isEqualTo(ToolExecutionRecord.Status.DENIED);
    }

    @Test
    void retriesOnlyIdempotentToolsAndTruncatesTextOutput() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutionProperties properties = properties();
        properties.setMaxRetries(2);
        properties.setMaxOutputChars(5);
        gateway = new DefaultToolExecutionGateway(properties, List.of(), List.of());
        ToolDefinition tool = new ToolDefinition() {
            public String name() {
                return "safe";
            }

            public String description() {
                return "safe";
            }

            public boolean idempotent() {
                return true;
            }

            public Object execute(Map<String, Object> args) {
                if (calls.incrementAndGet() < 2) throw new IllegalStateException("retry");
                return "123456789";
            }
        };

        assertThat(gateway.execute(tool, Map.of(), ToolExecutionContext.anonymous()))
                .isEqualTo("12345\n...[tool output truncated]");
        assertThat(calls).hasValue(2);
    }

    @Test
    void interruptsTimedOutInvocation() {
        ToolExecutionProperties properties = properties();
        properties.setTimeout(Duration.ofMillis(20));
        List<ToolExecutionRecord> records = new ArrayList<>();
        gateway = new DefaultToolExecutionGateway(properties, List.of(), List.of(records::add));

        assertThatThrownBy(() -> gateway.execute(
                        new ToolDefinition() {
                            public String name() {
                                return "slow";
                            }

                            public String description() {
                                return "slow";
                            }

                            public Object execute(Map<String, Object> args) {
                                try {
                                    Thread.sleep(10_000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return "late";
                            }
                        },
                        Map.of(),
                        ToolExecutionContext.anonymous()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("timed out");
        assertThat(records.getLast().status()).isEqualTo(ToolExecutionRecord.Status.TIMED_OUT);
    }

    @Test
    void removesAuthorizationFromPublishedAuditContext() {
        List<ToolExecutionRecord> records = new ArrayList<>();
        gateway = new DefaultToolExecutionGateway(properties(), List.of(), List.of(records::add));
        ToolExecutionContext context = new ToolExecutionContext(
                null, "tenant-a", "user-1", null, Map.of("authorization", "Bearer secret", "trace", "visible"));

        gateway.execute(tool("safe", false, new AtomicInteger(), "ok"), Map.of(), context);

        assertThat(records).singleElement().satisfies(record -> assertThat(
                        record.context().metadata())
                .doesNotContainKey("authorization")
                .containsEntry("trace", "visible"));
    }

    private static ToolExecutionProperties properties() {
        ToolExecutionProperties properties = new ToolExecutionProperties();
        properties.setMaxConcurrent(2);
        return properties;
    }

    private static ToolDefinition tool(String name, boolean idempotent, AtomicInteger calls, Object result) {
        return new ToolDefinition() {
            public String name() {
                return name;
            }

            public String description() {
                return name;
            }

            public boolean idempotent() {
                return idempotent;
            }

            public Object execute(Map<String, Object> args) {
                calls.incrementAndGet();
                return result;
            }
        };
    }
}
