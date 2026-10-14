package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.api.*;
import io.aigoodle.agent.runtime.AgentRuntime;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentNodeRuntimeDelegationTest {
    @Test
    void adaptsNodeConfigurationAndDelegatesToPublicAgentRuntime() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentResponse response = new AgentResponse().complete("runtime answer");
        response.setConversationId("conversation-1");
        when(runtime.run(any(), any())).thenReturn(response);
        AgentNodeExecutor executor = new AgentNodeExecutor(runtime);
        NodeDef node = NodeDef.of("agent-node", NodeType.AGENT)
                .with("modelProvider", "openai")
                .with("modelName", "gpt-test")
                .with("systemPrompt", "Use {{#sys.topic#}} context")
                .with("query", "Answer {{#sys.query#}}")
                .with("strategy", "function_calling")
                .with("tools", java.util.List.of("calculator"));
        ExecutionContext context =
                ExecutionContext.start(Map.of("topic", "math", "query", "6*7"), "conversation-1", null);

        var result = executor.execute(node, context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs()).containsEntry("text", "runtime answer");
        ArgumentCaptor<AgentDefinition> definition = ArgumentCaptor.forClass(AgentDefinition.class);
        ArgumentCaptor<AgentRequest> request = ArgumentCaptor.forClass(AgentRequest.class);
        verify(runtime).run(definition.capture(), request.capture());
        assertThat(definition.getValue().getStrategy()).isEqualTo(AgentStrategyType.FUNCTION_CALLING);
        assertThat(definition.getValue().getToolNames()).containsExactly("calculator");
        assertThat(definition.getValue().getInstructions()).isEqualTo("Use math context");
        assertThat(request.getValue().getQuery()).isEqualTo("Answer 6*7");
        assertThat(request.getValue().getConversationId()).isEqualTo("conversation-1");
    }
}
