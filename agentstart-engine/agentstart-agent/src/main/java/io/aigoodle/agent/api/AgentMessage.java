package io.aigoodle.agent.api;

/**
 * One conversational message used by memory and strategies.
 */
public record AgentMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    public static AgentMessage user(String content) {
        return new AgentMessage(Role.USER, content);
    }

    public static AgentMessage assistant(String content) {
        return new AgentMessage(Role.ASSISTANT, content);
    }

    public static AgentMessage system(String content) {
        return new AgentMessage(Role.SYSTEM, content);
    }
}
