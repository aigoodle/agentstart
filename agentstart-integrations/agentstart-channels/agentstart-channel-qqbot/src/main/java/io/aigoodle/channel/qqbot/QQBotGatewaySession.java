package io.aigoodle.channel.qqbot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.HttpJsonClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One official QQ Gateway connection per configured QQBot account. */
final class QQBotGatewaySession implements ChannelSession, WebSocket.Listener {
    private static final System.Logger LOG = System.getLogger(QQBotGatewaySession.class.getName());
    private static final int FULL_INTENTS = (1 << 0) | (1 << 1) | (1 << 12) | (1 << 25) | (1 << 26) | (1 << 30);
    private static final long[] RECONNECT_DELAYS = {1, 2, 5, 10, 30, 60};

    private final QQBotChannel channel;
    private final QQBotChannel.Config config;
    private final InboundMessageSink sink;
    private final HttpJsonClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final StringBuilder fragments = new StringBuilder();
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile boolean connected;
    private volatile Integer sequence;
    private volatile String sessionId;
    private int reconnectAttempt;

    QQBotGatewaySession(
            QQBotChannel channel, QQBotChannel.Config config, InboundMessageSink sink, HttpJsonClient http) {
        this.channel = channel;
        this.config = config;
        this.sink = sink;
        this.http = http;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "qqbot-gateway-" + config.accountId());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.execute(this::connect);
    }

    private void connect() {
        if (closed.get()) return;
        try {
            String token = channel.accessToken(config);
            String base = config.apiBase() == null || config.apiBase().isBlank()
                    ? "https://api.sgroup.qq.com"
                    : config.apiBase().replaceAll("/+$", "");
            Map<String, Object> gateway = http.get(base + "/gateway", Map.of("Authorization", "QQBot " + token));
            String url = Objects.toString(gateway.get("url"), "");
            if (url.isBlank()) throw new IllegalStateException("QQ gateway URL is missing");
            LOG.log(System.Logger.Level.INFO, "[{0}] connecting QQ Gateway {1}", config.accountId(), url);
            client.newWebSocketBuilder()
                    .header("User-Agent", "spring-agentstart-qqbot/0.1.0")
                    .connectTimeout(Duration.ofSeconds(20))
                    .buildAsync(URI.create(url), this)
                    .whenComplete((ws, error) -> {
                        if (error != null) {
                            LOG.log(System.Logger.Level.WARNING, "QQ Gateway connect failed", error);
                            scheduleReconnect();
                        } else {
                            socket = ws;
                        }
                    });
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "QQ Gateway bootstrap failed", failure);
            scheduleReconnect();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        reconnectAttempt = 0;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        fragments.append(data);
        if (last) {
            String payload = fragments.toString();
            fragments.setLength(0);
            try {
                handle(json.readValue(payload, new TypeReference<Map<String, Object>>() {}), webSocket);
            } catch (Exception failure) {
                LOG.log(System.Logger.Level.WARNING, "QQ Gateway payload handling failed", failure);
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void handle(Map<String, Object> payload, WebSocket webSocket) {
        int op = ((Number) payload.getOrDefault("op", -1)).intValue();
        if (payload.get("s") instanceof Number value) sequence = value.intValue();
        switch (op) {
            case 0 -> dispatch(Objects.toString(payload.get("t"), ""), map(payload.get("d")));
            case 7 -> reconnect(webSocket);
            case 9 -> {
                if (!Boolean.TRUE.equals(payload.get("d"))) {
                    sessionId = null;
                    sequence = null;
                }
                reconnect(webSocket);
            }
            case 10 -> hello(map(payload.get("d")), webSocket);
            case 11 -> {
                /* heartbeat acknowledged */
            }
            default -> {}
        }
    }

    private void hello(Map<String, Object> hello, WebSocket webSocket) {
        String token = "QQBot " + channel.accessToken(config);
        Map<String, Object> data = new LinkedHashMap<>();
        int op;
        if (sessionId != null && sequence != null) {
            op = 6;
            data.put("token", token);
            data.put("session_id", sessionId);
            data.put("seq", sequence);
        } else {
            op = 2;
            data.put("token", token);
            data.put("intents", config.intents() == null ? FULL_INTENTS : config.intents());
            data.put("shard", List.of(0, 1));
        }
        send(webSocket, Map.of("op", op, "d", data));
        long interval = ((Number) hello.getOrDefault("heartbeat_interval", 30_000)).longValue();
        ScheduledFuture<?> previous = heartbeat;
        if (previous != null) previous.cancel(false);
        heartbeat = scheduler.scheduleAtFixedRate(
                () -> {
                    WebSocket current = socket;
                    if (!closed.get() && current != null) {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("op", 1);
                        value.put("d", sequence);
                        send(current, value);
                    }
                },
                interval,
                interval,
                TimeUnit.MILLISECONDS);
    }

    private void dispatch(String type, Map<String, Object> data) {
        if ("READY".equals(type)) {
            sessionId = Objects.toString(data.get("session_id"), null);
            connected = true;
            LOG.log(System.Logger.Level.INFO, "[{0}] QQ Gateway READY", config.accountId());
            return;
        }
        if ("RESUMED".equals(type)) {
            connected = true;
            LOG.log(System.Logger.Level.INFO, "[{0}] QQ Gateway RESUMED", config.accountId());
            return;
        }
        if (!Set.of(
                        "C2C_MESSAGE_CREATE",
                        "GROUP_AT_MESSAGE_CREATE",
                        "GROUP_MESSAGE_CREATE",
                        "AT_MESSAGE_CREATE",
                        "DIRECT_MESSAGE_CREATE")
                .contains(type)) return;

        Map<String, Object> author = map(data.get("author"));
        String group = text(data, "group_openid");
        String channelId = text(data, "channel_id");
        String guild = text(data, "guild_id");
        String sender = text(author, "member_openid", "user_openid", "id");
        String conversation = first(group, channelId, guild, sender);
        if (sender == null || conversation == null || text(data, "id") == null) return;
        ConversationType conversationType =
                group != null || channelId != null ? ConversationType.GROUP : ConversationType.DIRECT;
        InboundMessage message = new InboundMessage(
                text(data, "id"),
                "qqbot",
                config.accountId(),
                conversation,
                conversationType,
                sender,
                text(author, "username"),
                conversation,
                List.of(MessageContent.text(Objects.toString(data.get("content"), ""))),
                instant(text(data, "timestamp")),
                Map.of("gatewayEventType", type, "raw", data));
        Thread.ofVirtual().name("qqbot-inbound-" + config.accountId()).start(() -> {
            try {
                sink.accept(message);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "QQ inbound dispatch failed", failure);
            }
        });
    }

    private void reconnect(WebSocket webSocket) {
        connected = false;
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect");
    }

    private synchronized void scheduleReconnect() {
        if (closed.get()) return;
        connected = false;
        long delay = RECONNECT_DELAYS[Math.min(reconnectAttempt++, RECONNECT_DELAYS.length - 1)];
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private void send(WebSocket webSocket, Object value) {
        try {
            webSocket.sendText(json.writeValueAsString(value), true);
        } catch (Exception failure) {
            LOG.log(System.Logger.Level.WARNING, "QQ Gateway send failed", failure);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        ScheduledFuture<?> activeHeartbeat = heartbeat;
        if (activeHeartbeat != null) activeHeartbeat.cancel(false);
        if (!closed.get()) scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        LOG.log(System.Logger.Level.WARNING, "QQ Gateway socket error", error);
    }

    @Override
    public boolean connected() {
        return connected;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        connected = false;
        ScheduledFuture<?> activeHeartbeat = heartbeat;
        if (activeHeartbeat != null) activeHeartbeat.cancel(false);
        WebSocket current = socket;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "account stopped");
        scheduler.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Map<String, Object> value, String... keys) {
        for (String key : keys) {
            Object item = value.get(key);
            if (item != null && !String.valueOf(item).isBlank()) return String.valueOf(item);
        }
        return null;
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static Instant instant(String value) {
        try {
            return value == null ? Instant.now() : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }
}
