package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentResponse;

/**
 * Host-extensible policy boundary around every Agent runtime invocation.
 * Implementations can enforce quotas, redact PII, record audit data or add
 * retry/fallback behavior without replacing Agent Start's runtime registry.
 */
public interface AgentRuntimeInterceptor {
    /** Lower values execute first and therefore wrap higher-valued interceptors. */
    default int order() {
        return 0;
    }

    AgentResponse intercept(AgentRuntimeInvocation invocation, Chain chain);

    @FunctionalInterface
    interface Chain {
        AgentResponse proceed(AgentRuntimeInvocation invocation);
    }
}
