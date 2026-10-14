package io.aigoodle.channel.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import java.util.*;

public final class FeishuChannel implements NativeChannel<FeishuChannel.Config> {
    public record Config(
            String appId,
            String appSecret,
            String verificationToken,
            String encryptKey,
            String accountId,
            String transport) {
        public Config(String appId, String appSecret, String verificationToken, String encryptKey, String accountId) {
            this(appId, appSecret, verificationToken, encryptKey, accountId, null);
        }

        boolean streamEnabled() {
            return !"webhook".equalsIgnoreCase(transport);
        }
    }

    private final HttpJsonClient http;
    private final ObjectMapper json;
    private final AccessTokenCache tokens = new AccessTokenCache();

    public FeishuChannel(ObjectMapper j) {
        json = j;
        http = new HttpJsonClient(j);
    }

    public String id() {
        return "feishu";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "飞书机器人",
                "飞书应用机器人消息通道",
                "1",
                new ChannelCapabilities(Set.of(MessageType.TEXT), Set.of(MessageType.TEXT), true, true, true, false),
                ChannelAccountModel.tenant(
                        new ChannelAccountModel.IdentityBridge(true, "OAUTH", "飞书用户", "企业员工", "运行期间可将飞书用户关联到租户员工。")),
                Map.of("icon", "飞书", "transport", "stream", "transports", List.of("stream", "webhook")));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.schema("appId", "string", "appSecret", "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.optionalSchema(
                "verificationToken", "string", "encryptKey", "string", "transport", "string");
    }

    public ConnectionTestResult test(Config c) {
        token(c);
        return ConnectionTestResult.ok();
    }

    @Override
    public ChannelSession connect(Config c, InboundMessageSink sink) {
        return c.streamEnabled() ? new FeishuStreamSession(c, sink, json) : null;
    }

    public SendResult send(Config c, OutboundMessage m) {
        Map<String, Object> r = http.post(
                "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id",
                Map.of("Authorization", "Bearer " + token(c)),
                Map.of(
                        "receive_id",
                        m.targetId(),
                        "msg_type",
                        "text",
                        "content",
                        "{\"text\":" + quote(PlatformMessages.outboundText(m)) + "}"));
        PlatformMessages.requireSuccess(r, "feishu", "code");
        Map<String, Object> d = PlatformMessages.map(r, "data");
        return SendResult.accepted(PlatformMessages.text(d, "message_id"));
    }

    public InboundMessage parse(Config c, Map<String, String> h, Map<String, Object> p) {
        p = decoded(c, p);
        if (c.verificationToken() != null
                && !c.verificationToken().isBlank()
                && !c.verificationToken().equals(PlatformMessages.text(p, "token"))
                && !c.verificationToken().equals(PlatformMessages.text(PlatformMessages.map(p, "header"), "token")))
            throw new ChannelException("invalid_signature", "Invalid Feishu verification token", false);
        Map<String, Object> e = PlatformMessages.map(p, "event"),
                m = PlatformMessages.map(e, "message"),
                s = PlatformMessages.map(e, "sender"),
                sid = PlatformMessages.map(s, "sender_id");
        String chat = PlatformMessages.text(m, "chat_id"), content = contentText(PlatformMessages.text(m, "content"));
        return PlatformMessages.inbound(
                id(),
                c.accountId(),
                PlatformMessages.text(m, "message_id"),
                chat,
                PlatformMessages.text(sid, "open_id", "user_id"),
                chat,
                content,
                "group".equals(PlatformMessages.text(m, "chat_type")),
                p);
    }

    public Object challenge(Config c, Map<String, String> h, Map<String, Object> p) {
        String v = PlatformMessages.text(decoded(c, p), "challenge");
        return v == null ? null : Map.of("challenge", v);
    }

    private String token(Config c) {
        return tokens.get(c.appId(), java.time.Duration.ofMinutes(110), () -> {
            Map<String, Object> r = http.post(
                    "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
                    Map.of(),
                    Map.of("app_id", c.appId(), "app_secret", c.appSecret()));
            PlatformMessages.requireSuccess(r, "feishu_auth", "code");
            return Objects.requireNonNull(PlatformMessages.text(r, "tenant_access_token"));
        });
    }

    private String contentText(String raw) {
        if (raw == null) return "";
        try {
            return PlatformMessages.text(
                    json.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}),
                    "text");
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String quote(String s) {
        try {
            return new ObjectMapper().writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Map<String, Object> decoded(Config c, Map<String, Object> p) {
        String encrypted = PlatformMessages.text(p, "encrypt");
        if (encrypted == null) return p;
        if (c.encryptKey() == null || c.encryptKey().isBlank())
            throw new ChannelException("missing_encrypt_key", "Feishu encrypt key is required", false);
        try {
            byte[] key = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(c.encryptKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(key, 0, 16));
            String plain = new String(
                    cipher.doFinal(Base64.getDecoder().decode(encrypted)), java.nio.charset.StandardCharsets.UTF_8);
            return json.readValue(plain, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new ChannelException("decrypt_failed", "Cannot decrypt Feishu callback", false, e);
        }
    }
}
