package io.aigoodle.trigger.event;

import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerInvocationRequest;
import io.aigoodle.trigger.service.TriggerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A small in-process event bus: publishing a named event fires every enabled EVENT
 * trigger whose configured {@code eventName} matches.
 */
public class EventTriggerBus {

    private final ObjectProvider<TriggerService> triggerServiceProvider;

    public EventTriggerBus(ObjectProvider<TriggerService> triggerServiceProvider) {
        this.triggerServiceProvider = triggerServiceProvider;
    }

    /** Fire matching triggers asynchronously; returns the started invocation ids. */
    public List<String> publish(String eventName, Map<String, Object> payload) {
        TriggerService triggerService = triggerServiceProvider.getObject();
        List<String> invocationIds = new ArrayList<>();
        for (TriggerEntity trigger : findMatchingTriggers(triggerService, eventName)) {
            invocationIds.add(
                    triggerService.fireAsynchronously(TriggerInvocationRequest.event(trigger.getId(), payload)));
        }
        return invocationIds;
    }

    /** Fire matching triggers synchronously (handy for tests / request-scoped flows). */
    public int publishSync(String eventName, Map<String, Object> payload) {
        TriggerService triggerService = triggerServiceProvider.getObject();
        int invocationCount = 0;
        for (TriggerEntity trigger : findMatchingTriggers(triggerService, eventName)) {
            triggerService.fireSynchronously(TriggerInvocationRequest.event(trigger.getId(), payload));
            invocationCount++;
        }
        return invocationCount;
    }

    private List<TriggerEntity> findMatchingTriggers(TriggerService triggerService, String eventName) {
        if (eventName == null) {
            return List.of();
        }
        List<TriggerEntity> matchingTriggers = new ArrayList<>();
        for (TriggerEntity trigger : triggerService.listEnabledByType(TriggerType.EVENT)) {
            Object configuredEventName = triggerService.config(trigger).get("eventName");
            if (eventName.equals(String.valueOf(configuredEventName))) {
                matchingTriggers.add(trigger);
            }
        }
        return matchingTriggers;
    }
}
