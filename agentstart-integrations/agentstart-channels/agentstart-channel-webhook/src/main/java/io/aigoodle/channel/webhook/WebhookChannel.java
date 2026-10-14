package io.aigoodle.channel.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import java.util.*;

public final class WebhookChannel implements NativeChannel<WebhookChannel.Config> {
    public record Config(String outboundUrl, String bearerToken, String callbackToken, String accountId) {}

    private final HttpJsonClient http;

    public WebhookChannel(ObjectMapper j) {
        http = new HttpJsonClient(j);
    }

    public String id() {
        return "webhook";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "Webhook",
                "通用 HTTP JSON 双向消息通道",
                "1",
                ChannelCapabilities.text(),
                ChannelAccountModel.tenant(null),
                Map.of("icon", "Webhook"));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.optionalSchema("callbackToken", "string", "bearerToken", "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.schema("outboundUrl", "string");
    }

    public ConnectionTestResult test(Config c) {
        return c.outboundUrl() != null && c.outboundUrl().startsWith("http")
                ? ConnectionTestResult.ok()
                : new ConnectionTestResult(false, "invalid_url", "Outbound URL is invalid", Map.of());
    }

    public SendResult send(Config c, OutboundMessage m) {
        Map<String, String> h = c.bearerToken() == null || c.bearerToken().isBlank()
                ? Map.of()
                : Map.of("Authorization", "Bearer " + c.bearerToken());
        Map<String, Object> r = http.post(
                c.outboundUrl(),
                h,
                Map.of(
                        "idempotencyKey",
                        String.valueOf(m.idempotencyKey()),
                        "conversationId",
                        String.valueOf(m.conversationId()),
                        "targetId",
                        m.targetId(),
                        "content",
                        PlatformMessages.outboundText(m)));
        return SendResult.accepted(PlatformMessages.text(r, "messageId", "id"));
    }

    public InboundMessage parse(Config c, Map<String, String> h, Map<String, Object> p) {
        String supplied = h.getOrDefault("x-agentstart-token", h.get("X-Agent-Start-Token"));
        if (c.callbackToken() != null
                && !c.callbackToken().isBlank()
                && !c.callbackToken().equals(supplied))
            throw new ChannelException("invalid_signature", "Invalid webhook token", false);
        String conversation = PlatformMessages.text(p, "conversationId", "senderId"),
                sender = PlatformMessages.text(p, "senderId");
        return PlatformMessages.inbound(
                id(),
                c.accountId(),
                PlatformMessages.text(p, "messageId", "id"),
                conversation,
                sender,
                PlatformMessages.text(p, "replyTargetId", "conversationId", "senderId"),
                PlatformMessages.text(p, "content", "text"),
                Boolean.TRUE.equals(p.get("group")),
                p);
    }
}
