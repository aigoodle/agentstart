package io.aigoodle.channel.nativebot;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.MessageContent;
import io.aigoodle.channel.api.OutboundMessage;
import io.aigoodle.channel.dingtalk.DingTalkChannel;
import io.aigoodle.channel.email.EmailChannel;
import io.aigoodle.channel.feishu.FeishuChannel;
import io.aigoodle.channel.qqbot.QQBotChannel;
import io.aigoodle.channel.webhook.WebhookChannel;
import io.aigoodle.channel.wecom.WeComChannel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Opt-in black-box sends. Each case runs only when that platform's environment is complete. */
class NativeChannelLiveTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void qqBotAuthentication() {
        String app = env("QQBOT_APP_ID"), secret = env("QQBOT_CLIENT_SECRET");
        assume(app, secret);
        var channel = new QQBotChannel(json);
        var configuration = new QQBotChannel.Config(app, secret, env("QQBOT_API_BASE"), "live-auth");
        assertTrue(channel.test(configuration).success());
    }

    @Test
    void qqBot() {
        String app = env("QQBOT_APP_ID"), secret = env("QQBOT_CLIENT_SECRET"), target = env("QQBOT_TARGET_ID");
        assume(app, secret, target);
        var c = new QQBotChannel(json);
        var cfg = new QQBotChannel.Config(app, secret, env("QQBOT_API_BASE"), "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, env("QQBOT_CONVERSATION_TYPE", "C2C")))
                .accepted());
    }

    @Test
    void feishu() {
        String app = env("FEISHU_APP_ID"), secret = env("FEISHU_APP_SECRET"), target = env("FEISHU_CHAT_ID");
        assume(app, secret, target);
        var c = new FeishuChannel(json);
        var cfg = new FeishuChannel.Config(app, secret, null, null, "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, "GROUP")).accepted());
    }

    @Test
    void dingTalk() {
        String app = env("DINGTALK_CLIENT_ID"),
                secret = env("DINGTALK_CLIENT_SECRET"),
                target = env("DINGTALK_TARGET_ID");
        assume(app, secret, target);
        var c = new DingTalkChannel(json);
        var cfg = new DingTalkChannel.Config(app, secret, env("DINGTALK_WEBHOOK_URL"), null, "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, env("DINGTALK_CONVERSATION_TYPE", "GROUP")))
                .accepted());
    }

    @Test
    void weCom() {
        String corp = env("WECOM_CORP_ID"),
                secret = env("WECOM_CORP_SECRET"),
                agent = env("WECOM_AGENT_ID"),
                target = env("WECOM_USER_ID");
        assume(corp, secret, agent, target);
        var c = new WeComChannel(json);
        var cfg = new WeComChannel.Config(corp, secret, agent, null, null, "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, "DIRECT")).accepted());
    }

    @Test
    void email() {
        String smtp = env("EMAIL_SMTP_HOST"),
                imap = env("EMAIL_IMAP_HOST"),
                user = env("EMAIL_USERNAME"),
                password = env("EMAIL_PASSWORD"),
                from = env("EMAIL_FROM"),
                target = env("EMAIL_TARGET");
        assume(smtp, imap, user, password, from, target);
        var c = new EmailChannel();
        var cfg = new EmailChannel.Config(
                smtp,
                integer("EMAIL_SMTP_PORT"),
                imap,
                integer("EMAIL_IMAP_PORT"),
                user,
                password,
                from,
                Boolean.parseBoolean(env("EMAIL_SSL", "true")),
                optionalBoolean("EMAIL_START_TLS"),
                30,
                "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, "DIRECT")).accepted());
    }

    @Test
    void webhook() {
        String url = env("WEBHOOK_OUTBOUND_URL"), target = env("WEBHOOK_TARGET_ID");
        assume(url, target);
        var c = new WebhookChannel(json);
        var cfg = new WebhookChannel.Config(url, env("WEBHOOK_BEARER_TOKEN"), env("WEBHOOK_CALLBACK_TOKEN"), "live");
        assertTrue(c.test(cfg).success());
        assertTrue(c.send(cfg, message(target, "DIRECT")).accepted());
    }

    private static OutboundMessage message(String target, String type) {
        return new OutboundMessage(
                "live-" + System.nanoTime(),
                target,
                target,
                null,
                List.of(MessageContent.text(env("CHANNEL_LIVE_TEXT", "Agent Start channel live test"))),
                Map.of("conversationType", type));
    }

    private static void assume(String... values) {
        assumeTrue(
                java.util.Arrays.stream(values).allMatch(v -> v != null && !v.isBlank()),
                "live credentials not configured");
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String env(String name, String fallback) {
        String value = env(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer integer(String name) {
        String value = env(name);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static Boolean optionalBoolean(String name) {
        String value = env(name);
        return value == null || value.isBlank() ? null : Boolean.valueOf(value);
    }
}
