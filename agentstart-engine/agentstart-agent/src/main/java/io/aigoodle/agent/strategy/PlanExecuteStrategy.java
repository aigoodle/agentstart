package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Plan-and-execute strategy: the model first decomposes the task into a list of steps,
 * each step is then executed (optionally invoking a tool), and finally the model
 * synthesises an answer from the step results. Better than ReAct for multi-step tasks
 * where an explicit plan helps.
 */
public class PlanExecuteStrategy implements AgentStrategy {

    private final PlanParser planParser = new PlanParser();
    private final PlannedStepExecutor stepExecutor = new PlannedStepExecutor();

    @Override
    public AgentStrategyType type() {
        return AgentStrategyType.PLAN_EXECUTE;
    }

    @Override
    public AgentResponse run(AgentRunContext context) {
        context.checkActive();
        Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        context.getTools().forEach(tool -> tools.put(tool.name(), tool));
        ChatClient chatClient = context.getChatClient();
        org.springframework.ai.chat.prompt.ChatOptions chatOptions =
                AgentChatOptionsFactory.build(context.getDefinition());
        SystemMessage systemMessage = PlanExecutePrompts.system(context, tools.values());
        AgentResponse response = AgentResponse.forConversation(context.getConversationId());

        String planOutput = call(
                context,
                chatClient,
                systemMessage,
                context.getHistory(),
                PlanExecutePrompts.planning(context.getQuery()),
                chatOptions);
        context.checkActive();
        List<String> plannedSteps = planParser.parse(planOutput, context.getQuery());
        recordPlan(response, context, plannedSteps);

        StringBuilder scratchpad = new StringBuilder();
        for (int index = 0; index < plannedSteps.size(); index++) {
            context.checkActive();
            String plannedStep = plannedSteps.get(index);
            String stepOutput = call(
                    context,
                    chatClient,
                    systemMessage,
                    List.of(),
                    PlanExecutePrompts.executeStep(plannedStep),
                    chatOptions);
            String stepResult = stepExecutor.execute(stepOutput, tools, response, context);
            scratchpad
                    .append("Step ")
                    .append(index + 1)
                    .append(": ")
                    .append(plannedStep)
                    .append(" => ")
                    .append(stepResult)
                    .append('\n');
        }

        String finalAnswer = call(
                context,
                chatClient,
                systemMessage,
                List.of(),
                PlanExecutePrompts.synthesize(context.getQuery(), scratchpad),
                chatOptions);
        context.checkActive();
        AgentStep finalStep = AgentStep.of(AgentStep.Kind.FINAL, finalAnswer);
        response.addStep(finalStep);
        context.publishStep(finalStep);
        response.complete(finalAnswer);
        return response;
    }

    private static void recordPlan(AgentResponse response, AgentRunContext context, List<String> plannedSteps) {
        AgentStep planStep = AgentStep.of(AgentStep.Kind.THOUGHT, "Plan: " + plannedSteps);
        response.addStep(planStep);
        context.publishStep(planStep);
    }

    private static String call(
            AgentRunContext context,
            ChatClient client,
            SystemMessage system,
            List<AgentMessage> history,
            String userText,
            org.springframework.ai.chat.prompt.ChatOptions perApp) {
        context.claimModelCall();
        List<Message> messages = new ArrayList<>();
        messages.add(system);
        for (AgentMessage h : history) {
            messages.add(
                    switch (h.role()) {
                        case ASSISTANT -> new AssistantMessage(h.content());
                        case SYSTEM -> new SystemMessage(h.content());
                        default -> new UserMessage(h.content());
                    });
        }
        messages.add(new UserMessage(userText));
        var spec = client.prompt().messages(messages);
        if (perApp != null) {
            spec = spec.options(perApp.mutate());
        }
        String content = spec.call().content();
        return content == null ? "" : content;
    }
}
