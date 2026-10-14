package io.aigoodle.trigger.dispatch;

import io.aigoodle.common.exception.PlatformException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a {@link TriggerDispatcher} by target type.
 */
public class TriggerDispatcherRegistry {

    private final Map<String, TriggerDispatcher> dispatchersByTargetType;

    public TriggerDispatcherRegistry(List<TriggerDispatcher> dispatcherBeans) {
        Map<String, TriggerDispatcher> registeredDispatchers = new LinkedHashMap<>();
        if (dispatcherBeans != null) {
            for (TriggerDispatcher dispatcher : dispatcherBeans) {
                registeredDispatchers.put(normalize(dispatcher.targetType()), dispatcher);
            }
        }
        this.dispatchersByTargetType = Collections.unmodifiableMap(registeredDispatchers);
    }

    public TriggerDispatcher get(String targetType) {
        String resolvedTargetType = targetType == null ? WorkflowTriggerDispatcher.TARGET_TYPE : targetType;
        TriggerDispatcher dispatcher = dispatchersByTargetType.get(normalize(resolvedTargetType));
        if (dispatcher == null) {
            throw new PlatformException(
                    "dispatcher_not_found",
                    "No trigger dispatcher for target type '" + resolvedTargetType + "' (have: "
                            + dispatchersByTargetType.keySet() + ")",
                    null);
        }
        return dispatcher;
    }

    private static String normalize(String targetType) {
        return targetType.toLowerCase(Locale.ROOT);
    }
}
