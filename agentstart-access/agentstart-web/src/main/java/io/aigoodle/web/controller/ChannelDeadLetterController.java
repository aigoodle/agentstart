package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelEventLogService;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-admin operations for terminal outbound failures. */
@RestController
@ConditionalOnBean(ChannelEventLogService.class)
@RequestMapping("/channel-dead-letters")
public class ChannelDeadLetterController {
    public record ReplayRequest(List<String> eventIds) {}

    private final ChannelEventLogService events;
    private final ChannelAdministrationPolicy administration;
    private final ChannelAuditService audits;

    public ChannelDeadLetterController(
            ChannelEventLogService events, ChannelAdministrationPolicy administration, ChannelAuditService audits) {
        this.events = events;
        this.administration = administration;
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<List<ChannelEventLogService.View>> list(@RequestParam(defaultValue = "100") int limit) {
        administration.requireAdministrator();
        return ApiResponse.ok(events.deadLetters(currentTenantId(), limit));
    }

    @PostMapping("/replay")
    public ApiResponse<ChannelEventLogService.DeadLetterReplayResult> replay(@RequestBody ReplayRequest request) {
        administration.requireAdministrator();
        var result = events.replayDeadLetters(currentTenantId(), request == null ? null : request.eventIds());
        audits.success(
                "CHANNEL_DEAD_LETTER_REPLAY",
                "CHANNEL_EVENT_BATCH",
                "dead-letters",
                Map.of("requested", result.requested(), "eligible", result.eligible(), "requeued", result.requeued()));
        return ApiResponse.ok(result);
    }
}
