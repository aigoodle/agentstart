package io.aigoodle.channel.feishu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.PlatformMessages;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** One official Feishu long-connection client for one configured employee-owned bot account. */
final class FeishuStreamSession implements ChannelSession {
    private static final System.Logger LOG = System.getLogger(FeishuStreamSession.class.getName());

    private final com.lark.oapi.ws.Client client;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;

    FeishuStreamSession(FeishuChannel.Config config, InboundMessageSink sink, ObjectMapper json) {
        EventDispatcher dispatcher = EventDispatcher.newBuilder(
                        value(config.verificationToken()), value(config.encryptKey()))
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        dispatch(config, sink, json, event);
                    }
                })
                .build();
        client = new com.lark.oapi.ws.Client.Builder(config.appId(), config.appSecret())
                .eventHandler(dispatcher)
                .autoReconnect(true)
                .build();
        worker = Thread.ofVirtual().name("feishu-stream-" + config.accountId()).start(() -> {
            try {
                client.start();
                connected.set(true);
                LOG.log(System.Logger.Level.INFO, "[{0}] Feishu stream started", config.accountId());
            } catch (RuntimeException failure) {
                connected.set(false);
                LOG.log(System.Logger.Level.ERROR, "Feishu stream start failed", failure);
            }
        });
    }

    private static void dispatch(
            FeishuChannel.Config config, InboundMessageSink sink, ObjectMapper json, P2MessageReceiveV1 event) {
        if (event == null || event.getEvent() == null) return;
        EventMessage message = event.getEvent().getMessage();
        EventSender senderValue = event.getEvent().getSender();
        if (message == null || senderValue == null || senderValue.getSenderId() == null) return;
        String sender = first(
                senderValue.getSenderId().getOpenId(),
                senderValue.getSenderId().getUserId(),
                senderValue.getSenderId().getUnionId());
        String chat = message.getChatId();
        if (message.getMessageId() == null || sender == null || chat == null) return;
        String text = content(json, message.getContent());
        InboundMessage inbound = PlatformMessages.inbound(
                "feishu",
                config.accountId(),
                message.getMessageId(),
                chat,
                sender,
                chat,
                text,
                "group".equalsIgnoreCase(message.getChatType()),
                Map.of(
                        "messageType", value(message.getMessageType()),
                        "chatType", value(message.getChatType()),
                        "eventId",
                                event.getHeader() == null
                                        ? ""
                                        : value(event.getHeader().getEventId())));
        InboundReceipt receipt = sink.accept(inbound);
        if (!receipt.accepted()) {
            throw new ChannelException("inbound_" + receipt.code(), "Feishu inbound message was not accepted", true);
        }
    }

    private static String content(ObjectMapper json, String raw) {
        if (raw == null) return "";
        try {
            Map<String, Object> value = json.readValue(raw, new TypeReference<>() {});
            Object text = value.get("text");
            return text == null ? raw : String.valueOf(text);
        } catch (Exception ignored) {
            return raw;
        }
    }

    @Override
    public boolean connected() {
        return connected.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        connected.set(false);
        worker.interrupt();
        // The official SDK currently exposes disconnect as protected. Invoking it here prevents a
        // replaced/deleted account from leaving an orphan WebSocket behind.
        try {
            var disconnect = client.getClass().getDeclaredMethod("disconnect");
            disconnect.setAccessible(true);
            disconnect.invoke(client);
        } catch (ReflectiveOperationException failure) {
            LOG.log(System.Logger.Level.WARNING, "Cannot close Feishu stream cleanly", failure);
        }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
