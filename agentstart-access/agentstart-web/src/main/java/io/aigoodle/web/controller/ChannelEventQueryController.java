package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;
import static io.aigoodle.common.context.UserContextHolder.currentUserId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAttachment;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelEventLogService;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Message observation API for Connector Center. */
@RestController
@ConditionalOnBean(ChannelEventLogService.class)
@RequestMapping("/channel-events")
public class ChannelEventQueryController {
    public record ReplyRequest(
            String content,
            String messageType,
            List<ChannelAttachment> attachments,
            java.util.Map<String, Object> contentPayload,
            String idempotencyKey) {}

    private final ChannelEventLogService events;
    private final ChannelAuditService audits;

    public ChannelEventQueryController(ChannelEventLogService events) {
        this(events, null);
    }

    @Autowired
    public ChannelEventQueryController(ChannelEventLogService events, ChannelAuditService audits) {
        this.events = events;
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<List<ChannelEventLogService.View>> list(
            @RequestParam(required = false) String connectionId, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(events.list(currentTenantId(), connectionId, limit));
    }

    @GetMapping("/page")
    public ApiResponse<ChannelEventLogService.Page> page(
            @RequestParam(required = false) String connectionId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(events.page(currentTenantId(), connectionId, conversationId, cursor, limit));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<ChannelEventLogService.View> retry(@PathVariable String id) {
        ChannelEventLogService.View result = events.retry(currentTenantId(), id);
        audit("CHANNEL_MESSAGE_RETRY", id, java.util.Map.of());
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/handoff")
    public ApiResponse<ChannelEventLogService.View> handoff(
            @PathVariable String id, @RequestBody(required = false) java.util.Map<String, String> body) {
        ChannelEventLogService.View result = events.handoff(
                currentTenantId(),
                id,
                body == null ? null : body.get("note"),
                body == null ? null : body.get("assignmentGroup"));
        audit("CHANNEL_MESSAGE_HANDOFF", id, java.util.Map.of());
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<ChannelEventLogService.View> reply(@PathVariable String id, @RequestBody ReplyRequest body) {
        String senderType = "EMPLOYEE";
        io.aigoodle.common.context.CurrentUser user = io.aigoodle.common.context.UserContextHolder.get();
        Set<String> roles = user == null ? null : user.getRoles();
        if (roles != null && roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role))) senderType = "ADMIN";
        ChannelEventLogService.View result = events.manualReply(
                currentTenantId(),
                id,
                body == null ? null : body.content(),
                body == null ? null : body.messageType(),
                body == null ? List.of() : body.attachments(),
                body == null ? java.util.Map.of() : body.contentPayload(),
                body == null ? null : body.idempotencyKey(),
                senderType,
                currentUserId());
        audit("CHANNEL_MESSAGE_REPLY", result == null ? id : result.id(), java.util.Map.of("replyToEventId", id));
        return ApiResponse.ok(result);
    }

    private void audit(String action, String id, java.util.Map<String, Object> details) {
        if (audits != null) audits.success(action, "CHANNEL_EVENT", id, details);
    }
}
