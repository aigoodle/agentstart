package io.aigoodle.web.controller;

import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.service.AgentExecutionService;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.web.dto.ChatRequest;
import io.aigoodle.web.support.AgentRequestMapper;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synchronous conversation endpoints for agent applications. The SSE streaming
 * chat endpoint lives in {@link AgentChatStreamController} — it is servlet-only
 * and skipped on reactive hosts.
 */
@RestController
@ConditionalOnBean(AgentExecutionService.class)
@RequestMapping("/agents")
public class AgentChatController {

    private final AgentExecutionService executions;
    private final ChannelAdministrationPolicy administration;

    public AgentChatController(AgentExecutionService executions, ChannelAdministrationPolicy administration) {
        this.executions = executions;
        this.administration = administration;
    }

    @GetMapping("/{id}/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessage>> history(
            @PathVariable String id, @PathVariable String conversationId, @RequestParam(defaultValue = "100") int max) {
        return ApiResponse.ok(executions.history(
                io.aigoodle.common.context.UserContextHolder.currentTenantId(), id, conversationId, max));
    }

    @PostMapping("/{id}/chat")
    public ApiResponse<AgentResponse> chat(@PathVariable String id, @RequestBody ChatRequest request) {
        return ApiResponse.ok(executions.runPublished(
                io.aigoodle.common.context.UserContextHolder.currentTenantId(),
                id,
                null,
                AgentRequestMapper.from(request),
                null,
                null));
    }

    @PostMapping("/{id}/chat/preview")
    public ApiResponse<AgentResponse> preview(@PathVariable String id, @RequestBody ChatRequest request) {
        administration.requireAdministrator();
        return ApiResponse.ok(executions.previewDraft(
                io.aigoodle.common.context.UserContextHolder.currentTenantId(),
                id,
                AgentRequestMapper.from(request),
                null,
                null));
    }
}
