package io.aigoodle.runtime.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;

/** Creates a call-owned AgentScope instance; the adapter always closes it after execution. */
@FunctionalInterface
public interface AgentScopeAgentFactory {
    ReActAgent create(AgentDefinition definition, AgentRequest request, RuntimeContext context);
}
