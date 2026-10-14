package io.aigoodle.runtime.agentscope;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.context.UserContextHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

/** Carries the trusted delegation ancestry with the caller across SDK worker threads. */
final class PublishedAgentDelegation {
    static final String PATH_ATTRIBUTE = "agentstart.delegationPath";
    private static final int MAX_DEPTH = 16;

    private PublishedAgentDelegation() {}

    static AgentResponse run(
            AgentDefinition parent,
            String target,
            AgentRequest request,
            BiFunction<String, AgentRequest, AgentResponse> runner) {
        var caller = UserContextHolder.require();
        var path = new ArrayList<String>();
        Object ancestry = caller.attr(PATH_ATTRIBUTE);
        if (ancestry != null) {
            if (!(ancestry instanceof List<?> values)
                    || values.stream().anyMatch(value -> !(value instanceof String))) {
                throw new IllegalArgumentException("Invalid trusted delegation ancestry");
            }
            for (Object value : (List<?>) ancestry) path.add((String) value);
        }
        if (path.isEmpty() && parent.getId() != null) path.add(parent.getId());
        if (path.contains(target)) throw new IllegalArgumentException("Agent delegation cycle detected");
        if (path.size() >= MAX_DEPTH) throw new IllegalArgumentException("Agent delegation depth exceeded");
        path.add(target);
        var extra = new HashMap<>(caller.getExtra());
        extra.put(PATH_ATTRIBUTE, List.copyOf(path));
        return UserContextHolder.callAs(caller.toBuilder().extra(extra).build(), () -> runner.apply(target, request));
    }
}
