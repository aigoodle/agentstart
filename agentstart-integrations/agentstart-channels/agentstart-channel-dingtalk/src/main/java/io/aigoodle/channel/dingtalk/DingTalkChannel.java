package io.aigoodle.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DingTalkChannel implements NativeChannel<DingTalkChannel.Config> {
    public record Config(
            String clientId, String clientSecret, String webhookUrl, String callbackToken, String accountId) {}

    private final HttpJsonClient http;
    private final AccessTokenCache tokens = new AccessTokenCache();

    public DingTalkChannel(ObjectMapper j) {
        http = new HttpJsonClient(j);
    }

    public String id() {
        return "dingtalk";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "钉钉机器人",
                "钉钉企业机器人消息通道",
                "1",
                new ChannelCapabilities(Set.of(MessageType.TEXT), Set.of(MessageType.TEXT), false, true, true, false),
                ChannelAccountModel.tenant(
                        new ChannelAccountModel.IdentityBridge(true, "OAUTH", "钉钉用户", "企业员工", "运行期间可将钉钉用户关联到租户员工。")),
                Map.of("icon", "钉钉", "transport", "stream"));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.schema("clientId", "string", "clientSecret", "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.optionalSchema("webhookUrl", "string", "callbackToken", "string");
    }

    public ConnectionTestResult test(Config c) {
        if (c.webhookUrl() == null || c.webhookUrl().isBlank()) token(c);
        return ConnectionTestResult.ok();
    }

    public SendResult send(Config c, OutboundMessage m) {
        String content = PlatformMessages.outboundText(m);
        Map<String, Object> r;
        if (c.webhookUrl() != null && !c.webhookUrl().isBlank()) {
            r = http.post(c.webhookUrl(), Map.of(), Map.of("msgtype", "text", "text", Map.of("content", content)));
        } else {
            String kind = String.valueOf(m.metadata().getOrDefault("conversationType", "GROUP"));
            Map<String, String> headers = Map.of("x-acs-dingtalk-access-token", token(c));
            String msgParam = json(Map.of("content", content));
            if ("DIRECT".equalsIgnoreCase(kind) || "C2C".equalsIgnoreCase(kind)) {
                r = http.post(
                        "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend",
                        headers,
                        Map.of(
                                "robotCode",
                                c.clientId(),
                                "userIds",
                                List.of(m.targetId()),
                                "msgKey",
                                "sampleText",
                                "msgParam",
                                msgParam));
            } else {
                r = http.post(
                        "https://api.dingtalk.com/v1.0/robot/groupMessages/send",
                        headers,
                        Map.of(
                                "robotCode",
                                c.clientId(),
                                "openConversationId",
                                m.targetId(),
                                "msgKey",
                                "sampleText",
                                "msgParam",
                                msgParam));
            }
        }
        PlatformMessages.requireSuccess(r, "dingtalk", "errcode", "code");
        return new SendResult(true, PlatformMessages.text(r, "processQueryKey", "messageId"), r);
    }

    public InboundMessage parse(Config c, Map<String, String> h, Map<String, Object> p) {
        String token = h.getOrDefault("token", h.get("Token"));
        if (c.callbackToken() != null
                && !c.callbackToken().isBlank()
                && !c.callbackToken().equals(token))
            throw new ChannelException("invalid_signature", "Invalid DingTalk callback token", false);
        String conversation = PlatformMessages.text(p, "conversationId", "openConversationId"),
                sender = PlatformMessages.text(p, "senderStaffId", "senderId"),
                content = PlatformMessages.text(PlatformMessages.map(p, "text"), "content");
        if (content == null) content = PlatformMessages.text(p, "content");
        boolean group = "2".equals(PlatformMessages.text(p, "conversationType"));
        return PlatformMessages.inbound(
                id(),
                c.accountId(),
                PlatformMessages.text(p, "msgId", "messageId"),
                conversation,
                sender,
                group ? conversation : sender,
                content,
                group,
                p);
    }

    public ChannelSession connect(Config c, InboundMessageSink sink) {
        AtomicBoolean connected = new AtomicBoolean();
        try {
            var listener = (com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener<
                            com.dingtalk.open.app.api.models.bot.ChatbotMessage, com.alibaba.fastjson.JSONObject>)
                    message -> {
                        var text = message.getText();
                        String content = text == null ? "" : text.getContent();
                        String conversation = message.getConversationId();
                        String sender =
                                message.getSenderStaffId() == null ? message.getSenderId() : message.getSenderStaffId();
                        boolean group = "2".equals(message.getConversationType());
                        InboundReceipt receipt = sink.accept(PlatformMessages.inbound(
                                id(),
                                c.accountId(),
                                message.getMsgId(),
                                conversation,
                                sender,
                                group ? conversation : sender,
                                content,
                                group,
                                Map.of(
                                        "conversationType",
                                        message.getConversationType(),
                                        "senderNick",
                                        String.valueOf(message.getSenderNick()))));
                        if (!receipt.accepted()) {
                            throw new ChannelException(
                                    "inbound_" + receipt.code(), "DingTalk inbound message was not accepted", true);
                        }
                        return new com.alibaba.fastjson.JSONObject();
                    };
            var client = com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder.custom()
                    .credential(
                            new com.dingtalk.open.app.api.security.AuthClientCredential(c.clientId(), c.clientSecret()))
                    .registerCallbackListener(
                            com.dingtalk.open.app.api.callback.DingTalkStreamTopics.BOT_MESSAGE_TOPIC, listener)
                    .build();
            Thread worker = Thread.ofVirtual()
                    .name("dingtalk-stream-" + c.accountId())
                    .start(() -> {
                        try {
                            client.start();
                            connected.set(true);
                        } catch (Exception e) {
                            connected.set(false);
                        }
                    });
            return new ChannelSession() {
                public boolean connected() {
                    return connected.get();
                }

                public void close() {
                    try {
                        client.stop();
                    } catch (Exception ignored) {
                    }
                    worker.interrupt();
                    connected.set(false);
                }
            };
        } catch (Exception e) {
            throw new ChannelException("dingtalk_stream_start_failed", e.getMessage(), true, e);
        }
    }

    private String token(Config c) {
        return tokens.get(c.clientId(), java.time.Duration.ofMinutes(110), () -> {
            Map<String, Object> r = http.post(
                    "https://api.dingtalk.com/v1.0/oauth2/accessToken",
                    Map.of(),
                    Map.of("appKey", c.clientId(), "appSecret", c.clientSecret()));
            PlatformMessages.requireSuccess(r, "dingtalk_auth", "errcode", "code");
            return Objects.requireNonNull(PlatformMessages.text(r, "accessToken"));
        });
    }

    private static String json(Object value) {
        return com.alibaba.fastjson.JSON.toJSONString(value);
    }
}
