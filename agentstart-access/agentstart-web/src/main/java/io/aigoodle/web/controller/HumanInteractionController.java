package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.workflow.service.HumanInteractionService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/** A single interaction API shared by chat UI, public web forms and channel callbacks. */
@RestController
@ConditionalOnBean(HumanInteractionService.class)
public class HumanInteractionController {
    private final HumanInteractionService service;

    public HumanInteractionController(HumanInteractionService service) {
        this.service = service;
    }

    @GetMapping("/public/human-interactions/{accessToken}")
    public ApiResponse<Map<String, Object>> publicForm(@PathVariable String accessToken) {
        return ApiResponse.ok(service.requirePublic(accessToken));
    }

    @PostMapping("/public/human-interactions/{accessToken}/submit")
    public ApiResponse<HumanInteractionService.Submission> submit(
            @PathVariable String accessToken, @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> values =
                request.get("values") instanceof Map<?, ?> map ? (Map<String, Object>) map : request;
        String text = request.get("submittedText") == null ? null : String.valueOf(request.get("submittedText"));
        return ApiResponse.ok(service.submitPublic(accessToken, values, text));
    }

    @GetMapping("/conversations/{conversationId}/human-interactions")
    public ApiResponse<List<Map<String, Object>>> conversation(@PathVariable String conversationId) {
        return ApiResponse.ok(service.conversation(currentTenantId(), conversationId));
    }
}
