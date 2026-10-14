package io.aigoodle.agent.service;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.tool.ToolDefinition;
import java.util.List;
import java.util.function.BiFunction;

/** Resolves the platform tools and tenant-owned delegations exposed to an execution backend. */
@FunctionalInterface
public interface AgentToolCatalog {
    List<ToolDefinition> resolve(AgentDefinition definition, BiFunction<String, AgentRequest, AgentResponse> runner);
}
