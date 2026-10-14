package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interprets and executes the optional tool call produced for a planned step. */
final class PlannedStepExecutor {

    private static final Pattern ACTION = Pattern.compile("Action\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_INPUT =
            Pattern.compile("Action\\s*Input\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    String execute(
            String modelOutput, Map<String, ToolDefinition> tools, AgentResponse response, AgentRunContext context) {
        Matcher actionMatch = ACTION.matcher(modelOutput);
        if (!actionMatch.find()) {
            return modelOutput.strip();
        }

        String toolName = firstLine(actionMatch.group(1));
        Matcher inputMatch = ACTION_INPUT.matcher(modelOutput);
        String argumentsJson = inputMatch.find() ? inputMatch.group(1).strip() : "{}";
        recordAction(response, context, toolName, argumentsJson);

        String observation = invokeTool(tools.get(toolName), toolName, argumentsJson, context);
        recordObservation(response, context, observation);
        return observation;
    }

    private static String invokeTool(
            ToolDefinition tool, String toolName, String argumentsJson, AgentRunContext context) {
        if (tool == null) {
            return "error: unknown tool '" + toolName + "'";
        }
        try {
            Map<String, Object> arguments = JsonUtils.parseMap(argumentsJson);
            Object result = context.executeTool(tool, arguments == null ? Map.of() : arguments);
            return result == null ? "" : String.valueOf(result);
        } catch (Exception exception) {
            return "error: " + exception.getMessage();
        }
    }

    private static void recordAction(
            AgentResponse response, AgentRunContext context, String toolName, String argumentsJson) {
        AgentStep action = AgentStep.action(toolName, argumentsJson);
        response.addStep(action);
        context.publishStep(action);
    }

    private static void recordObservation(AgentResponse response, AgentRunContext context, String observationText) {
        AgentStep observation = AgentStep.observation(observationText);
        response.addStep(observation);
        context.publishStep(observation);
    }

    private static String firstLine(String text) {
        String strippedText = text.strip();
        int newline = strippedText.indexOf('\n');
        return (newline >= 0 ? strippedText.substring(0, newline) : strippedText).strip();
    }
}
