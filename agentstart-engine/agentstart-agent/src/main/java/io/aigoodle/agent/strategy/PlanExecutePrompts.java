package io.aigoodle.agent.strategy;

import io.aigoodle.tool.ToolDefinition;
import org.springframework.ai.chat.messages.SystemMessage;

/** Keeps the plan-and-execute model contract separate from runtime orchestration. */
final class PlanExecutePrompts {

    private PlanExecutePrompts() {}

    static String planning(String task) {
        return task + "\n\nFirst, produce a plan as a JSON array of short step strings. "
                + "Output ONLY the JSON array.";
    }

    static String executeStep(String step) {
        return "Execute step: " + step
                + "\nIf a tool is needed, reply with:\n"
                + "Action: <tool name>\n"
                + "Action Input: <JSON args>\n"
                + "Otherwise reply with the result text directly.";
    }

    static String synthesize(String task, CharSequence stepResults) {
        return "Task: " + task + "\n\nStep results:\n" + stepResults + "\nProduce the final answer for the user.";
    }

    static SystemMessage system(AgentRunContext context, Iterable<ToolDefinition> tools) {
        String instructions = context.getDefinition().getInstructions();
        StringBuilder prompt =
                new StringBuilder(instructions == null ? "You are a capable planning agent." : instructions);
        prompt.append("\n\nAvailable tools:\n");
        for (ToolDefinition tool : tools) {
            prompt.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append('\n');
        }
        return new SystemMessage(prompt.toString());
    }
}
