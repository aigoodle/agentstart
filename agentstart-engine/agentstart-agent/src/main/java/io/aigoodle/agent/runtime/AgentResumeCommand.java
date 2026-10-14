package io.aigoodle.agent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Human decision that resumes a run paused at an approval checkpoint. */
public record AgentResumeCommand(String approvalId, Decision decision, Map<String, Decision> decisions) {
    public enum Decision {
        APPROVE,
        DENY
    }

    public AgentResumeCommand(String approvalId, Decision decision) {
        this(approvalId, decision, null);
    }

    public AgentResumeCommand {
        boolean singular = approvalId != null && !approvalId.isBlank() && decision != null;
        boolean batch = decisions != null
                && !decisions.isEmpty()
                && decisions.entrySet().stream()
                        .allMatch(entry ->
                                entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null);
        if (!singular && !batch) {
            throw new IllegalArgumentException("approvalId/decision or non-empty decisions must be supplied");
        }
        approvalId = singular ? approvalId.trim() : null;
        decisions = batch ? Map.copyOf(new LinkedHashMap<>(decisions)) : Map.of();
    }

    public Map<String, Decision> allDecisions() {
        if (!decisions.isEmpty()) return decisions;
        return Map.of(approvalId, decision);
    }
}
