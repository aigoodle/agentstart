package io.aigoodle.channel.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import java.util.*;

public final class WeComChannel implements NativeChannel<WeComChannel.Config> {
    public record Config(
            String botId,
            String secret,
            String wsUrl,
            String accountId,
            String corpId,
            String corpSecret,
            String agentId,
            String token,
            String encodingAesKey) {
        /** Backward-compatible constructor for legacy enterprise-app callback accounts. */
        public Config(
                String corpId,
                String corpSecret,
                String agentId,
                String token,
                String encodingAesKey,
                String accountId) {
            this(null, null, null, accountId, corpId, corpSecret, agentId, token, encodingAesKey);
        }

        String resolvedBotId() {
            return trim(present(botId) ? botId : corpId);
        }

        String resolvedSecret() {
            return trim(present(secret) ? secret : corpSecret);
        }

        boolean aiBot() {
            // Accounts created by the previous UI stored Bot ID/Secret in the legacy
            // corpId/corpSecret slots. Official AI Bot IDs use the "aib" prefix, so
            // recognise those records without asking users to recreate the account.
            return present(botId) || (present(corpId) && corpId.startsWith("aib")) || !present(agentId);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }

        private static String trim(String value) {
            return value == null ? null : value.trim();
        }
    }

    private final HttpJsonClient http;
    private final AccessTokenCache tokens = new AccessTokenCache();
    private final Map<String, WeComGatewaySession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public WeComChannel(ObjectMapper j) {
        http = new HttpJsonClient(j);
    }

    public String id() {
        return "wecom";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "企业微信智能机器人",
                "企业微信智能机器人 WebSocket 长连接消息通道",
                "2",
                new ChannelCapabilities(
                        Set.of(MessageType.TEXT, MessageType.MARKDOWN),
                        Set.of(MessageType.TEXT, MessageType.MARKDOWN),
                        true,
                        true,
                        true,
                        true),
                ChannelAccountModel.tenant(new ChannelAccountModel.IdentityBridge(
                        true, "OAUTH", "企业微信用户", "企业员工", "首次交互时引导企业微信用户完成员工身份绑定。")),
                Map.of(
                        "icon", "企微",
                        "platformId", "wecom",
                        "transport", "websocket",
                        "transports", List.of("websocket", "legacy-webhook")));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.schema("botId", "string", "secret", "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.optionalSchema("wsUrl", "string");
    }

    public ConnectionTestResult test(Config c) {
        if (c.aiBot()) {
            if (!present(c.resolvedBotId()) || !present(c.resolvedSecret()))
                return new ConnectionTestResult(false, "wecom_credentials", "Bot ID 和 Secret 均为必填项", Map.of());
            WeComGatewaySession session = sessions.get(c.accountId());
            if (session != null && session.uses(c) && !session.connected()) {
                String error = session.lastError();
                return new ConnectionTestResult(
                        false,
                        "wecom_not_connected",
                        error == null ? "企业微信长连接正在认证，请稍后重试" : error,
                        Map.of("transport", "websocket"));
            }
            return ConnectionTestResult.ok();
        }
        token(c);
        return ConnectionTestResult.ok();
    }

    @Override
    public ChannelSession connect(Config c, InboundMessageSink sink) {
        if (!c.aiBot()) return null;
        WeComGatewaySession session = new WeComGatewaySession(c, sink, this::detach);
        sessions.put(c.accountId(), session);
        return session;
    }

    public SendResult send(Config c, OutboundMessage m) {
        if (c.aiBot()) {
            WeComGatewaySession session = sessions.get(c.accountId());
            if (session == null || !session.connected())
                throw new ChannelException(
                        "wecom_not_connected",
                        session == null || session.lastError() == null ? "企业微信智能机器人长连接尚未就绪" : session.lastError(),
                        true);
            return session.send(m);
        }
        Map<String, Object> r = http.post(
                "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token(c),
                Map.of(),
                Map.of(
                        "touser",
                        m.targetId(),
                        "msgtype",
                        "text",
                        "agentid",
                        Long.parseLong(c.agentId()),
                        "text",
                        Map.of("content", PlatformMessages.outboundText(m)),
                        "safe",
                        0));
        if (!"0".equals(String.valueOf(r.getOrDefault("errcode", 0))))
            throw new ChannelException("wecom_" + r.get("errcode"), String.valueOf(r.get("errmsg")), false);
        return SendResult.accepted(PlatformMessages.text(r, "msgid"));
    }

    public InboundMessage parse(Config c, Map<String, String> h, Map<String, Object> p) {
        Map<String, Object> body = p;
        String encrypted = PlatformMessages.text(p, "Encrypt");
        if (encrypted != null) {
            verify(
                    c,
                    PlatformMessages.text(p, "msg_signature", "signature"),
                    PlatformMessages.text(p, "timestamp"),
                    PlatformMessages.text(p, "nonce"),
                    encrypted);
            body = xml(decrypt(c, encrypted));
        } else if (c.token() != null && !c.token().isBlank()) {
            verifyPlain(c, p);
        }
        String sender = PlatformMessages.text(body, "FromUserName", "fromUserName"),
                id = PlatformMessages.text(body, "MsgId", "msgId");
        return PlatformMessages.inbound(
                id(),
                c.accountId(),
                id,
                sender,
                sender,
                sender,
                PlatformMessages.text(body, "Content", "content"),
                false,
                body);
    }

    public Object challenge(Config c, Map<String, String> h, Map<String, Object> p) {
        String echo = PlatformMessages.text(p, "echostr");
        if (echo == null) return null;
        String signature = PlatformMessages.text(p, "msg_signature", "signature"),
                timestamp = PlatformMessages.text(p, "timestamp"),
                nonce = PlatformMessages.text(p, "nonce");
        verify(c, signature, timestamp, nonce, echo);
        return c.encodingAesKey() == null || c.encodingAesKey().isBlank() ? echo : decrypt(c, echo);
    }

    private String token(Config c) {
        return tokens.get(c.corpId() + ":" + c.agentId(), java.time.Duration.ofMinutes(110), () -> {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + java.net.URLEncoder.encode(c.corpId(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&corpsecret="
                    + java.net.URLEncoder.encode(c.corpSecret(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> r = http.get(url, Map.of());
            String t = PlatformMessages.text(r, "access_token");
            if (t == null) throw new ChannelException("wecom_auth", String.valueOf(r.get("errmsg")), false);
            return t;
        });
    }

    private void detach(String accountId, WeComGatewaySession session) {
        sessions.remove(accountId, session);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String signature(String... parts) {
        try {
            if (java.util.Arrays.stream(parts).anyMatch(java.util.Objects::isNull)) return "";
            java.util.Arrays.sort(parts);
            String joined = String.join("", parts);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void verify(Config c, String supplied, String timestamp, String nonce, String encrypted) {
        if (c.token() != null
                && !c.token().isBlank()
                && !signature(c.token(), timestamp, nonce, encrypted).equalsIgnoreCase(supplied))
            throw new ChannelException("invalid_signature", "Invalid WeCom callback signature", false);
    }

    private static void verifyPlain(Config c, Map<String, Object> payload) {
        String supplied = PlatformMessages.text(payload, "msg_signature", "signature");
        String expected = signature(
                c.token(), PlatformMessages.text(payload, "timestamp"), PlatformMessages.text(payload, "nonce"));
        if (!expected.equalsIgnoreCase(supplied))
            throw new ChannelException("invalid_signature", "Invalid WeCom callback signature", false);
    }

    private static String decrypt(Config c, String encrypted) {
        try {
            byte[] key = java.util.Base64.getDecoder().decode(c.encodingAesKey() + "=");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(key, 0, 16));
            byte[] plain = cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted));
            int pad = plain[plain.length - 1] & 255;
            int length = java.nio.ByteBuffer.wrap(plain, 16, 4).getInt();
            String corp = new String(
                    plain, 20 + length, plain.length - pad - 20 - length, java.nio.charset.StandardCharsets.UTF_8);
            if (!corp.equals(c.corpId())) throw new ChannelException("invalid_corp_id", "WeCom CorpId mismatch", false);
            return new String(plain, 20, length, java.nio.charset.StandardCharsets.UTF_8);
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException("decrypt_failed", "Cannot decrypt WeCom callback", false, e);
        }
    }

    private static Map<String, Object> xml(String value) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(value)));
            Map<String, Object> result = new LinkedHashMap<>();
            var nodes = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++)
                if (nodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
                    result.put(nodes.item(i).getNodeName(), nodes.item(i).getTextContent());
            return result;
        } catch (Exception e) {
            throw new ChannelException("invalid_xml", "Invalid WeCom callback XML", false, e);
        }
    }
}
