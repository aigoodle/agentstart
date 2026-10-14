package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentCheckpoint;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Runs the model-independent ReAct reasoning loop.
 *
 * <p>The strategy owns iteration state, approval decisions and tool execution.
 * Streaming protocol classification is delegated to {@link ReActStreamingResponse}
 * so the main loop reads in the same order as the conversation it implements.</p>
 */
public class ReActStrategy implements ResumableAgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReActStrategy.class);

    private static final Pattern ACTION_PATTERN = Pattern.compile("Action\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_INPUT_PATTERN = Pattern.compile(
            "Action\\s*Input\\s*:\\s*(.+?)(?:\\nObservation:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("Final\\s*Answer\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("Thought\\s*:\\s*(.+?)(?:\\nAction:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public AgentStrategyType type() {
        return AgentStrategyType.REACT;
    }

    @Override
    public AgentResponse run(AgentRunContext context) {
        AgentDefinition definition = context.getDefinition();
        AgentResponse response = newResponse(context);
        boolean hideThought = thinkingIsDisabled(definition);
        List<Message> messages = createConversation(context, definition, hideThought);
        return runLoop(context, response, messages, 1, hideThought);
    }

    @Override
    public AgentResponse resume(AgentRunContext context, AgentResponse paused, AgentResumeCommand command) {
        if (paused.getStatus() != AgentResponse.Status.AWAITING_APPROVAL
                || paused.getPendingApproval() == null
                || paused.getCheckpoint() == null) {
            throw new IllegalArgumentException("Agent response has no resumable approval checkpoint");
        }
        if (!paused.getPendingApproval().getApprovalId().equals(command.approvalId())) {
            throw new IllegalArgumentException("Approval id does not match the paused checkpoint");
        }
        ReActCheckpoint checkpoint = JsonUtils.parse(paused.getCheckpoint().stateJson(), ReActCheckpoint.class);
        List<Message> messages = checkpoint.messages().stream()
                .map(ReActStrategy::toSpringMessage)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<String, ToolDefinition> toolsByName = indexTools(context.getTools());
        ToolDefinition tool = toolsByName.get(checkpoint.toolName());
        String observation;
        if (command.decision() == AgentResumeCommand.Decision.DENY) {
            observation = "Tool '" + checkpoint.toolName() + "' was denied by the approver.";
        } else if (tool == null) {
            observation = "error: unknown tool '" + checkpoint.toolName() + "'. Available: " + toolsByName.keySet();
        } else {
            observation = invoke(tool, checkpoint.toolInput(), context);
        }
        paused.setPendingApproval(null);
        paused.setCheckpoint(null);
        recordObservation(paused, observation, context);
        messages.add(new UserMessage("Observation: " + observation));
        return runLoop(
                context, paused, messages, checkpoint.iteration() + 1, thinkingIsDisabled(context.getDefinition()));
    }

    private AgentResponse runLoop(
            AgentRunContext context,
            AgentResponse response,
            List<Message> messages,
            int firstIteration,
            boolean hideThought) {
        AgentDefinition definition = context.getDefinition();
        Map<String, ToolDefinition> toolsByName = indexTools(context.getTools());
        ChatOptions chatOptions = AgentChatOptionsFactory.build(definition);

        for (int iteration = firstIteration; iteration <= definition.getMaxIterations(); iteration++) {
            context.checkActive();
            response.setIterations(iteration);
            String modelOutput = generateModelOutput(context, messages, chatOptions, hideThought);
            log.debug("ReAct iteration {} output: {}", iteration, modelOutput);

            String finalAnswer = extractOptional(FINAL_ANSWER_PATTERN, modelOutput);
            if (finalAnswer != null) {
                return complete(response, finalAnswer, context);
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(modelOutput);
            if (!actionMatcher.find()) {
                String directAnswer = hideThought
                        ? ReActStreamingResponse.stripThoughtPreamble(modelOutput)
                                .strip()
                        : modelOutput.strip();
                return complete(response, directAnswer, context);
            }

            String toolName = firstLine(actionMatcher.group(1));
            String toolInput = extractOrDefault(ACTION_INPUT_PATTERN, modelOutput, "{}");
            recordAction(response, modelOutput, toolName, toolInput, context);

            ToolExecution execution = executeTool(toolsByName, toolName, toolInput, definition, context);
            if (execution.awaitingApproval()) {
                messages.add(new AssistantMessage(modelOutput));
                return awaitApproval(response, toolName, toolInput, iteration, messages, context);
            }

            recordObservation(response, execution.observation(), context);
            messages.add(new AssistantMessage(modelOutput));
            messages.add(new UserMessage("Observation: " + execution.observation()));
        }

        return response.stopAfterMaxIterations(definition.getMaxIterations());
    }

    private static Map<String, ToolDefinition> indexTools(List<ToolDefinition> tools) {
        Map<String, ToolDefinition> toolsByName = new LinkedHashMap<>();
        for (ToolDefinition tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
        return toolsByName;
    }

    private static AgentResponse newResponse(AgentRunContext context) {
        return AgentResponse.forConversation(context.getConversationId());
    }

    private static boolean thinkingIsDisabled(AgentDefinition definition) {
        return "disabled".equals(AgentChatOptionsFactory.resolveThinkingMode(definition));
    }

    private static List<Message> createConversation(
            AgentRunContext context, AgentDefinition definition, boolean hideThought) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(createSystemPrompt(definition, context.getTools(), hideThought)));
        for (AgentMessage historyMessage : context.getHistory()) {
            messages.add(toSpringMessage(historyMessage));
        }
        messages.add(new UserMessage(context.getQuery()));
        return messages;
    }

    private static String generateModelOutput(
            AgentRunContext context, List<Message> messages, ChatOptions chatOptions, boolean hideThought) {
        context.checkActive();
        context.claimModelCall();
        var request = context.getChatClient().prompt().messages(messages);
        if (chatOptions != null) {
            request = request.options(chatOptions.mutate());
        }
        return ReActStreamingResponse.generate(context, request, hideThought);
    }

    private static AgentResponse complete(AgentResponse response, String answer, AgentRunContext context) {
        AgentStep finalStep = AgentStep.of(AgentStep.Kind.FINAL, answer);
        response.addStep(finalStep);
        context.publishStep(finalStep);
        return response.complete(answer);
    }

    private static ToolExecution executeTool(
            Map<String, ToolDefinition> toolsByName,
            String toolName,
            String toolInput,
            AgentDefinition definition,
            AgentRunContext context) {
        ToolDefinition tool = toolsByName.get(toolName);
        if (tool == null) {
            return ToolExecution.completed(
                    "error: unknown tool '" + toolName + "'. Available: " + toolsByName.keySet());
        }
        if (!definition.getApprovalRequiredTools().contains(toolName)) {
            return ToolExecution.completed(invoke(tool, toolInput, context));
        }

        ApprovalGate.Decision decision = context.getApprovalGate()
                .review(new ApprovalGate.ToolCall(
                        definition.getId(), context.getConversationId(), toolName, toolInput));
        if (decision == null) {
            decision = ApprovalGate.Decision.DENY;
        }
        return switch (decision) {
            case PENDING -> ToolExecution.pendingApproval();
            case DENY -> ToolExecution.completed("Tool '" + toolName + "' was denied by the approver.");
            case APPROVE -> ToolExecution.completed(invoke(tool, toolInput, context));
        };
    }

    private static String invoke(ToolDefinition tool, String toolInput, AgentRunContext context) {
        try {
            Map<String, Object> arguments = JsonUtils.parseMap(toolInput);
            if (arguments == null || arguments.isEmpty()) {
                arguments = Map.of("input", toolInput);
            }
            Object result = context.executeTool(tool, arguments);
            return result == null ? "" : String.valueOf(result);
        } catch (Exception exception) {
            return "error: " + exception.getMessage();
        }
    }

    private static void recordAction(
            AgentResponse response, String modelOutput, String toolName, String toolInput, AgentRunContext context) {
        String thought = null;
        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(modelOutput);
        if (thoughtMatcher.find()) {
            thought = thoughtMatcher.group(1).strip();
        }
        AgentStep actionStep = AgentStep.action(toolName, toolInput, thought);
        response.addStep(actionStep);
        context.publishStep(actionStep);
    }

    private static void recordObservation(AgentResponse response, String observation, AgentRunContext context) {
        AgentStep observationStep = AgentStep.observation(observation);
        response.addStep(observationStep);
        context.publishStep(observationStep);
    }

    private static AgentResponse awaitApproval(
            AgentResponse response,
            String toolName,
            String toolInput,
            int iteration,
            List<Message> messages,
            AgentRunContext context) {
        AgentResponse.PendingApproval pendingApproval =
                AgentResponse.PendingApproval.forTool(UUID.randomUUID().toString(), toolName, toolInput);
        response.awaitApproval(pendingApproval);
        ReActCheckpoint checkpoint = new ReActCheckpoint(
                iteration, messages.stream().map(ReActStrategy::toAgentMessage).toList(), toolName, toolInput);
        response.setCheckpoint(new AgentCheckpoint(AgentStrategyType.REACT.name(), JsonUtils.toJson(checkpoint)));

        AgentStep approvalStep = AgentStep.of(AgentStep.Kind.APPROVAL, "awaiting approval for tool '" + toolName + "'");
        response.addStep(approvalStep);
        context.publishStep(approvalStep);
        return response;
    }

    private static String createSystemPrompt(
            AgentDefinition definition, List<ToolDefinition> tools, boolean hideThought) {
        String instructions =
                definition.getInstructions() == null ? "You are a helpful assistant." : definition.getInstructions();
        StringBuilder prompt = new StringBuilder(instructions);
        if (tools.isEmpty()) {
            return prompt.append("\n\nRespond directly to the user. Do NOT emit any ")
                    .append("\"Thought:\", \"Action:\" or \"Final Answer:\" prefix.")
                    .toString();
        }

        prompt.append("\n\nYou have access to these tools:\n");
        StringBuilder toolNames = new StringBuilder();
        for (ToolDefinition tool : tools) {
            prompt.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append('\n');
            if (!toolNames.isEmpty()) {
                toolNames.append(", ");
            }
            toolNames.append(tool.name());
        }
        appendResponseFormat(prompt, toolNames, hideThought);
        return prompt.toString();
    }

    private static void appendResponseFormat(StringBuilder prompt, StringBuilder toolNames, boolean hideThought) {
        if (hideThought) {
            prompt.append("\nIMPORTANT: If your response does NOT require calling any tool, ")
                    .append("respond directly to the user as plain text — do NOT emit any ")
                    .append("\"Thought:\", \"Action:\" or \"Final Answer:\" prefix.\n\n")
                    .append("Only when a tool call is genuinely needed, use this format:\n")
                    .append("Action: one of [")
                    .append(toolNames)
                    .append("]\n")
                    .append("Action Input: a compact JSON object of arguments\n")
                    .append("Observation: (the tool result is provided to you)\n")
                    .append("... repeat as needed ...\n")
                    .append("Then answer the user directly as plain text.");
            return;
        }
        prompt.append("\nUse EXACTLY this format:\n")
                .append("Thought: your reasoning\n")
                .append("Action: one of [")
                .append(toolNames)
                .append("]\n")
                .append("Action Input: a compact JSON object of arguments\n")
                .append("Observation: (the tool result is provided to you)\n")
                .append("... repeat as needed ...\n")
                .append("When you can answer, reply:\n")
                .append("Thought: I now know the final answer\n")
                .append("Final Answer: the answer for the user");
    }

    private static Message toSpringMessage(AgentMessage message) {
        return switch (message.role()) {
            case ASSISTANT -> new AssistantMessage(message.content());
            case SYSTEM -> new SystemMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }

    private static AgentMessage toAgentMessage(Message message) {
        if (message instanceof AssistantMessage) {
            return AgentMessage.assistant(message.getText());
        }
        if (message instanceof SystemMessage) {
            return AgentMessage.system(message.getText());
        }
        return AgentMessage.user(message.getText());
    }

    private static String firstLine(String value) {
        String strippedValue = value.strip();
        int lineBreak = strippedValue.indexOf('\n');
        return (lineBreak >= 0 ? strippedValue.substring(0, lineBreak) : strippedValue).strip();
    }

    private static String extractOptional(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    private static String extractOrDefault(Pattern pattern, String content, String defaultValue) {
        String extracted = extractOptional(pattern, content);
        return extracted == null ? defaultValue : extracted;
    }

    private record ToolExecution(String observation, boolean awaitingApproval) {

        private static ToolExecution completed(String observation) {
            return new ToolExecution(observation, false);
        }

        private static ToolExecution pendingApproval() {
            return new ToolExecution(null, true);
        }
    }

    private record ReActCheckpoint(int iteration, List<AgentMessage> messages, String toolName, String toolInput) {}
}
