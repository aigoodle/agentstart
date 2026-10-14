package io.aigoodle.agent.api;

import lombok.Data;

/** One observable step in an agent's reasoning and tool-execution trace. */
@Data
public class AgentStep {

    public enum Kind {
        THOUGHT,
        ACTION,
        OBSERVATION,
        TOOL_CALL,
        FINAL,
        APPROVAL,
        DELEGATION
    }

    private Kind kind;
    private String thought;
    private String action;
    private String actionInput;
    private String observation;
    private String content;

    public static AgentStep of(Kind kind, String content) {
        AgentStep step = new AgentStep();
        step.kind = kind;
        step.content = content;
        return step;
    }

    public static AgentStep action(String toolName, String toolInput) {
        return action(toolName, toolInput, null);
    }

    public static AgentStep action(String toolName, String toolInput, String thought) {
        AgentStep step = new AgentStep();
        step.kind = Kind.ACTION;
        step.action = toolName;
        step.actionInput = toolInput;
        step.thought = thought;
        return step;
    }

    public static AgentStep observation(String result) {
        AgentStep step = new AgentStep();
        step.kind = Kind.OBSERVATION;
        step.observation = result;
        return step;
    }
}
