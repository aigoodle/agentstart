package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(ChannelAuditService.class)
@RequestMapping("/channel-audits")
public class ChannelAuditController {
    private final ChannelAuditService audits;
    private final ChannelAdministrationPolicy policy;

    public ChannelAuditController(ChannelAuditService audits, ChannelAdministrationPolicy policy) {
        this.audits = audits;
        this.policy = policy;
    }

    @GetMapping
    public ApiResponse<List<ChannelAuditService.View>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "100") int limit) {
        policy.requireAdministrator();
        return ApiResponse.ok(
                audits.list(currentTenantId(), action, resourceType, resourceId, actorId, outcome, limit));
    }
}
