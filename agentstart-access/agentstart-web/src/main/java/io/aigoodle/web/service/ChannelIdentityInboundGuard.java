package io.aigoodle.web.service;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelIdentityAuthenticator;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundHandler;
import io.aigoodle.channel.ChannelInboundResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Prevents an unverified external channel principal from reaching workflows or agents. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ChannelIdentityInboundGuard implements ChannelInboundHandler {
    private final ChannelConnectionService connections;
    private final ChannelIdentityAuthenticator authenticator;

    public ChannelIdentityInboundGuard(
            ChannelConnectionService connections, ChannelIdentityAuthenticator authenticator) {
        this.connections = connections;
        this.authenticator = authenticator;
    }

    @Override
    public boolean supports(ChannelInboundEvent event) {
        return ownership(event) != null;
    }

    @Override
    public ChannelInboundResult handle(ChannelInboundEvent event) {
        return tryHandle(event).orElseGet(ChannelInboundResult::unhandled);
    }

    @Override
    public Optional<ChannelInboundResult> tryHandle(ChannelInboundEvent event) {
        ChannelConnectionService.Ownership ownership = ownership(event);
        if (ownership == null) return Optional.empty();
        ChannelIdentityAuthenticator.Result result = authenticator.authenticate(ownership.tenantId(), event);
        if (result.allowed()) return Optional.empty();
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
        metadata.put("managed", true);
        metadata.put("identityRequired", true);
        metadata.put("externalUserId", event.senderId());
        if (result.bindingUrl() != null) metadata.put("bindingUrl", result.bindingUrl());
        String reply = result.message();
        if (result.bindingUrl() != null) reply = reply + "\n" + result.bindingUrl();
        return Optional.of(new ChannelInboundResult(true, reply, result.code(), metadata));
    }

    private ChannelConnectionService.Ownership ownership(ChannelInboundEvent event) {
        return connections.ownership(event.provider(), event.runtimeNodeId(), event.channelId(), event.accountId());
    }
}
