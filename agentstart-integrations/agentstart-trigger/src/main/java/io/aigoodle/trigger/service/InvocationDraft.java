package io.aigoodle.trigger.service;

import io.aigoodle.trigger.entity.TriggerInvocationEntity;
import java.util.Map;

/** Values persisted when an invocation history entry is first opened. */
record InvocationDraft(
        String triggerId,
        String source,
        Map<String, Object> payload,
        String conversationId,
        String replayedInvocationId) {

    static InvocationDraft initial(TriggerInvocationRequest request) {
        return new InvocationDraft(
                request.triggerId(), request.source(), request.payload(), request.conversationId(), null);
    }

    static InvocationDraft replay(TriggerInvocationEntity original, Map<String, Object> payload) {
        return new InvocationDraft(
                original.getTriggerId(), "replay", payload, original.getConversationId(), original.getId());
    }
}
