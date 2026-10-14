package io.aigoodle.channel.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import java.util.*;

public final class QQBotChannel implements NativeChannel<QQBotChannel.Config> {
    public record Config(
            String appId, String clientSecret, String apiBase, String accountId, String transport, Integer intents) {
        public Config(String appId, String clientSecret, String apiBase, String accountId) {
            this(appId, clientSecret, apiBase, accountId, null, null);
        }

        boolean websocketEnabled() {
            // Before Gateway support existed, accounts were persisted with the descriptive value
            // "signed-webhook". Keep those accounts working without requiring every user to edit and
            // resave credentials; only an explicit webhook selection disables the Gateway session.
            return !"webhook".equalsIgnoreCase(transport);
        }
    }

    private final HttpJsonClient http;
    private final AccessTokenCache tokens = new AccessTokenCache();
    private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();

    public QQBotChannel(ObjectMapper json) {
        http = new HttpJsonClient(json);
    }

    public String id() {
        return "qqbot";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "QQBot",
                "QQ 官方机器人消息通道",
                "1",
                new ChannelCapabilities(Set.of(MessageType.TEXT), Set.of(MessageType.TEXT), true, true, true, false),
                Map.of("icon", "QQ", "transport", "websocket", "transports", List.of("websocket", "webhook")));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.schema("appId", "string", "clientSecret", "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.optionalSchema("apiBase", "string", "transport", "string", "intents", "integer");
    }

    public ConnectionTestResult test(Config c) {
        token(c);
        return ConnectionTestResult.ok();
    }

    @Override
    public ChannelSession connect(Config c, InboundMessageSink sink) {
        return c.websocketEnabled() ? new QQBotGatewaySession(this, c, sink, http) : null;
    }

    public SendResult send(Config c, OutboundMessage m) {
        String base = base(c);
        String kind = String.valueOf(m.metadata().getOrDefault("conversationType", "C2C"));
        String path = "GROUP".equalsIgnoreCase(kind)
                ? "/v2/groups/" + m.targetId() + "/messages"
                : "/v2/users/" + m.targetId() + "/messages";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", PlatformMessages.outboundText(m));
        body.put("msg_type", 0);
        body.put("msg_seq", sequence.updateAndGet(v -> v >= 9999 ? 1 : v + 1));
        if (m.replyToMessageId() != null) body.put("msg_id", m.replyToMessageId());
        Map<String, Object> r = http.post(base + path, Map.of("Authorization", "QQBot " + token(c)), body);
        PlatformMessages.requireSuccess(r, "qqbot", "code", "errcode");
        return SendResult.accepted(PlatformMessages.text(r, "id", "message_id"));
    }

    public InboundMessage parse(Config c, Map<String, String> h, Map<String, Object> p) {
        Map<String, Object> d = PlatformMessages.map(p, "d"), author = PlatformMessages.map(d, "author");
        verify(c, h);
        String group = PlatformMessages.text(d, "group_openid", "channel_id"),
                sender = PlatformMessages.text(author, "member_openid", "user_openid", "id"),
                target = group == null ? sender : group;
        return PlatformMessages.inbound(
                id(),
                c.accountId(),
                PlatformMessages.text(d, "id"),
                target,
                sender,
                target,
                PlatformMessages.text(d, "content"),
                group != null,
                p);
    }

    public Object challenge(Config c, Map<String, String> h, Map<String, Object> p) {
        if (!"13".equals(String.valueOf(p.get("op")))) return null;
        Map<String, Object> d = PlatformMessages.map(p, "d");
        String plain = PlatformMessages.text(d, "plain_token"), ts = PlatformMessages.text(d, "event_ts");
        if (plain == null || ts == null)
            throw new ChannelException("invalid_challenge", "QQ callback challenge is incomplete", false);
        return Map.of("plain_token", plain, "signature", sign(c.clientSecret(), ts + plain));
    }

    private String token(Config c) {
        return tokens.get(c.appId(), java.time.Duration.ofMinutes(110), () -> {
            Map<String, Object> r = http.post(
                    "https://bots.qq.com/app/getAppAccessToken",
                    Map.of(),
                    Map.of("appId", c.appId(), "clientSecret", c.clientSecret()));
            PlatformMessages.requireSuccess(r, "qqbot_auth", "code", "errcode");
            return Objects.requireNonNull(PlatformMessages.text(r, "access_token"), "QQ access token missing");
        });
    }

    String accessToken(Config c) {
        return token(c);
    }

    private String base(Config c) {
        return c.apiBase() == null || c.apiBase().isBlank() ? "https://api.sgroup.qq.com" : c.apiBase();
    }

    private static void verify(Config c, Map<String, String> h) {
        String timestamp = header(h, "x-signature-timestamp"),
                signature = header(h, "x-signature-ed25519"),
                raw = h.get("x-agentstart-raw-body");
        if (timestamp == null || signature == null || raw == null)
            throw new ChannelException("missing_signature", "QQ callback signature is required", false);
        try {
            byte[] message = (timestamp
                            + new String(Base64.getDecoder().decode(raw), java.nio.charset.StandardCharsets.UTF_8))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey(c.clientSecret()));
            verifier.update(message);
            if (!verifier.verify(java.util.HexFormat.of().parseHex(signature)))
                throw new ChannelException("invalid_signature", "Invalid QQ callback signature", false);
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException("invalid_signature", "Cannot verify QQ callback signature", false, e);
        }
    }

    private static String sign(String secret, String value) {
        try {
            java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
            signer.initSign(privateKey(secret));
            signer.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(signer.sign());
        } catch (Exception e) {
            throw new ChannelException("challenge_sign_failed", "Cannot sign QQ challenge", false, e);
        }
    }

    private static java.security.PrivateKey privateKey(String secret) throws Exception {
        byte[] seed = seed(secret),
                prefix = java.util.HexFormat.of().parseHex("302e020100300506032b657004220420"),
                der = java.util.Arrays.copyOf(prefix, prefix.length + seed.length);
        System.arraycopy(seed, 0, der, prefix.length, seed.length);
        return java.security.KeyFactory.getInstance("Ed25519")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
    }

    private static java.security.PublicKey publicKey(String secret) throws Exception {
        byte[]
                raw =
                        new org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed(secret), 0)
                                .generatePublicKey()
                                .getEncoded(),
                prefix = java.util.HexFormat.of().parseHex("302a300506032b6570032100"),
                der = java.util.Arrays.copyOf(prefix, prefix.length + raw.length);
        System.arraycopy(raw, 0, der, prefix.length, raw.length);
        return java.security.KeyFactory.getInstance("Ed25519")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
    }

    private static byte[] seed(String secret) {
        String value = secret;
        while (value.length() < 32) value += value;
        return value.substring(0, 32).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String header(Map<String, String> h, String key) {
        for (var e : h.entrySet()) if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
    }
}
