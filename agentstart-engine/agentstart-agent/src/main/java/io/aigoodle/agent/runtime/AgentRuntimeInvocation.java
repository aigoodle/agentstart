package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentStep;
import java.util.function.Consumer;

/** Immutable invocation envelope shared by native and optional Agent runtimes. */
public record AgentRuntimeInvocation(
        String runtimeType,
        AgentDefinition definition,
        AgentRequest request,
        Consumer<AgentStep> stepListener,
        Consumer<String> tokenListener) {}
