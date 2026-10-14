package io.aigoodle.channel.nativebot;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.ChannelDefinition;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.dingtalk.DingTalkChannel;
import io.aigoodle.channel.email.EmailChannel;
import io.aigoodle.channel.feishu.FeishuChannel;
import io.aigoodle.channel.qqbot.QQBotChannel;
import io.aigoodle.channel.webhook.WebhookChannel;
import io.aigoodle.channel.wecom.WeComChannel;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeChannelParsingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void qqGroupKeepsGroupReplyTargetAndVerifiesNativeSignature() throws Exception {
        QQBotChannel c = new QQBotChannel(json);
        QQBotChannel.Config cfg = new QQBotChannel.Config("a", "secret", "", "acc");
        Map<String, Object> payload = Map.of(
                "d",
                Map.of("id", "m1", "group_openid", "g1", "content", "hello", "author", Map.of("member_openid", "u1")));
        String raw = json.writeValueAsString(payload), timestamp = "1730000000";
        @SuppressWarnings("unchecked")
        Map<String, Object> signed = (Map<String, Object>)
                c.challenge(cfg, Map.of(), Map.of("op", 13, "d", Map.of("plain_token", raw, "event_ts", timestamp)));
        Map<String, String> headers = Map.of(
                "x-signature-timestamp",
                timestamp,
                "x-signature-ed25519",
                String.valueOf(signed.get("signature")),
                "x-agentstart-raw-body",
                Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        InboundMessage m = c.parse(cfg, headers, payload);
        assertEquals(ConversationType.GROUP, m.conversationType());
        assertEquals("g1", m.replyTargetId());
        assertEquals("u1", m.senderId());
    }

    @Test
    void qqValidationReturnsEd25519Signature() {
        QQBotChannel c = new QQBotChannel(json);
        Object result = c.challenge(
                new QQBotChannel.Config("a", "secret", null, "acc"),
                Map.of(),
                Map.of("op", 13, "d", Map.of("plain_token", "token", "event_ts", "123")));
        assertInstanceOf(Map.class, result);
        assertEquals("token", ((Map<?, ?>) result).get("plain_token"));
        assertEquals(128, String.valueOf(((Map<?, ?>) result).get("signature")).length());
    }

    @Test
    void feishuChallengeDoesNotDispatchMessage() {
        FeishuChannel c = new FeishuChannel(json);
        assertEquals(
                Map.of("challenge", "ok"),
                c.challenge(new FeishuChannel.Config("a", "s", "v", null, "acc"), Map.of(), Map.of("challenge", "ok")));
    }

    @Test
    void feishuDecryptsEncryptedCallback() throws Exception {
        String key = "encrypt-key", plain = "{\"challenge\":\"encrypted-ok\"}";
        byte[] aes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(aes, "AES"),
                new javax.crypto.spec.IvParameterSpec(aes, 0, 16));
        String encrypted = Base64.getEncoder()
                .encodeToString(cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        FeishuChannel c = new FeishuChannel(json);
        assertEquals(
                Map.of("challenge", "encrypted-ok"),
                c.challenge(
                        new FeishuChannel.Config("a", "s", null, key, "acc"), Map.of(), Map.of("encrypt", encrypted)));
    }

    @Test
    void weComDecryptsAndVerifiesEncryptedCallback() throws Exception {
        String corpId = "corp-id",
                token = "callback-token",
                timestamp = "1730000000",
                nonce = "nonce",
                xml = "<xml><FromUserName>user-1</FromUserName><MsgId>message-1</MsgId><Content>hello</Content></xml>";
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String encodingKey = Base64.getEncoder().withoutPadding().encodeToString(key);
        byte[] message = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                corp = corpId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int unpadded = 20 + message.length + corp.length, pad = 32 - unpadded % 32;
        java.nio.ByteBuffer plain = java.nio.ByteBuffer.allocate(unpadded + pad);
        plain.put(new byte[16]).putInt(message.length).put(message).put(corp);
        for (int i = 0; i < pad; i++) plain.put((byte) pad);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(key, 0, 16));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(plain.array()));
        String[] parts = {token, timestamp, nonce, encrypted};
        Arrays.sort(parts);
        String signature = java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-1")
                        .digest(String.join("", parts).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        WeComChannel c = new WeComChannel(json);
        InboundMessage inbound = c.parse(
                new WeComChannel.Config(corpId, "secret", "1", token, encodingKey, "acc"),
                Map.of(),
                Map.of("Encrypt", encrypted, "msg_signature", signature, "timestamp", timestamp, "nonce", nonce));
        assertEquals("message-1", inbound.messageId());
        assertEquals("user-1", inbound.replyTargetId());
        assertEquals("hello", inbound.text());
    }

    @Test
    void weComPlainCallbackStillRequiresConfiguredTokenSignature() throws Exception {
        String token = "callback-token", timestamp = "1730000000", nonce = "nonce";
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        String signature = java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-1")
                        .digest(String.join("", parts).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        WeComChannel channel = new WeComChannel(json);
        WeComChannel.Config config = new WeComChannel.Config("corp", "secret", "1", token, null, "acc");
        Map<String, Object> payload = Map.of(
                "FromUserName",
                "user-1",
                "MsgId",
                "message-1",
                "Content",
                "hello",
                "timestamp",
                timestamp,
                "nonce",
                nonce,
                "msg_signature",
                signature);

        assertEquals("message-1", channel.parse(config, Map.of(), payload).messageId());
        Map<String, Object> unsigned = new LinkedHashMap<>(payload);
        unsigned.remove("msg_signature");
        assertThrows(ChannelException.class, () -> channel.parse(config, Map.of(), unsigned));
    }

    @Test
    void weComMigratesBotCredentialsStoredInLegacyFieldsWithoutCallingCorpTokenApi() {
        WeComChannel channel = new WeComChannel(json);
        WeComChannel.Config legacyUiRecord =
                new WeComChannel.Config("aib-example", "bot-secret", "1", null, null, "acc");

        ConnectionTestResult result = channel.test(legacyUiRecord);

        assertTrue(result.success());
        assertEquals("websocket", channel.descriptor().metadata().get("transport"));
    }

    @Test
    void webhookRequiresConfiguredToken() {
        WebhookChannel c = new WebhookChannel(json);
        WebhookChannel.Config cfg = new WebhookChannel.Config("https://example.test", null, "secret", "acc");
        assertThrows(
                ChannelException.class,
                () -> c.parse(cfg, Map.of(), Map.of("messageId", "1", "senderId", "u", "content", "x")));
        InboundMessage m = c.parse(
                cfg, Map.of("x-agentstart-token", "secret"), Map.of("messageId", "1", "senderId", "u", "content", "x"));
        assertEquals("u", m.replyTargetId());
    }

    @Test
    void platformBusinessErrorsAreNotReportedAsSuccessfulSends() {
        assertDoesNotThrow(() -> PlatformMessages.requireSuccess(Map.of("code", 0), "feishu", "code"));
        ChannelException failure = assertThrows(
                ChannelException.class,
                () -> PlatformMessages.requireSuccess(
                        Map.of("code", 230001, "msg", "permission denied"), "feishu", "code"));
        assertEquals("feishu_230001", failure.code());
        assertEquals("permission denied", failure.getMessage());
    }

    @Test
    void runtimePublishesAllNativeChannels() {
        NativeChannelRuntimeProvider runtime = new NativeChannelRuntimeProvider(
                List.of(
                        new QQBotChannel(json),
                        new FeishuChannel(json),
                        new DingTalkChannel(json),
                        new WeComChannel(json),
                        new EmailChannel(),
                        new WebhookChannel(json)),
                json);
        assertEquals(
                Set.of("qqbot", "feishu", "dingtalk", "wecom", "email", "webhook"),
                runtime.discoverChannels().stream()
                        .map(ChannelDefinition::channelId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void everyBundledChannelPackagesAVersionedYamlManifest() {
        assertEquals(
                Set.of("qqbot", "feishu", "dingtalk", "wecom", "email", "webhook"),
                ChannelManifestCatalog.load().keySet());
        assertTrue(ChannelManifestCatalog.load().values().stream().allMatch(item -> item.schemaVersion() == 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runtimePublishesAccountOwnershipAndIdentityContracts() throws Exception {
        NativeChannelRuntimeProvider runtime = new NativeChannelRuntimeProvider(
                List.of(new QQBotChannel(json), new WeComChannel(json), new EmailChannel()), json);
        Map<String, ChannelDefinition> channels = runtime.discoverChannels().stream()
                .collect(java.util.stream.Collectors.toMap(ChannelDefinition::channelId, item -> item));

        Map<String, Object> qq =
                (Map<String, Object>) channels.get("qqbot").metadata().get("accountModel");
        Map<String, Object> wecom =
                (Map<String, Object>) channels.get("wecom").metadata().get("accountModel");
        Map<String, Object> bridge = (Map<String, Object>) wecom.get("identityBridge");
        assertEquals("PERSONAL", qq.get("scope"));
        assertEquals(true, qq.get("ownerRequired"));
        assertEquals("TENANT", wecom.get("scope"));
        assertEquals(false, wecom.get("ownerRequired"));
        assertEquals("HOST", bridge.get("mode"));
        assertEquals("TENANT", ((Map<?, ?>) channels.get("email").metadata().get("accountModel")).get("scope"));
        assertEquals(1, channels.get("wecom").metadata().get("manifestVersion"));
        assertEquals("classpath-yaml", channels.get("wecom").metadata().get("formSource"));
        assertTrue(json.readTree(channels.get("wecom").credentialSchema())
                .path("properties")
                .path("secret")
                .path("description")
                .asText()
                .contains("不是自建应用 Secret"));
        assertEquals(
                "password",
                ((Map<?, ?>) ((Map<?, ?>) channels.get("wecom").uiSchema().get("fields")).get("secret")).get("widget"));
    }

    @Test
    void everyNativeChannelPublishesItsCompleteConfigurationContract() {
        List<NativeChannel<?>> channels = List.of(
                new QQBotChannel(json),
                new FeishuChannel(json),
                new DingTalkChannel(json),
                new WeComChannel(json),
                new EmailChannel(),
                new WebhookChannel(json));
        Map<String, Set<String>> expected = Map.of(
                "qqbot", Set.of("appId", "clientSecret", "apiBase", "transport", "intents"),
                "feishu", Set.of("appId", "appSecret", "verificationToken", "encryptKey", "transport"),
                "dingtalk", Set.of("clientId", "clientSecret", "webhookUrl", "callbackToken"),
                "wecom", Set.of("botId", "secret", "wsUrl"),
                "email",
                        Set.of(
                                "smtpHost",
                                "smtpPort",
                                "imapHost",
                                "imapPort",
                                "username",
                                "password",
                                "from",
                                "ssl",
                                "startTls",
                                "pollIntervalSeconds"),
                "webhook", Set.of("outboundUrl", "callbackToken", "bearerToken"));
        for (NativeChannel<?> channel : channels) {
            Set<String> actual = new HashSet<>(properties(channel.credentialSchema()));
            actual.addAll(properties(channel.configurationSchema()));
            assertEquals(expected.get(channel.id()), actual, channel.id());
        }
    }

    @Test
    void runtimeRejectsMissingRequiredFieldsBeforeCallingAPlatform() {
        NativeChannelRuntimeProvider runtime = new NativeChannelRuntimeProvider(List.of(new QQBotChannel(json)), json);
        ChannelException failure = assertThrows(
                ChannelException.class,
                () -> runtime.saveAccount(
                        new io.aigoodle.channel.SaveChannelAccountRequest("qqbot", "account", "QQ", false, Map.of())));
        assertEquals("invalid_configuration", failure.code());
        assertTrue(failure.getMessage().contains("appId"));
        assertTrue(failure.getMessage().contains("clientSecret"));
    }

    @Test
    void webhookAccountsAreReadyButNotReportedAsPersistentConnections() {
        CapturingChannel channel = new CapturingChannel();
        NativeChannelRuntimeProvider runtime = new NativeChannelRuntimeProvider(List.of(channel), json);

        io.aigoodle.channel.ChannelAccount account = runtime.saveAccount(
                new io.aigoodle.channel.SaveChannelAccountRequest("capture", "account", "Capture", true, Map.of()));

        assertTrue(account.running());
        assertFalse(account.connected());
        assertEquals("webhook", account.metadata().get("transport"));
    }

    @Test
    void runtimePreservesOriginalConversationAndReplyMessage() {
        CapturingChannel channel = new CapturingChannel();
        NativeChannelRuntimeProvider runtime = new NativeChannelRuntimeProvider(List.of(channel), json);
        runtime.saveAccount(
                new io.aigoodle.channel.SaveChannelAccountRequest("capture", "account", "Capture", true, Map.of()));
        runtime.sendWithResult(new io.aigoodle.channel.ChannelOutboundMessage(
                "capture",
                "account",
                "group-1",
                "group-1",
                "reply",
                "TEXT",
                List.of(),
                Map.of("conversationType", "GROUP"),
                Map.of("replyToPlatformMessageId", "message-1", "idempotencyKey", "key-1")));
        assertEquals("message-1", channel.sent.replyToMessageId());
        assertEquals("GROUP", channel.sent.metadata().get("conversationType"));
    }

    private static final class CapturingChannel implements NativeChannel<Map<String, Object>> {
        OutboundMessage sent;

        public String id() {
            return "capture";
        }

        @SuppressWarnings("unchecked")
        public Class<Map<String, Object>> configType() {
            return (Class<Map<String, Object>>) (Class<?>) Map.class;
        }

        public ChannelDescriptor descriptor() {
            return new ChannelDescriptor(id(), "Capture", "test", "1", ChannelCapabilities.text(), Map.of());
        }

        public Map<String, Object> credentialSchema() {
            return Map.of();
        }

        public ConnectionTestResult test(Map<String, Object> c) {
            return ConnectionTestResult.ok();
        }

        public SendResult send(Map<String, Object> c, OutboundMessage m) {
            sent = m;
            return SendResult.accepted("sent");
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> properties(Map<String, Object> schema) {
        Object value = schema.get("properties");
        return value instanceof Map<?, ?> map ? ((Map<String, Object>) map).keySet() : Set.of();
    }
}
