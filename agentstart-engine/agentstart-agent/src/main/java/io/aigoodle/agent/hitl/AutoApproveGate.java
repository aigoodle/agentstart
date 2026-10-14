package io.aigoodle.agent.hitl;

/**
 * Default approval policy used when an application has not supplied a
 * human-in-the-loop gate. Applications can replace this bean with a policy
 * that denies calls or returns {@link Decision#PENDING} for human review.
 */
public class AutoApproveGate implements ApprovalGate {

    @Override
    public Decision review(ToolCall toolCall) {
        return Decision.APPROVE;
    }
}
