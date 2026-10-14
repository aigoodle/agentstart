package io.aigoodle.trigger.dispatch;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs a trigger's target. The built-in dispatcher runs workflows; publish another
 * bean (e.g. one that runs an agent) to add a new {@code targetType} — the trigger
 * module stays decoupled from the agent module this way.
 */
public interface TriggerDispatcher {

    /** The {@code targetType} this dispatcher handles, e.g. {@code "workflow"}. */
    String targetType();

    DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId);

    /** Dispatches with an optional live text consumer. Non-streaming targets keep old behavior. */
    default DispatchResult dispatch(
            String targetId, Map<String, Object> inputs, String conversationId, Consumer<String> textConsumer) {
        return dispatch(targetId, inputs, conversationId);
    }
}
