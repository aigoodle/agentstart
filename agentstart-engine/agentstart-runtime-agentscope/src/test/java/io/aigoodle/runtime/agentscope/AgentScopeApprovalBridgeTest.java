package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentScopeApprovalBridgeTest {
    @Test
    void preservesExactCallsAndMixedDecisionsAcrossPersistedApprovalProtocol() {
        var pending = AgentScopeApprovalBridge.pending(new RequireUserConfirmEvent(
                "reply",
                List.of(
                        new ToolUseBlock("first", "echo", Map.of("value", 42)),
                        new ToolUseBlock("second", "send", Map.of("text", "hello")))));
        var persisted = io.aigoodle.common.util.JsonUtils.parseList(
                io.aigoodle.common.util.JsonUtils.toJson(pending),
                io.aigoodle.agent.api.AgentResponse.PendingApproval.class);
        var message = AgentScopeApprovalBridge.confirmation(
                persisted,
                new AgentResumeCommand(
                        null,
                        null,
                        Map.of(
                                "first",
                                AgentResumeCommand.Decision.APPROVE,
                                "second",
                                AgentResumeCommand.Decision.DENY)));
        var results = (List<?>) message.getMetadata().get(Msg.METADATA_CONFIRM_RESULTS);
        var first = (ConfirmResult) results.getFirst();
        assertTrue(first.isConfirmed());
        assertEquals("first", first.getToolCall().getId());
        assertEquals(Map.of("value", 42), first.getToolCall().getInput());
        assertFalse(((ConfirmResult) results.get(1)).isConfirmed());
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentScopeApprovalBridge.confirmation(
                        persisted, new AgentResumeCommand("unknown", AgentResumeCommand.Decision.APPROVE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentScopeApprovalBridge.confirmation(
                        persisted, new AgentResumeCommand("first", AgentResumeCommand.Decision.APPROVE)));
    }
}
