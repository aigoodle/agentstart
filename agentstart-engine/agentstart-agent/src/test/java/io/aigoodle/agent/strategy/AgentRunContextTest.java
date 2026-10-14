package io.aigoodle.agent.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.tool.ToolDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRunContextTest {

    @Test
    void suppliesSafeDefaultsForOptionalRuntimeCollaborators() {
        AgentRunContext context = AgentRunContext.builder().build();

        assertThat(context.getHistory()).isEmpty();
        assertThat(context.getTools()).isEmpty();
        assertThat(context.getApprovalGate()).isNotNull();
        assertThat(context.getApprovalGate()
                        .review(new ApprovalGate.ToolCall("agent-1", "conversation-1", "search", "{}")))
                .isEqualTo(ApprovalGate.Decision.APPROVE);
        assertThat(context.isTokenStreamingEnabled()).isFalse();
    }

    @Test
    void protectsStrategiesWhenOptionalCollectionsAreExplicitlySetToNull() {
        AgentRunContext context = AgentRunContext.builder()
                .history(null)
                .tools(null)
                .approvalGate(null)
                .build();

        assertThat(context.getHistory()).isEmpty();
        assertThat(context.getTools()).isEmpty();
        assertThat(context.getApprovalGate()).isNotNull();
    }

    @Test
    void publishesOnlyMeaningfulEvents() {
        List<AgentStep> steps = new ArrayList<>();
        List<String> tokenDeltas = new ArrayList<>();
        AgentRunContext context = AgentRunContext.builder()
                .stepListener(steps::add)
                .tokenListener(tokenDeltas::add)
                .build();
        AgentStep actionStep = AgentStep.of(AgentStep.Kind.ACTION, "search");

        context.publishStep(null);
        context.publishStep(actionStep);
        context.publishToken(null);
        context.publishToken("");
        context.publishToken("answer");

        assertThat(steps).containsExactly(actionStep);
        assertThat(tokenDeltas).containsExactly("answer");
        assertThat(context.isTokenStreamingEnabled()).isTrue();
    }

    @Test
    void listenerFailuresDoNotAbortTheAgentRun() {
        AgentRunContext context = AgentRunContext.builder()
                .stepListener(step -> {
                    throw new IllegalStateException("disconnected");
                })
                .tokenListener(token -> {
                    throw new IllegalStateException("disconnected");
                })
                .build();

        assertThatCode(() -> {
                    context.publishStep(AgentStep.of(AgentStep.Kind.FINAL, "done"));
                    context.publishToken("done");
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWorkAfterDeadline() {
        AgentRunContext context = AgentRunContext.builder()
                .runId("run-timeout")
                .deadline(Instant.now().minusMillis(1))
                .build();

        assertThatThrownBy(context::checkActive)
                .isInstanceOf(AgentRunInterruptedException.class)
                .satisfies(error -> assertThat(((AgentRunInterruptedException) error).isTimedOut())
                        .isTrue());
    }

    @Test
    void rejectsWorkWhenDurableRunWasCancelled() {
        AgentRunContext context = AgentRunContext.builder()
                .runId("run-cancelled")
                .active(() -> false)
                .build();

        assertThatThrownBy(context::checkActive)
                .isInstanceOf(AgentRunInterruptedException.class)
                .satisfies(error -> assertThat(((AgentRunInterruptedException) error).isTimedOut())
                        .isFalse());
    }

    @Test
    void failsClosedBeforeExceedingModelAndToolBudgets() {
        AgentRunContext context = AgentRunContext.builder()
                .definition(AgentDefinition.builder()
                        .tenantId("tenant-a")
                        .maxModelCalls(1)
                        .maxToolCalls(1)
                        .build())
                .build();

        assertThat(context.claimModelCall()).isEqualTo(1);
        assertThatThrownBy(context::claimModelCall)
                .isInstanceOfSatisfying(AgentExecutionBudgetExceededException.class, error -> {
                    assertThat(error.resource()).isEqualTo("model");
                    assertThat(error.limit()).isEqualTo(1);
                });

        assertThat(context.executeTool(echoTool(), Map.of("value", "first"))).isEqualTo("first");
        assertThatThrownBy(() -> context.executeTool(echoTool(), Map.of("value", "second")))
                .isInstanceOfSatisfying(AgentExecutionBudgetExceededException.class, error -> {
                    assertThat(error.resource()).isEqualTo("tool");
                    assertThat(error.limit()).isEqualTo(1);
                });
        assertThat(context.modelCallCount()).isEqualTo(1);
        assertThat(context.toolCallCount()).isEqualTo(1);
    }

    @Test
    void zeroBudgetsRemainBackwardCompatibleAndUnlimited() {
        AgentRunContext context = AgentRunContext.builder()
                .definition(AgentDefinition.builder().build())
                .build();

        assertThat(context.claimModelCall()).isEqualTo(1);
        assertThat(context.claimModelCall()).isEqualTo(2);
        assertThat(context.executeTool(echoTool(), Map.of("value", "ok"))).isEqualTo("ok");
    }

    private static ToolDefinition echoTool() {
        return new ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "echo";
            }

            public String inputSchema() {
                return "{}";
            }

            public Object execute(Map<String, Object> arguments) {
                return arguments.get("value");
            }
        };
    }
}
