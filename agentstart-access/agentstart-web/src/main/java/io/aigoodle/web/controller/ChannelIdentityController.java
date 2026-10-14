package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnBean(ChannelIdentityService.class)
@RequestMapping("/channel-identities")
public class ChannelIdentityController {
    public record SaveRequest(
            String provider,
            String channelId,
            String accountId,
            String externalUserId,
            String enterpriseUserId,
            String verificationStatus,
            Boolean enabled) {}

    private final ChannelIdentityService identities;
    private final ChannelAdministrationPolicy administration;
    private final ChannelAuditService audits;

    public ChannelIdentityController(
            ChannelIdentityService identities, ChannelAdministrationPolicy administration, ChannelAuditService audits) {
        this.identities = identities;
        this.administration = administration;
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<List<ChannelIdentityService.Identity>> list() {
        return ApiResponse.ok(identities.list(currentTenantId()));
    }

    @PutMapping
    public ApiResponse<ChannelIdentityService.Identity> save(@RequestBody SaveRequest r) {
        administration.requireAdministrator();
        ChannelIdentityService.Identity saved = identities.save(
                currentTenantId(),
                r.provider(),
                r.channelId(),
                r.accountId(),
                r.externalUserId(),
                r.enterpriseUserId(),
                r.verificationStatus(),
                !Boolean.FALSE.equals(r.enabled()));
        audits.success("CHANNEL_IDENTITY_SAVE", "CHANNEL_IDENTITY", saved.id(), details(saved));
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        administration.requireAdministrator();
        identities.delete(currentTenantId(), id);
        audits.success("CHANNEL_IDENTITY_DELETE", "CHANNEL_IDENTITY", id, Map.of());
        return ApiResponse.ok(null);
    }

    private static Map<String, Object> details(ChannelIdentityService.Identity identity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", identity.provider());
        details.put("channelId", identity.channelId());
        details.put("accountId", identity.accountId());
        details.put("externalUserId", identity.externalUserId());
        details.put("enterpriseUserId", identity.enterpriseUserId());
        details.put("verificationStatus", identity.verificationStatus());
        details.put("enabled", identity.enabled());
        return details;
    }
}
