package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.*;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentRuntime;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginAgentNodeTest {
    @Test
    void externalRuntimeDoesNotRequireNativeModelAndReceivesWorkflowIdentity() {
        AgentRuntime runtime = (definition, request, steps, tokens) -> {
            assertThat(definition.getRuntimeType()).isEqualTo("PLUGIN");
            assertThat(definition.getRuntimeRef()).isEqualTo("video/plan");
            assertThat(UserContextHolder.currentTenantId()).isEqualTo("tenant-a");
            assertThat(UserContextHolder.currentUserId()).isEqualTo("user-a");
            return AgentResponse.forConversation(request.getConversationId()).complete("Script");
        };
        var context = ExecutionContext.start(Map.of("query", "Write"), "c1", null);
        context.setTenantId("tenant-a");
        context.setUserId("user-a");
        var result = new AgentNodeExecutor(runtime)
                .execute(
                        NodeDef.of("agent", NodeType.AGENT)
                                .with("runtimeType", "PLUGIN")
                                .with("runtimeRef", "video/plan"),
                        context);
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs()).containsEntry("text", "Script");
        assertThat(UserContextHolder.get()).isNull();
    }
}
