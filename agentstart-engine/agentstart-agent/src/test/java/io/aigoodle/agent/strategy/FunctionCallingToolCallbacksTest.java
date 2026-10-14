package io.aigoodle.agent.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class FunctionCallingToolCallbacksTest {

    @Test
    void deniedToolIsObservableButNeverExecuted() {
        AtomicBoolean executed = new AtomicBoolean();
        ToolDefinition sensitiveTool = tool("delete_record", arguments -> {
            executed.set(true);
            return "deleted";
        });
        AgentRunContext context = context(sensitiveTool, Set.of("delete_record"), call -> ApprovalGate.Decision.DENY);
        AgentResponse response = new AgentResponse();

        ToolCallback callback =
                new FunctionCallingToolCallbacks().create(context, response).getFirst();
        String result = callback.call("{\"id\":42}");

        assertThat(executed).isFalse();
        assertThat(result).contains("was not approved");
        assertThat(response.getSteps())
                .extracting(AgentStep::getKind)
                .containsExactly(AgentStep.Kind.ACTION, AgentStep.Kind.OBSERVATION);
    }

    @Test
    void toolFailureBecomesAnObservationInsteadOfEscapingTheModelLoop() {
        ToolDefinition failingTool = tool("unstable", arguments -> {
            throw new IllegalStateException("service unavailable");
        });
        AgentRunContext context = context(failingTool, Set.of(), call -> ApprovalGate.Decision.APPROVE);
        AgentResponse response = new AgentResponse();

        String result = new FunctionCallingToolCallbacks()
                .create(context, response)
                .getFirst()
                .call("{}");

        assertThat(result).isEqualTo("error: service unavailable");
        assertThat(response.getSteps().getLast().getObservation()).isEqualTo("error: service unavailable");
    }

    @Test
    void missingApprovalDecisionFailsClosed() {
        AtomicBoolean executed = new AtomicBoolean();
        ToolDefinition sensitiveTool = tool("delete_record", arguments -> {
            executed.set(true);
            return "deleted";
        });
        AgentRunContext context = context(sensitiveTool, Set.of("delete_record"), call -> null);
        AgentResponse response = new AgentResponse();

        String result = new FunctionCallingToolCallbacks()
                .create(context, response)
                .getFirst()
                .call("{\"id\":42}");

        assertThat(executed).isFalse();
        assertThat(result).contains("DENY");
        assertThat(response.getSteps())
                .extracting(AgentStep::getKind)
                .containsExactly(AgentStep.Kind.ACTION, AgentStep.Kind.OBSERVATION);
    }

    private static AgentRunContext context(
            ToolDefinition tool, Set<String> approvalRequiredTools, ApprovalGate approvalGate) {
        return AgentRunContext.builder()
                .definition(AgentDefinition.builder()
                        .id("agent-1")
                        .approvalRequiredTools(approvalRequiredTools)
                        .build())
                .conversationId("conversation-1")
                .tools(List.of(tool))
                .approvalGate(approvalGate)
                .build();
    }

    private static ToolDefinition tool(String name, ToolOperation operation) {
        return new ToolDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name;
            }

            @Override
            public Object execute(Map<String, Object> arguments) {
                return operation.execute(arguments);
            }
        };
    }

    @FunctionalInterface
    private interface ToolOperation {
        Object execute(Map<String, Object> arguments);
    }
}
