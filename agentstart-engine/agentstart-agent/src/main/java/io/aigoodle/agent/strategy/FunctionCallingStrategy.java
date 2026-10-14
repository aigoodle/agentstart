package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.api.AgentStrategyType;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;

/**
 * Native tool-calling strategy: hands the tools to the model via Spring AI's
 * tool-calling loop. Sensitive tools are wrapped so the {@link ApprovalGate} can
 * deny/short-circuit them inline (full pause/resume HITL is provided by ReAct).
 */
public class FunctionCallingStrategy implements AgentStrategy {

    private final FunctionCallingToolCallbacks toolCallbacks = new FunctionCallingToolCallbacks();
    private final FunctionCallingChatExchange chatExchange = new FunctionCallingChatExchange();

    @Override
    public AgentStrategyType type() {
        return AgentStrategyType.FUNCTION_CALLING;
    }

    @Override
    public AgentResponse run(AgentRunContext context) {
        context.checkActive();
        AgentResponse response = AgentResponse.forConversation(context.getConversationId());

        List<ToolCallback> callbacks = toolCallbacks.create(context, response);
        String finalAnswer = chatExchange.exchange(context, callbacks);
        context.checkActive();

        response.complete(finalAnswer);
        AgentStep finalStep = AgentStep.of(AgentStep.Kind.FINAL, finalAnswer);
        response.addStep(finalStep);
        context.publishStep(finalStep);
        return response;
    }
}
