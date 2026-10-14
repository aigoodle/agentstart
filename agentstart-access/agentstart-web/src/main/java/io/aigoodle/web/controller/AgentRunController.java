package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import io.aigoodle.agent.runtime.AgentRunEvent;
import io.aigoodle.agent.runtime.AgentRunSnapshot;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only execution inspection API shared by consoles and embedded UI components. */
@RestController
@ConditionalOnBean(AgentRuntimeRegistry.class)
@RequestMapping("/agent-runs")
public class AgentRunController {
    private static final int DEFAULT_EVENT_LIMIT = 200;

    private final AgentRuntimeRegistry agentRuntime;
    private final ChannelAdministrationPolicy administration;
    private final ChannelAuditService audits;

    public AgentRunController(
            AgentRuntimeRegistry agentRuntime, ChannelAdministrationPolicy administration, ChannelAuditService audits) {
        this.agentRuntime = agentRuntime;
        this.administration = administration;
        this.audits = audits;
    }

    @GetMapping("/{runId}")
    public ApiResponse<AgentRunSnapshot> get(@PathVariable String runId) {
        return ApiResponse.ok(requireOwned(runId));
    }

    @GetMapping("/{runId}/events")
    public ApiResponse<List<AgentRunEvent>> events(
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(agentRuntime.runEventsForTenant(
                currentTenantId(), runId, Math.max(0, afterSequence), Math.min(1000, Math.max(1, limit))));
    }

    @PostMapping("/{runId}/resume")
    public ApiResponse<AgentResponse> resume(@PathVariable String runId, @RequestBody AgentResumeCommand command) {
        administration.requireAdministrator();
        AgentResponse response = agentRuntime.resumeForTenant(currentTenantId(), runId, command);
        long approved = command.allDecisions().values().stream()
                .filter(value -> value == AgentResumeCommand.Decision.APPROVE)
                .count();
        audits.success(
                "AGENT_RUN_RESUME",
                "AGENT_RUN",
                runId,
                Map.of(
                        "decisionCount",
                        command.allDecisions().size(),
                        "approvedCount",
                        approved,
                        "rejectedCount",
                        command.allDecisions().size() - approved,
                        "resultStatus",
                        response.getStatus().name()));
        return ApiResponse.ok(response);
    }

    @PostMapping("/{runId}/cancel")
    @Transactional
    public ApiResponse<AgentRunSnapshot> cancel(@PathVariable String runId) {
        administration.requireAdministrator();
        AgentRunSnapshot cancelled = agentRuntime.cancelForTenant(currentTenantId(), runId);
        audits.success(
                "AGENT_RUN_CANCEL",
                "AGENT_RUN",
                runId,
                Map.of("resultStatus", cancelled.status().name()));
        return ApiResponse.ok(cancelled);
    }

    private AgentRunSnapshot requireOwned(String runId) {
        return agentRuntime
                .findRun(currentTenantId(), runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
    }
}
