package io.aigoodle.workflow.engine;

import java.util.Map;

/** State restored from a durable checkpoint before scheduling resumes. */
public record WorkflowResumeState(
        Map<String, ResumedNode> terminalNodes,
        Map<String, Map<String, Object>> variablePool,
        Map<String, Integer> attempts,
        Map<String, Object> iterationCursors) {
    public WorkflowResumeState {
        terminalNodes = terminalNodes == null ? Map.of() : Map.copyOf(terminalNodes);
        variablePool = variablePool == null ? Map.of() : Map.copyOf(variablePool);
        attempts = attempts == null ? Map.of() : Map.copyOf(attempts);
        iterationCursors = iterationCursors == null ? Map.of() : Map.copyOf(iterationCursors);
    }

    public static WorkflowResumeState empty() {
        return new WorkflowResumeState(Map.of(), Map.of(), Map.of(), Map.of());
    }

    public record ResumedNode(boolean executed, String handle) {}
}
