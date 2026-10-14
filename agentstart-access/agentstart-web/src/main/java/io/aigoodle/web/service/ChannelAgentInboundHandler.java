package io.aigoodle.web.service;

import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.agent.service.AgentService;
import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.channel.ChannelAgentRouter;
import io.aigoodle.channel.ChannelConversationService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundHandler;
import io.aigoodle.channel.ChannelInboundResult;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.common.context.UserContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Routes a bound channel account to the selected persisted agent application. */
@Component
@ConditionalOnBean({AgentService.class, ChannelAgentRouter.class})
public class ChannelAgentInboundHandler implements ChannelInboundHandler {
    private final ChannelAgentRouter router;
    private final AgentService agents;
    private final ChannelIdentityService identities;
    private final AgentVersionService versions;
    private final ChannelConversationService conversations;
    private final AgentRuntimeRegistry runtimes;

    public ChannelAgentInboundHandler(
            ChannelAgentRouter router, AgentService agents, ChannelIdentityService identities) {
        this(router, agents, identities, null, null, null);
    }

    public ChannelAgentInboundHandler(
            ChannelAgentRouter router,
            AgentService agents,
            ChannelIdentityService identities,
            AgentVersionService versions) {
        this(router, agents, identities, versions, null, null);
    }

    public ChannelAgentInboundHandler(
            ChannelAgentRouter router,
            AgentService agents,
            ChannelIdentityService identities,
            AgentVersionService versions,
            ChannelConversationService conversations) {
        this(router, agents, identities, versions, conversations, null);
    }

    @Autowired
    public ChannelAgentInboundHandler(
            ChannelAgentRouter router,
            AgentService agents,
            ChannelIdentityService identities,
            AgentVersionService versions,
            ChannelConversationService conversations,
            AgentRuntimeRegistry runtimes) {
        this.router = router;
        this.agents = agents;
        this.identities = identities;
        this.versions = versions;
        this.conversations = conversations;
        this.runtimes = runtimes;
    }

    @Override
    public boolean supports(ChannelInboundEvent event) {
        return router.route(event).managed();
    }

    @Override
    public ChannelInboundResult handle(ChannelInboundEvent event) {
        ChannelAgentRouter.Result route = router.route(event);
        return handle(event, route);
    }

    @Override
    public Optional<ChannelInboundResult> tryHandle(ChannelInboundEvent event) {
        ChannelAgentRouter.Result route = router.route(event);
        return route.managed() ? Optional.of(handle(event, route)) : Optional.empty();
    }

    private ChannelInboundResult handle(ChannelInboundEvent event, ChannelAgentRouter.Result route) {
        if (!route.managed()) return ChannelInboundResult.unhandled();
        if (!route.routed()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            putIfPresent(metadata, "connectionId", route.connectionId());
            metadata.put("routeSource", route.source().name());
            return ChannelInboundResult.managedSilent(metadata);
        }
        if (conversations != null
                && conversations.agentPaused(route.tenantId(), route.connectionId(), event.conversationId())) {
            return new ChannelInboundResult(
                    true,
                    null,
                    "human_handoff",
                    Map.of(
                            "connectionId",
                            route.connectionId(),
                            "routeSource",
                            route.source().name(),
                            "agentPaused",
                            true,
                            "pauseReason",
                            "human_handoff",
                            "managed",
                            true));
        }
        ChannelIdentityService.Identity identity = identities.resolve(route.tenantId(), event);
        String executionEmployeeId = trusted(identity) ? identity.enterpriseUserId() : route.employeeId();
        String conversationId = String.join(
                ":",
                event.provider(),
                route.connectionId(),
                executionEmployeeId,
                event.group() ? "group" : "user",
                event.conversationId());
        EffectiveAgent effective;
        boolean fallbackUsed = false;
        try {
            effective = effectiveAgent(route, event);
        } catch (RuntimeException primaryResolutionFailure) {
            if (!hasFallback(route)) return agentError(route, primaryResolutionFailure, false, null);
            try {
                String fallbackVersionId =
                        resolveVersionId(route.tenantId(), route.fallbackAgentId(), route.fallbackAgentVersionId());
                effective = new EffectiveAgent(
                        route.fallbackAgentId(),
                        fallbackVersionId,
                        "FALLBACK",
                        "fallback:primary_unavailable:"
                                + primaryResolutionFailure.getClass().getSimpleName(),
                        route.routingPolicyVersion());
                fallbackUsed = true;
                promoteFallbackRoute(
                        route,
                        event,
                        route.agentId(),
                        effective.agentId(),
                        effective.versionId(),
                        effective.reason(),
                        effective.routingPolicyVersion());
            } catch (RuntimeException fallbackResolutionFailure) {
                return agentError(
                        route,
                        fallbackResolutionFailure,
                        true,
                        "fallback:unavailable:"
                                + fallbackResolutionFailure.getClass().getSimpleName());
            }
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        putIfPresent(variables, "tenantId", route.tenantId());
        putIfPresent(variables, "employeeId", executionEmployeeId);
        putIfPresent(variables, "channelAccountOwnerId", route.employeeId());
        putIfPresent(variables, "channelId", event.channelId());
        putIfPresent(variables, "channelAccountId", event.accountId());
        putIfPresent(variables, "senderId", event.senderId());
        String externalMemoryScope =
                event.group() || event.senderId() == null || event.senderId().isBlank()
                        ? event.conversationId()
                        : event.senderId();
        variables.put(
                "memoryOwnerId",
                String.join(
                        ":", "channel", executionEmployeeId, event.provider(), event.accountId(), externalMemoryScope));
        if (identity != null) {
            putIfPresent(variables, "enterpriseUserId", identity.enterpriseUserId());
            putIfPresent(variables, "externalIdentityStatus", identity.verificationStatus());
            variables.put(
                    "externalIdentityVerified", identity.enabled() && "VERIFIED".equals(identity.verificationStatus()));
        } else {
            variables.put("externalIdentityVerified", false);
        }
        putIfPresent(variables, "agentRouteSource", effective.source());
        putIfPresent(variables, "agentRouteReason", effective.reason());
        variables.put("routingPolicyVersion", effective.routingPolicyVersion());
        putIfPresent(variables, "agentVersionId", effective.versionId());
        if (fallbackUsed) markFallback(variables, effective.reason(), effective.agentId(), effective.versionId());
        AgentRequest request = AgentRequest.builder()
                .query(event.content())
                .conversationId(conversationId)
                .variables(variables)
                .build();
        AgentResponse response;
        String effectiveAgentId = effective.agentId();
        String effectiveVersionId = effective.versionId();
        String executionRouteReason = effective.reason();
        try {
            response = runAsChannelPrincipal(
                    route, event, executionEmployeeId, effectiveAgentId, effectiveVersionId, request);
        } catch (RuntimeException primaryFailure) {
            if (fallbackUsed || !hasFallback(route)) {
                return agentError(route, primaryFailure, fallbackUsed, executionRouteReason);
            }
            try {
                effectiveAgentId = route.fallbackAgentId();
                effectiveVersionId =
                        resolveVersionId(route.tenantId(), effectiveAgentId, route.fallbackAgentVersionId());
                fallbackUsed = true;
                executionRouteReason = "fallback:primary_exception:"
                        + primaryFailure.getClass().getSimpleName();
                markFallback(variables, executionRouteReason, effectiveAgentId, effectiveVersionId);
                promoteFallbackRoute(
                        route,
                        event,
                        effective.agentId(),
                        effectiveAgentId,
                        effectiveVersionId,
                        executionRouteReason,
                        effective.routingPolicyVersion());
                response = runAsChannelPrincipal(
                        route, event, executionEmployeeId, effectiveAgentId, effectiveVersionId, request);
            } catch (RuntimeException fallbackFailure) {
                return agentError(route, fallbackFailure, true, executionRouteReason);
            }
        }
        if (!response.isCompleted() && hasFallback(route)) {
            if (!fallbackUsed) {
                try {
                    effectiveAgentId = route.fallbackAgentId();
                    effectiveVersionId =
                            resolveVersionId(route.tenantId(), effectiveAgentId, route.fallbackAgentVersionId());
                    fallbackUsed = true;
                    executionRouteReason = "fallback:primary_incomplete:"
                            + response.getStatus().name().toLowerCase();
                    markFallback(variables, executionRouteReason, effectiveAgentId, effectiveVersionId);
                    promoteFallbackRoute(
                            route,
                            event,
                            effective.agentId(),
                            effectiveAgentId,
                            effectiveVersionId,
                            executionRouteReason,
                            effective.routingPolicyVersion());
                    response = runAsChannelPrincipal(
                            route, event, executionEmployeeId, effectiveAgentId, effectiveVersionId, request);
                } catch (RuntimeException fallbackFailure) {
                    return agentError(route, fallbackFailure, true, executionRouteReason);
                }
            }
        }
        if (!response.isCompleted()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            putIfPresent(metadata, "runId", response.getRunId());
            return new ChannelInboundResult(
                    true,
                    response.getError() == null ? "Agent 暂时无法完成本次请求" : response.getError(),
                    response.getStatus().name().toLowerCase(),
                    metadata);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "runId", response.getRunId());
        putIfPresent(metadata, "connectionId", route.connectionId());
        putIfPresent(metadata, "agentId", effectiveAgentId);
        putIfPresent(metadata, "agentVersionId", effectiveVersionId);
        putIfPresent(metadata, "routeReason", executionRouteReason);
        metadata.put("routingPolicyVersion", effective.routingPolicyVersion());
        metadata.put("fallbackUsed", fallbackUsed);
        metadata.put("managed", true);
        return ChannelInboundResult.reply(response.getText(), metadata);
    }

    private AgentResponse runAsChannelPrincipal(
            ChannelAgentRouter.Result route,
            ChannelInboundEvent event,
            String executionEmployeeId,
            String agentId,
            String versionId,
            AgentRequest request) {
        CurrentUser principal = CurrentUser.builder()
                .tenantId(route.tenantId())
                .userId(executionEmployeeId)
                .username(event.senderId())
                .principalType(PrincipalType.CHAT_SESSION)
                .roles(java.util.Set.of("CHANNEL_USER"))
                .extra(new LinkedHashMap<>())
                .build()
                .put("channelProvider", event.provider())
                .put("channelId", event.channelId())
                .put("channelAccountId", event.accountId())
                .put("externalSenderId", event.senderId())
                .put("connectionId", route.connectionId());
        return UserContextHolder.callAs(principal, () -> {
            // Existing installations may have bindings created before versioning existed.
            // A pinned version is always strict; an unpinned legacy binding follows its
            // current draft only until the first immutable version is published.
            if (versions == null || (versionId == null && versions.current(route.tenantId(), agentId) == null)) {
                return agents.run(agentId, request);
            }
            var definition = versions.definition(route.tenantId(), agentId, versionId);
            return runtimes == null
                    ? agents.runDefinition(definition, request)
                    : runtimes.run(definition, request, null, null);
        });
    }

    private static boolean trusted(ChannelIdentityService.Identity identity) {
        return identity != null
                && identity.enabled()
                && "VERIFIED".equalsIgnoreCase(identity.verificationStatus())
                && identity.enterpriseUserId() != null
                && !identity.enterpriseUserId().isBlank();
    }

    private EffectiveAgent effectiveAgent(ChannelAgentRouter.Result route, ChannelInboundEvent event) {
        if (conversations != null) {
            ChannelConversationService.AgentAssignment pinned =
                    conversations.agentAssignment(route.tenantId(), route.connectionId(), event.conversationId());
            if (pinned != null)
                return new EffectiveAgent(
                        pinned.agentId(),
                        pinned.agentVersionId(),
                        "CONVERSATION",
                        pinned.routeReason(),
                        pinned.routingPolicyVersion());
        }
        String versionId = resolveVersionId(route.tenantId(), route.agentId(), route.agentVersionId());
        if (conversations != null) {
            ChannelConversationService.AgentAssignment pinned = conversations.pinAgentRoute(
                    route.tenantId(),
                    route.connectionId(),
                    event.conversationId(),
                    route.agentId(),
                    versionId,
                    route.reason(),
                    route.routingPolicyVersion());
            if (pinned != null)
                return new EffectiveAgent(
                        pinned.agentId(),
                        pinned.agentVersionId(),
                        "CONVERSATION",
                        pinned.routeReason(),
                        pinned.routingPolicyVersion());
        }
        return new EffectiveAgent(
                route.agentId(), versionId, route.source().name(), route.reason(), route.routingPolicyVersion());
    }

    private String resolveVersionId(String tenantId, String agentId, String requestedVersionId) {
        if (versions == null) return requestedVersionId;
        if (requestedVersionId != null && !requestedVersionId.isBlank()) {
            return versions.requireRunnable(tenantId, agentId, requestedVersionId)
                    .getId();
        }
        var current = versions.current(tenantId, agentId);
        return current == null ? null : current.getId();
    }

    private static void markFallback(Map<String, Object> variables, String reason, String agentId, String versionId) {
        variables.put("agentFallback", true);
        variables.put("agentRouteReason", reason);
        variables.put("effectiveAgentId", agentId);
        if (versionId != null) variables.put("agentVersionId", versionId);
        else variables.remove("agentVersionId");
    }

    private void promoteFallbackRoute(
            ChannelAgentRouter.Result route,
            ChannelInboundEvent event,
            String expectedAgentId,
            String fallbackAgentId,
            String fallbackVersionId,
            String reason,
            long routingPolicyVersion) {
        if (conversations != null) {
            conversations.promoteFallbackRoute(
                    route.tenantId(),
                    route.connectionId(),
                    event.conversationId(),
                    expectedAgentId,
                    fallbackAgentId,
                    fallbackVersionId,
                    reason,
                    routingPolicyVersion);
        }
    }

    private record EffectiveAgent(
            String agentId, String versionId, String source, String reason, long routingPolicyVersion) {}

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static boolean hasFallback(ChannelAgentRouter.Result route) {
        return route.fallbackAgentId() != null
                && !route.fallbackAgentId().isBlank()
                && !route.fallbackAgentId().equals(route.agentId());
    }

    private static ChannelInboundResult agentError(
            ChannelAgentRouter.Result route, RuntimeException error, boolean fallbackUsed, String routeReason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("managed", true);
        putIfPresent(metadata, "connectionId", route.connectionId());
        putIfPresent(metadata, "errorType", error.getClass().getSimpleName());
        metadata.put("fallbackUsed", fallbackUsed);
        putIfPresent(metadata, "routeReason", routeReason);
        return new ChannelInboundResult(true, "服务暂时不可用，请稍后重试。", "agent_error", metadata);
    }
}
