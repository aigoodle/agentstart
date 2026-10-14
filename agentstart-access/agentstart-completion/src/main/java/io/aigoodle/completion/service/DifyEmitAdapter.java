package io.aigoodle.completion.service;

import io.aigoodle.completion.common.SseBridge;
import java.util.Map;
import java.util.UUID;

/** Translates internal OpenAI-style events into the Dify chat-messages protocol. */
final class DifyEmitAdapter implements SseBridge.Emit {

    private final SseBridge.Emit delegate;
    private final DifyEventPayloadFactory payloadFactory;

    private boolean messageContentEmitted;

    DifyEmitAdapter(SseBridge.Emit delegate, String taskId, String conversationId) {
        this.delegate = delegate;
        this.payloadFactory = new DifyEventPayloadFactory(
                taskId, conversationId, UUID.randomUUID().toString());
    }

    @Override
    public void event(String eventName, Object eventData) {
        if (eventName == null) {
            return;
        }
        switch (eventName) {
            case "message" -> emitMessage(eventData);
            case "message_end" -> emit(payloadFactory.messageEnd(eventData, messageContentEmitted));
            case "error" -> emit(payloadFactory.error(eventData));
            case "chat_started" -> emitWrapped("workflow_started", eventData);
            case "chat_finished" -> emitWrapped("workflow_finished", eventData);
            case "step" -> emitWrapped("agent_step", eventData);
            case "workflow_started", "workflow_finished", "node_started", "node_finished" -> emitWrapped(
                    eventName, eventData);
            default -> emitWrapped(eventName, eventData);
        }
    }

    private void emitMessage(Object eventData) {
        String content = OpenAIChunkContentExtractor.extract(eventData);
        if (content == null || content.isEmpty()) {
            return;
        }
        emit(payloadFactory.message(content));
        messageContentEmitted = true;
    }

    private void emitWrapped(String eventName, Object eventData) {
        emit(payloadFactory.wrapped(eventName, eventData));
    }

    private void emit(Map<String, Object> payload) {
        delegate.event("message", payload);
    }
}
