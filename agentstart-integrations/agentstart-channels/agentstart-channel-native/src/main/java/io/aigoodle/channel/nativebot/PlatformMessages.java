package io.aigoodle.channel.nativebot;

import io.aigoodle.channel.api.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PlatformMessages {
    private PlatformMessages() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    public static InboundMessage inbound(
            String channel,
            String account,
            String id,
            String conversation,
            String sender,
            String target,
            String content,
            boolean group,
            Map<String, Object> raw) {
        return new InboundMessage(
                id,
                channel,
                account,
                conversation,
                group ? ConversationType.GROUP : ConversationType.DIRECT,
                sender,
                null,
                target,
                List.of(MessageContent.text(content == null ? "" : content)),
                Instant.now(),
                raw);
    }

    public static String outboundText(OutboundMessage message) {
        return message.contents().stream()
                .map(MessageContent::text)
                .filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /** Rejects platform-level failures returned inside an otherwise successful HTTP response. */
    public static void requireSuccess(Map<String, Object> response, String platform, String... codeKeys) {
        Object code = null;
        for (String key : codeKeys) {
            if (response.containsKey(key)) {
                code = response.get(key);
                break;
            }
        }
        if (code == null) return;
        String value = String.valueOf(code);
        if ("0".equals(value) || "ok".equalsIgnoreCase(value) || "success".equalsIgnoreCase(value)) {
            return;
        }
        String message = text(response, "msg", "message", "errmsg", "errorMessage");
        throw new ChannelException(
                platform + "_" + value,
                message == null ? platform + " request failed with code " + value : message,
                false);
    }

    public static Map<String, Object> schema(Object... pairs) {
        java.util.LinkedHashMap<String, Object> p = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            p.put(String.valueOf(pairs[i]), property(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1])));
        return Map.of("type", "object", "properties", p, "required", List.copyOf(p.keySet()));
    }

    public static Map<String, Object> optionalSchema(Object... pairs) {
        java.util.LinkedHashMap<String, Object> p = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            p.put(String.valueOf(pairs[i]), property(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1])));
        return Map.of("type", "object", "properties", p);
    }

    private static Map<String, Object> property(String name, String type) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("type", type);
        value.put("title", title(name));
        if (name.toLowerCase(java.util.Locale.ROOT).matches(".*(secret|password|token|key).*$"))
            value.put("writeOnly", true);
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith("url")) value.put("format", "uri");
        return value;
    }

    private static String title(String name) {
        return switch (name) {
            case "appId" -> "App ID";
            case "clientId" -> "Client ID / AppKey";
            case "clientSecret" -> "Client Secret";
            case "appSecret" -> "App Secret";
            case "apiBase" -> "API 基础地址";
            case "verificationToken" -> "验证 Token";
            case "encryptKey" -> "飞书 Encrypt Key";
            case "callbackToken" -> "回调 Token";
            case "webhookUrl" -> "群机器人 Webhook URL（可选）";
            case "corpId" -> "Corp ID";
            case "botId" -> "Bot ID";
            case "secret" -> "Secret";
            case "wsUrl" -> "长连接地址（私有化部署可选）";
            case "corpSecret" -> "Corp Secret";
            case "agentId" -> "Agent ID";
            case "token" -> "回调 Token";
            case "encodingAesKey" -> "EncodingAESKey";
            case "smtpHost" -> "SMTP 主机";
            case "smtpPort" -> "SMTP 端口";
            case "imapHost" -> "IMAP 主机";
            case "imapPort" -> "IMAP 端口";
            case "username" -> "邮箱用户名";
            case "password" -> "邮箱密码 / 授权码";
            case "from" -> "发件人地址";
            case "ssl" -> "启用 SSL";
            case "startTls" -> "启用 STARTTLS";
            case "pollIntervalSeconds" -> "收件轮询秒数";
            case "outboundUrl" -> "出站 Webhook URL";
            case "bearerToken" -> "Bearer Token";
            default -> name;
        };
    }
}
