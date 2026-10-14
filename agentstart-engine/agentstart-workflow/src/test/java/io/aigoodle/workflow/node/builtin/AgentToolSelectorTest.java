package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class AgentToolSelectorTest {

    private final ToolCallback search = tool("search");
    private final ToolCallback calculator = tool("calculator");

    @Test
    void noAllowlistMakesEveryRegisteredToolAvailable() {
        AgentToolSelector selector = new AgentToolSelector(List.of(search, calculator));

        assertThat(selector.selectFor(agentNode())).containsExactly(search, calculator);
    }

    @Test
    void selectsToolsInRegistrationOrderUsingNormalizedNames() {
        AgentToolSelector selector = new AgentToolSelector(List.of(search, calculator));
        NodeDef node = agentNode().with("tools", List.of(" calculator ", "missing", "calculator"));

        assertThat(selector.selectFor(node)).containsExactly(calculator);
    }

    @Test
    void blankAllowlistEntriesPreserveDefaultAllToolsBehavior() {
        AgentToolSelector selector = new AgentToolSelector(List.of(search, calculator));
        NodeDef node = agentNode().with("tools", List.of(" ", ""));

        assertThat(selector.selectFor(node)).containsExactly(search, calculator);
    }

    @Test
    void unreadableToolMetadataIsSkippedWithoutBreakingOtherTools() {
        ToolCallback brokenTool = mock(ToolCallback.class);
        when(brokenTool.getToolDefinition()).thenThrow(new IllegalStateException("definition unavailable"));
        AgentToolSelector selector = new AgentToolSelector(List.of(brokenTool, calculator));
        NodeDef node = agentNode().with("tools", List.of("calculator"));

        assertThat(selector.selectFor(node)).containsExactly(calculator);
    }

    private static ToolCallback tool(String name) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    private static NodeDef agentNode() {
        return NodeDef.of("agent", NodeType.AGENT);
    }
}
