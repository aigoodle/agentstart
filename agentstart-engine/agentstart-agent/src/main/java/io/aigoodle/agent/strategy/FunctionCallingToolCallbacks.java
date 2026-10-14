package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.adapter.ToolDefinitionCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;

/** Adapts agent tools for approval checks and observable Spring AI tool calls. */
final class FunctionCallingToolCallbacks {

    List<ToolCallback> create(AgentRunContext context, AgentResponse response) {
        AgentDefinition definition = context.getDefinition();
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolDefinition tool : context.getTools()) {
            ToolDefinition executableTool = requiresApproval(definition, tool)
                    ? new ApprovalGuardedTool(tool, definition, context.getConversationId(), context.getApprovalGate())
                    : tool;
            ToolDefinition observableTool = new StepRecordingTool(executableTool, response, context);
            callbacks.add(new ToolDefinitionCallback(observableTool));
        }
        return callbacks;
    }

    private static boolean requiresApproval(AgentDefinition definition, ToolDefinition tool) {
        return definition.getApprovalRequiredTools() != null
                && definition.getApprovalRequiredTools().contains(tool.name());
    }

    /** Publishes action and observation steps around an internally executed tool call. */
    private record StepRecordingTool(ToolDefinition target, AgentResponse response, AgentRunContext context)
            implements ToolDefinition {

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public String inputSchema() {
            return target.inputSchema();
        }

        @Override
        public Object execute(Map<String, Object> arguments) {
            recordAction(arguments);
            Object result = executeSafely(arguments);
            recordObservation(result);
            return result;
        }

        private Object executeSafely(Map<String, Object> arguments) {
            try {
                return context.executeTool(target, arguments);
            } catch (Exception exception) {
                return "error: " + exception.getMessage();
            }
        }

        private void recordAction(Map<String, Object> arguments) {
            AgentStep actionStep = AgentStep.action(target.name(), String.valueOf(arguments));
            response.addStep(actionStep);
            context.publishStep(actionStep);
        }

        private void recordObservation(Object result) {
            AgentStep observationStep = AgentStep.observation(result == null ? "" : String.valueOf(result));
            response.addStep(observationStep);
            context.publishStep(observationStep);
        }
    }

    /** Consults the configured approval gate before invoking a sensitive tool. */
    private record ApprovalGuardedTool(
            ToolDefinition target, AgentDefinition definition, String conversationId, ApprovalGate approvalGate)
            implements ToolDefinition {

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public String inputSchema() {
            return target.inputSchema();
        }

        @Override
        public Object execute(Map<String, Object> arguments) {
            ApprovalGate.Decision decision = review(arguments);
            if (decision == ApprovalGate.Decision.APPROVE) {
                return target.execute(arguments);
            }
            return "Tool '" + target.name() + "' was not approved (" + decision + ").";
        }

        private ApprovalGate.Decision review(Map<String, Object> arguments) {
            ApprovalGate.Decision decision = approvalGate.review(new ApprovalGate.ToolCall(
                    definition.getId(), conversationId, target.name(), String.valueOf(arguments)));
            return decision == null ? ApprovalGate.Decision.DENY : decision;
        }
    }
}
