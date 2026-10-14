package io.aigoodle.runtime.agentscope;

import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import io.aigoodle.common.util.JsonUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Converts exact persisted approval checkpoints into SDK confirmation messages. */
final class AgentScopeApprovalBridge {
    private AgentScopeApprovalBridge() {}

    static List<AgentResponse.PendingApproval> pending(RequireUserConfirmEvent event) {
        return event.getToolCalls().stream()
                .map(call -> AgentResponse.PendingApproval.forTool(
                        call.getId(), call.getName(), JsonUtils.toJson(call.getInput())))
                .toList();
    }

    static Msg confirmation(List<AgentResponse.PendingApproval> pending, AgentResumeCommand command) {
        if (pending == null || pending.isEmpty()) throw new IllegalArgumentException("No SDK approvals are pending");
        var ids = pending.stream()
                .map(AgentResponse.PendingApproval::getApprovalId)
                .collect(Collectors.toSet());
        var decisions = command.allDecisions();
        if (ids.size() != pending.size() || !ids.equals(decisions.keySet())) {
            throw new IllegalArgumentException("Decisions must match every pending SDK approval exactly");
        }
        List<ConfirmResult> results = pending.stream()
                .map(approval -> new ConfirmResult(
                        decisions.get(approval.getApprovalId()) == AgentResumeCommand.Decision.APPROVE,
                        ToolUseBlock.builder()
                                .id(approval.getApprovalId())
                                .name(approval.getToolName())
                                .input(SpringAiToolCallAssembler.strictArguments(approval.getToolInput()))
                                .content(approval.getToolInput())
                                .build()))
                .toList();
        return Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text("").build()))
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
    }
}
