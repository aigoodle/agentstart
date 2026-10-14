package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelConnectionService.SaveRequest;
import io.aigoodle.channel.ChannelConnectionService.View;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import io.aigoodle.web.support.ChannelOwnershipPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User/tenant-owned channel connections; secret values are accepted but never returned. */
@RestController
@ConditionalOnBean(ChannelConnectionService.class)
@RequestMapping("/channel-connections")
public class ChannelConnectionController {
    private final ChannelConnectionService connections;
    private final ChannelAuditService audits;
    private final ChannelOwnershipPolicy ownership;
    private final ChannelAdministrationPolicy administration;
    private final AgentVersionService versions;

    public ChannelConnectionController(ChannelConnectionService connections) {
        this(connections, null, null, null, null);
    }

    public ChannelConnectionController(
            ChannelConnectionService connections, ChannelAuditService audits, ChannelOwnershipPolicy ownership) {
        this(connections, audits, ownership, null, null);
    }

    @Autowired
    public ChannelConnectionController(
            ChannelConnectionService connections,
            ChannelAuditService audits,
            ChannelOwnershipPolicy ownership,
            ChannelAdministrationPolicy administration,
            AgentVersionService versions) {
        this.connections = connections;
        this.audits = audits;
        this.ownership = ownership;
        this.administration = administration;
        this.versions = versions;
    }

    @GetMapping
    public ApiResponse<List<View>> list(@RequestParam(required = false) String ownerId) {
        return ApiResponse.ok(connections.list(currentTenantId(), visibleOwner(ownerId)));
    }

    @PostMapping
    public ApiResponse<View> save(@RequestBody SaveRequest request) {
        View current = null;
        if (request.id() == null || request.id().isBlank()) requireAccess(request.ownerType(), request.ownerId());
        else {
            current = connections.get(request.id(), currentTenantId());
            requireAccess(current.ownerType(), current.ownerId());
            requireAccess(request.ownerType(), request.ownerId());
        }
        boolean agentBindingChanged = current == null
                ? text(request.agentId()) != null || text(request.agentVersionId()) != null
                : !Objects.equals(text(current.agentId()), text(request.agentId()))
                        || !Objects.equals(text(current.agentVersionId()), text(request.agentVersionId()));
        if (agentBindingChanged) {
            if (administration == null) throw new SecurityException("connection Agent binding requires administrator");
            administration.requireAdministrator();
            validateAgentReference(request.agentId(), request.agentVersionId());
        }
        SaveRequest trusted = new SaveRequest(
                request.id(),
                currentTenantId(),
                request.ownerType(),
                request.ownerId(),
                request.provider(),
                request.channelId(),
                request.name(),
                request.credentials(),
                request.config(),
                request.enabled(),
                request.agentId(),
                request.agentVersionId(),
                request.runtimeNodeId());
        View saved = connections.save(trusted);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", request.provider());
        details.put("channelId", request.channelId());
        details.put("agentBindingChanged", agentBindingChanged);
        if (text(request.agentId()) != null) details.put("agentId", text(request.agentId()));
        if (text(request.agentVersionId()) != null) details.put("agentVersionId", text(request.agentVersionId()));
        audit("CHANNEL_CONNECTION_SAVE", saved == null ? request.id() : saved.id(), details);
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<View> test(@PathVariable String id) {
        View current = connections.get(id, currentTenantId());
        requireAccess(current.ownerType(), current.ownerId());
        View tested = connections.test(id, currentTenantId());
        audit("CHANNEL_CONNECTION_TEST", id, Map.of());
        return ApiResponse.ok(tested);
    }

    @GetMapping("/{id}/configuration")
    public ApiResponse<ChannelConnectionService.EditConfiguration> editConfiguration(@PathVariable String id) {
        View current = connections.get(id, currentTenantId());
        requireAccess(current.ownerType(), current.ownerId());
        return ApiResponse.ok(connections.editConfiguration(id, currentTenantId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        View current = connections.get(id, currentTenantId());
        requireAccess(current.ownerType(), current.ownerId());
        connections.delete(id, currentTenantId());
        audit("CHANNEL_CONNECTION_DELETE", id, Map.of());
        return ApiResponse.ok(null);
    }

    private void audit(String action, String id, Map<String, Object> details) {
        if (audits != null) audits.success(action, "CHANNEL_CONNECTION", id, details);
    }

    private String visibleOwner(String requested) {
        return ownership == null ? requested : ownership.visibleOwnerId(requested);
    }

    private void requireAccess(String ownerType, String ownerId) {
        if (ownership != null) ownership.requireAccess(ownerType, ownerId);
    }

    private void validateAgentReference(String agentId, String versionId) {
        if (text(agentId) == null) {
            if (text(versionId) != null) throw new IllegalArgumentException("agentId is required with agentVersionId");
            return;
        }
        if (versions == null) throw new IllegalStateException("Agent version service is unavailable");
        versions.requireRunnable(currentTenantId(), text(agentId), text(versionId));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
