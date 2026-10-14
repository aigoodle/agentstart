package io.aigoodle.agent.runtime;

import java.util.EnumSet;
import java.util.Set;

/** Durable lifecycle of one agent execution. */
public enum AgentRunStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    MAX_ITERATIONS;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, FAILED, CANCELLED, TIMED_OUT, MAX_ITERATIONS -> true;
            default -> false;
        };
    }

    public boolean canTransitionTo(AgentRunStatus target) {
        if (target == null || this == target || isTerminal()) {
            return false;
        }
        return switch (this) {
            case CREATED -> EnumSet.of(RUNNING, CANCELLED).contains(target);
            case RUNNING -> EnumSet.of(WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, TIMED_OUT, MAX_ITERATIONS)
                    .contains(target);
            case WAITING_APPROVAL -> EnumSet.of(RUNNING, FAILED, CANCELLED, TIMED_OUT)
                    .contains(target);
            default -> false;
        };
    }

    public Set<AgentRunStatus> nextStatuses() {
        return EnumSet.allOf(AgentRunStatus.class).stream()
                .filter(this::canTransitionTo)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
