package io.aigoodle.channel.wecom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.ChannelReplyStream;
import io.aigoodle.channel.api.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** One official WeCom AI Bot WebSocket connection per configured account. */
final class WeComGatewaySession implements ChannelSession, WebSocket.Listener {
    private static final System.Logger LOG = System.getLogger(WeComGatewaySession.class.getName());
    private static final String DEFAULT_URL = "wss://openws.work.weixin.qq.com";
    private static final long[] RECONNECT_DELAYS = {1, 2, 4, 8, 16, 30};

    private final WeComChannel.Config config;
    private final InboundMessageSink sink;
    private final BiConsumer<String, WeComGatewaySession> onClose;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final StringBuilder fragments = new StringBuilder();
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile boolean authenticated;
    private volatile String lastError;
    private volatile int missedHeartbeatAcks;
    private int reconnectAttempt;

    WeComGatewaySession(
            WeComChannel.Config config, InboundMessageSink sink, BiConsumer<String, WeComGatewaySession> onClose) {
        this.config = config;
        this.sink = sink;
        this.onClose = onClose;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wecom-aibot-" + config.accountId());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.execute(this::connect);
    }

    private void connect() {
        if (closed.get()) return;
        reconnectScheduled.set(false);
        authenticated = false;
        String endpoint = config.wsUrl() == null || config.wsUrl().isBlank() ? DEFAULT_URL : config.wsUrl();
        try {
            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .header("User-Agent", "spring-agentstart-wecom-aibot/0.2.0")
                    .buildAsync(URI.create(endpoint), this)
                    .whenComplete((webSocket, failure) -> {
                        if (failure != null) {
                            fail("企业微信长连接建立失败: " + message(failure), failure);
                            scheduleReconnect();
                        } else {
                            socket = webSocket;
                        }
                    });
        } catch (RuntimeException failure) {
            fail("企业微信长连接地址无效: " + message(failure), failure);
            scheduleReconnect();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        webSocket.request(1);
        send(
                webSocket,
                Map.of(
                        "cmd", "aibot_subscribe",
                        "headers", Map.of("req_id", requestId("aibot_subscribe")),
                        "body",
                                Map.of(
                                        "bot_id", config.resolvedBotId(),
                                        "secret", config.resolvedSecret())));
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        fragments.append(data);
        if (last) {
            String payload = fragments.toString();
            fragments.setLength(0);
            try {
                handle(json.readValue(payload, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception failure) {
                fail("企业微信长连接消息解析失败: " + message(failure), failure);
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void handle(Map<String, Object> frame) {
        String command = text(frame, "cmd");
        Map<String, Object> headers = map(frame.get("headers"));
        String requestId = text(headers, "req_id");
        int errorCode = number(frame.get("errcode"), 0);
        if (requestId != null && requestId.startsWith("aibot_subscribe")) {
            if (errorCode != 0) {
                lastError = authenticationError(errorCode, text(frame, "errmsg"));
                authenticated = false;
                WebSocket current = socket;
                if (current != null) current.abort();
                scheduleReconnect();
                return;
            }
            authenticated = true;
            lastError = null;
            reconnectAttempt = 0;
            missedHeartbeatAcks = 0;
            startHeartbeat();
            LOG.log(System.Logger.Level.INFO, "[{0}] WeCom AI Bot authenticated", config.accountId());
            return;
        }
        if (requestId != null && requestId.startsWith("ping")) {
            if (errorCode == 0) missedHeartbeatAcks = 0;
            return;
        }
        if ("aibot_msg_callback".equals(command)) {
            dispatch(frame);
            return;
        }
        if ("aibot_event_callback".equals(command)) {
            Map<String, Object> event = map(map(frame.get("body")).get("event"));
            if ("disconnected_event".equals(text(event, "eventtype"))) {
                lastError = "该 Bot ID 已由另一个实例建立长连接";
                close();
            }
        }
    }

    private static String authenticationError(int errorCode, String platformMessage) {
        if (errorCode == 853000)
            return "企业微信认证失败：Bot ID 或 Secret 无效。请使用「智能机器人」详情页的 Bot ID/Secret，"
                    + "不要填写 Corp ID、自建应用 Secret 或群机器人 Webhook Key。平台信息: "
                    + platformMessage + " (853000)";
        return "企业微信认证失败: " + platformMessage + " (" + errorCode + ")";
    }

    private void dispatch(Map<String, Object> frame) {
        Map<String, Object> body = map(frame.get("body"));
        Map<String, Object> from = map(body.get("from"));
        String messageId = text(body, "msgid");
        String sender = text(from, "userid");
        String chatType = text(body, "chattype");
        boolean group = "group".equalsIgnoreCase(chatType);
        String conversation = group ? text(body, "chatid") : sender;
        if (messageId == null || sender == null || conversation == null) return;

        List<MessageContent> contents = contents(body);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("transport", "websocket");
        String requestId = text(map(frame.get("headers")), "req_id");
        if (requestId != null) metadata.put("wecomReqId", requestId);
        if (requestId != null) {
            metadata.put(ChannelReplyStream.METADATA_KEY, new WeComReplyStream(requestId));
        }
        metadata.put("raw", body);
        InboundMessage message = new InboundMessage(
                messageId,
                "wecom",
                config.accountId(),
                conversation,
                group ? ConversationType.GROUP : ConversationType.DIRECT,
                sender,
                sender,
                conversation,
                contents,
                timestamp(body.get("create_time")),
                metadata);
        Thread.ofVirtual().name("wecom-aibot-inbound-" + config.accountId()).start(() -> {
            try {
                sink.accept(message);
            } catch (RuntimeException failure) {
                fail("企业微信入站消息处理失败: " + message(failure), failure);
            }
        });
    }

    SendResult send(OutboundMessage message) {
        WebSocket current = socket;
        if (!authenticated || current == null)
            throw new ChannelException("wecom_not_connected", "企业微信智能机器人长连接尚未就绪", true);
        String requestId = requestId("aibot_send_msg");
        send(
                current,
                Map.of(
                        "cmd", "aibot_send_msg",
                        "headers", Map.of("req_id", requestId),
                        "body",
                                Map.of(
                                        "chatid", message.targetId(),
                                        "msgtype", "markdown",
                                        "markdown", Map.of("content", outboundText(message)))));
        return SendResult.accepted(requestId);
    }

    private void sendStream(String callbackRequestId, String streamId, String content, boolean finish) {
        WebSocket current = socket;
        if (!authenticated || current == null)
            throw new ChannelException("wecom_not_connected", "企业微信智能机器人长连接尚未就绪", true);
        send(current, streamFrame(callbackRequestId, streamId, content, finish));
    }

    static Map<String, Object> streamFrame(String callbackRequestId, String streamId, String content, boolean finish) {
        Map<String, Object> stream = new LinkedHashMap<>();
        stream.put("id", streamId);
        stream.put("finish", finish);
        stream.put("content", limitUtf8(content, 20_480));
        return Map.of(
                "cmd", "aibot_respond_msg",
                "headers", Map.of("req_id", callbackRequestId),
                "body", Map.of("msgtype", "stream", "stream", stream));
    }

    /** One callback-bound stream. WeCom replaces the displayed text with each full content value. */
    private final class WeComReplyStream implements ChannelReplyStream {
        private static final long MIN_UPDATE_NANOS = TimeUnit.MILLISECONDS.toNanos(80);
        private final String callbackRequestId;
        private final String streamId = requestId("stream");
        private final StringBuilder content = new StringBuilder();
        private boolean started;
        private boolean finished;
        private long lastUpdate;

        private WeComReplyStream(String callbackRequestId) {
            this.callbackRequestId = callbackRequestId;
        }

        @Override
        public synchronized void start() {
            if (started || finished) return;
            sendStream(callbackRequestId, streamId, "正在思考…", false);
            started = true;
            lastUpdate = System.nanoTime();
        }

        @Override
        public synchronized void push(String chunk) {
            if (finished || chunk == null || chunk.isEmpty()) return;
            if (!started) start();
            content.append(chunk);
            long now = System.nanoTime();
            if (now - lastUpdate >= MIN_UPDATE_NANOS) {
                sendStream(callbackRequestId, streamId, content.toString(), false);
                lastUpdate = now;
            }
        }

        @Override
        public synchronized void complete(String finalContent) {
            if (finished) return;
            if (!started) start();
            String answer = finalContent == null || finalContent.isBlank() ? content.toString() : finalContent;
            sendStream(callbackRequestId, streamId, answer, true);
            finished = true;
        }

        @Override
        public synchronized void fail(String message) {
            if (finished) return;
            if (!started) start();
            String detail = message == null || message.isBlank() ? "工作流执行失败，请稍后重试" : message;
            sendStream(callbackRequestId, streamId, detail, true);
            finished = true;
        }
    }

    private void startHeartbeat() {
        ScheduledFuture<?> existing = heartbeat;
        if (existing != null) existing.cancel(false);
        heartbeat = scheduler.scheduleAtFixedRate(this::heartbeat, 30, 30, TimeUnit.SECONDS);
    }

    private void heartbeat() {
        if (closed.get()) return;
        if (missedHeartbeatAcks >= 3) {
            lastError = "企业微信长连接心跳超时";
            WebSocket current = socket;
            if (current != null) current.abort();
            return;
        }
        WebSocket current = socket;
        if (current != null) {
            missedHeartbeatAcks++;
            send(current, Map.of("cmd", "ping", "headers", Map.of("req_id", requestId("ping"))));
        }
    }

    private synchronized void scheduleReconnect() {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) return;
        authenticated = false;
        ScheduledFuture<?> existing = heartbeat;
        if (existing != null) existing.cancel(false);
        long delay = RECONNECT_DELAYS[Math.min(reconnectAttempt++, RECONNECT_DELAYS.length - 1)];
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private void send(WebSocket webSocket, Object value) {
        try {
            webSocket.sendText(json.writeValueAsString(value), true);
        } catch (Exception failure) {
            throw new ChannelException("wecom_send_failed", message(failure), true, failure);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        authenticated = false;
        if (!closed.get()) {
            lastError = "企业微信长连接已断开: " + statusCode + " " + reason;
            scheduleReconnect();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        authenticated = false;
        fail("企业微信长连接异常: " + message(error), error);
        scheduleReconnect();
    }

    @Override
    public boolean connected() {
        return authenticated;
    }

    String lastError() {
        return lastError;
    }

    boolean uses(WeComChannel.Config candidate) {
        return Objects.equals(config.resolvedBotId(), candidate.resolvedBotId())
                && Objects.equals(config.resolvedSecret(), candidate.resolvedSecret())
                && Objects.equals(config.wsUrl(), candidate.wsUrl());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        authenticated = false;
        ScheduledFuture<?> existing = heartbeat;
        if (existing != null) existing.cancel(false);
        WebSocket current = socket;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "account stopped");
        scheduler.shutdownNow();
        onClose.accept(config.accountId(), this);
    }

    static List<MessageContent> contents(Map<String, Object> body) {
        String type = text(body, "msgtype");
        if ("text".equals(type)) return List.of(MessageContent.text(text(map(body.get("text")), "content")));
        if ("voice".equals(type)) return List.of(MessageContent.text(text(map(body.get("voice")), "content")));
        if ("mixed".equals(type)) {
            List<MessageContent> result = new ArrayList<>();
            Object items = map(body.get("mixed")).get("msg_item");
            if (items instanceof Collection<?> collection) {
                for (Object item : collection) {
                    Map<String, Object> value = map(item);
                    if ("text".equals(text(value, "msgtype")))
                        result.add(MessageContent.text(text(map(value.get("text")), "content")));
                }
            }
            return result.isEmpty() ? List.of(MessageContent.text("")) : List.copyOf(result);
        }
        Map<String, Object> media = map(body.get(type));
        String url = text(media, "url");
        MessageType messageType =
                switch (String.valueOf(type)) {
                    case "image" -> MessageType.IMAGE;
                    case "file" -> MessageType.FILE;
                    case "video" -> MessageType.VIDEO;
                    case "voice" -> MessageType.AUDIO;
                    default -> MessageType.UNKNOWN;
                };
        return List.of(new MessageContent(messageType, null, url, null, null, null, media));
    }

    private static String outboundText(OutboundMessage message) {
        return message.contents().stream()
                .map(MessageContent::text)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item == null || String.valueOf(item).isBlank() ? null : String.valueOf(item);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Instant timestamp(Object value) {
        if (value instanceof Number number) return Instant.ofEpochSecond(number.longValue());
        return Instant.now();
    }

    private static String requestId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String limitUtf8(String value, int maxBytes) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        int end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(0, end);
    }

    private static String message(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }

    private void fail(String description, Throwable failure) {
        lastError = description;
        LOG.log(System.Logger.Level.WARNING, description, failure);
    }
}
