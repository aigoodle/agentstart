package io.aigoodle.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import io.aigoodle.agent.strategy.AgentRunContext;
import io.aigoodle.agent.strategy.ReActStrategy;
import io.aigoodle.agent.support.ScriptedChatModel;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.builtin.CalculatorTool;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Human-in-the-loop behaviour of the ReAct strategy, tested deterministically with a
 * scripted model and a calculator tool that requires approval.
 */
class HitlTest {

    private AgentRunContext context(ApprovalGate gate) {
        ScriptedChatModel model = new ScriptedChatModel(last -> last.startsWith("Observation:")
                ? "Thought: done\nFinal Answer: "
                        + last.replace("Observation:", "").strip()
                : "Thought: I will compute\nAction: calculator\nAction Input: {\"expression\":\"2+2\"}");
        AgentDefinition def = AgentDefinition.builder()
                .id("a1")
                .name("calc-agent")
                .instructions("You compute.")
                .approvalRequiredTools(Set.of("calculator"))
                .maxIterations(4)
                .build();
        return AgentRunContext.builder()
                .definition(def)
                .query("what is 2+2?")
                .conversationId("c-hitl")
                .history(List.of())
                .chatClient(ChatClient.create(model))
                .tools(List.of(new CalculatorTool()))
                .approvalGate(gate)
                .build();
    }

    @Test
    void deniedToolYieldsDenialObservation() {
        AgentResponse r = new ReActStrategy().run(context(tc -> ApprovalGate.Decision.DENY));
        assertEquals(AgentResponse.Status.COMPLETED, r.getStatus());
        assertTrue(
                r.getText().toLowerCase().contains("denied"),
                "final answer should reflect the denial, was: " + r.getText());
    }

    @Test
    void pendingToolPausesWithAwaitingApproval() {
        AgentResponse r = new ReActStrategy().run(context(tc -> ApprovalGate.Decision.PENDING));
        assertEquals(AgentResponse.Status.AWAITING_APPROVAL, r.getStatus());
        assertNotNull(r.getPendingApproval());
        assertEquals("calculator", r.getPendingApproval().getToolName());
        assertNotNull(r.getPendingApproval().getApprovalId());
    }

    @Test
    void approvedToolExecutesAndAnswers() {
        AgentResponse r = new ReActStrategy().run(context(tc -> ApprovalGate.Decision.APPROVE));
        assertEquals(AgentResponse.Status.COMPLETED, r.getStatus());
        assertTrue(r.getText().contains("4"), "approved calc should yield 4, was: " + r.getText());
    }

    @Test
    void missingApprovalDecisionFailsClosed() {
        AgentResponse response = new ReActStrategy().run(context(toolCall -> null));

        assertEquals(AgentResponse.Status.COMPLETED, response.getStatus());
        assertTrue(response.getText().toLowerCase().contains("denied"));
    }

    @Test
    void resumesFromSerializedCheckpointWithoutRepeatingThePendingModelTurn() {
        ReActStrategy strategy = new ReActStrategy();
        AgentRunContext context = context(tc -> ApprovalGate.Decision.PENDING);
        AgentResponse paused = strategy.run(context);
        AgentResponse restored = JsonUtils.parse(JsonUtils.toJson(paused), AgentResponse.class);

        AgentResponse completed = strategy.resume(
                context,
                restored,
                new AgentResumeCommand(
                        restored.getPendingApproval().getApprovalId(), AgentResumeCommand.Decision.APPROVE));

        assertEquals(AgentResponse.Status.COMPLETED, completed.getStatus());
        assertTrue(completed.getText().contains("4"));
        assertNull(completed.getCheckpoint());
        assertNull(completed.getPendingApproval());
        assertEquals(2, completed.getIterations());
    }

    @Test
    void rejectsAnApprovalForAStaleCheckpoint() {
        ReActStrategy strategy = new ReActStrategy();
        AgentRunContext context = context(tc -> ApprovalGate.Decision.PENDING);
        AgentResponse paused = strategy.run(context);

        assertThrows(
                IllegalArgumentException.class,
                () -> strategy.resume(
                        context,
                        paused,
                        new AgentResumeCommand("another-approval", AgentResumeCommand.Decision.APPROVE)));
    }
}
