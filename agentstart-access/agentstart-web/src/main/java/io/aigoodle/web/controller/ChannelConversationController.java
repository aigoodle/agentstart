package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.*;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelConversationService;
import io.aigoodle.channel.ChannelEventLogService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/** Queue-oriented customer-service API; all resources are scoped to the trusted tenant. */
@RestController
@ConditionalOnBean(ChannelConversationService.class)
@RequestMapping("/channel-conversations")
public class ChannelConversationController {
    public record VersionRequest(Long expectedVersion) {}

    public record ClaimRequest(Long expectedVersion, String assignmentGroup) {}

    private final ChannelConversationService conversations;
    private final ChannelEventLogService events;
    private final ChannelAuditService audits;

    public ChannelConversationController(
            ChannelConversationService conversations, ChannelEventLogService events, ChannelAuditService audits) {
        this.conversations = conversations;
        this.events = events;
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<List<ChannelConversationService.View>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(defaultValue = "false") boolean slaBreached,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(conversations.list(currentTenantId(), status, assigneeId, slaBreached, limit));
    }

    @GetMapping("/page")
    public ApiResponse<ChannelConversationService.Page> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(defaultValue = "false") boolean slaBreached,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(conversations.page(
                currentTenantId(), status, assigneeId, slaBreached, provider, channelId, cursor, limit));
    }

    @GetMapping("/summary")
    public ApiResponse<ChannelConversationService.Summary> summary() {
        return ApiResponse.ok(conversations.summary(currentTenantId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChannelConversationService.View> get(@PathVariable String id) {
        return ApiResponse.ok(conversations.requireView(currentTenantId(), id));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<ChannelConversationService.View> claim(
            @PathVariable String id, @RequestBody ClaimRequest request) {
        ChannelConversationService.View result = conversations.claim(
                currentTenantId(),
                id,
                requiredVersion(request == null ? null : request.expectedVersion()),
                require().getUserId(),
                currentUsername(),
                request == null ? null : request.assignmentGroup());
        audit("CHANNEL_CONVERSATION_CLAIM", id, Map.of("lockVersion", result.lockVersion()));
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/resume-bot")
    public ApiResponse<ChannelConversationService.View> resumeBot(
            @PathVariable String id, @RequestBody VersionRequest request) {
        ChannelConversationService.View result = conversations.resumeBot(
                currentTenantId(), id, requiredVersion(request == null ? null : request.expectedVersion()));
        audit("CHANNEL_CONVERSATION_RESUME_BOT", id, Map.of());
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ChannelConversationService.View> close(
            @PathVariable String id, @RequestBody VersionRequest request) {
        ChannelConversationService.View result = conversations.close(
                currentTenantId(), id, requiredVersion(request == null ? null : request.expectedVersion()));
        audit("CHANNEL_CONVERSATION_CLOSE", id, Map.of());
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/notes")
    public ApiResponse<ChannelEventLogService.View> note(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        ChannelConversationService.View conversation = conversations.requireView(currentTenantId(), id);
        if (conversation.lastEventId() == null) throw new IllegalArgumentException("conversation has no event");
        ChannelEventLogService.View result = events.internalNote(
                currentTenantId(),
                conversation.lastEventId(),
                body == null ? null : body.get("content"),
                currentUserId());
        audit("CHANNEL_CONVERSATION_NOTE", id, Map.of("eventId", result.id()));
        return ApiResponse.ok(result);
    }

    private void audit(String action, String id, Map<String, Object> details) {
        if (audits != null) audits.success(action, "CHANNEL_CONVERSATION", id, details);
    }

    private static long requiredVersion(Long version) {
        if (version == null || version < 1) throw new IllegalArgumentException("expectedVersion is required");
        return version;
    }
}
