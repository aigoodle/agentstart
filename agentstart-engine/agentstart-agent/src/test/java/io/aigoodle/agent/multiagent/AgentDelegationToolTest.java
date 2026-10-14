package io.aigoodle.agent.multiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.exception.PlatformException;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentDelegationToolTest {

    @Test
    void delegatesTheNamedInputAndReturnsCompletedText() {
        BiFunction<String, AgentRequest, AgentResponse> agentRunner = mock(BiFunction.class);
        AgentResponse response = response(AgentResponse.Status.COMPLETED, "Completed work", null);
        when(agentRunner.apply(eq("worker-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
        AgentDelegationTool tool = tool(agentRunner);

        Object result = tool.execute(Map.of("input", "Research this topic"));

        ArgumentCaptor<AgentRequest> delegatedRequest = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentRunner).apply(eq("worker-1"), delegatedRequest.capture());
        assertThat(delegatedRequest.getValue().getQuery()).isEqualTo("Research this topic");
        assertThat(result).isEqualTo("Completed work");
    }

    @Test
    void acceptsTheLegacyQueryArgument() {
        BiFunction<String, AgentRequest, AgentResponse> agentRunner = mock(BiFunction.class);
        when(agentRunner.apply(eq("worker-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(AgentResponse.Status.COMPLETED, "Done", null));
        AgentDelegationTool tool = tool(agentRunner);

        Object result = tool.execute(Map.of("query", "Legacy task"));

        assertThat(result).isEqualTo("Done");
    }

    @Test
    void rejectsMissingOrBlankDelegationInput() {
        AgentDelegationTool tool = tool(mock(BiFunction.class));

        assertThatThrownBy(() -> tool.execute(Map.of("input", " ")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("delegation_input_required"));
    }

    @Test
    void exposesApprovalAsAnExplicitDelegationState() {
        BiFunction<String, AgentRequest, AgentResponse> agentRunner = mock(BiFunction.class);
        when(agentRunner.apply(eq("worker-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(AgentResponse.Status.AWAITING_APPROVAL, null, null));
        AgentDelegationTool tool = tool(agentRunner);

        assertThatThrownBy(() -> tool.execute(Map.of("input", "Sensitive task")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("delegation_awaiting_approval"));
    }

    @Test
    void preservesTheDelegatedFailureReason() {
        BiFunction<String, AgentRequest, AgentResponse> agentRunner = mock(BiFunction.class);
        when(agentRunner.apply(eq("worker-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(AgentResponse.Status.FAILED, null, "Model unavailable"));
        AgentDelegationTool tool = tool(agentRunner);

        assertThatThrownBy(() -> tool.execute(Map.of("input", "Do work")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("delegation_failed");
                    assertThat(exception).hasMessageContaining("Model unavailable");
                });
    }

    private static AgentDelegationTool tool(BiFunction<String, AgentRequest, AgentResponse> agentRunner) {
        return new AgentDelegationTool("delegate_to_worker", "Delegate to worker", "worker-1", agentRunner);
    }

    private static AgentResponse response(AgentResponse.Status status, String text, String error) {
        AgentResponse response = new AgentResponse();
        response.setStatus(status);
        response.setText(text);
        response.setError(error);
        return response;
    }
}
