package io.aigoodle.plugin.agent;

import io.aigoodle.agent.api.*;
import io.aigoodle.agent.runtime.AgentRuntimeExtension;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.*;
import io.aigoodle.connector.execution.*;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.tool.execution.ToolExecutionContextProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Explicit PLUGIN Agent backend. runtimeRef is pluginId/actionId; action must opt in. */
public final class PluginAgentRuntime implements AgentRuntimeExtension {
    private final Supplier<ConnectorExecutionGateway> gateway;
    private final Supplier<ConnectorRegistry> registry;
    private final ToolExecutionContextProvider identityProvider;

    public PluginAgentRuntime(
            Supplier<ConnectorExecutionGateway> gateway,
            Supplier<ConnectorRegistry> registry,
            ToolExecutionContextProvider identityProvider) {
        this.gateway = gateway;
        this.registry = registry;
        this.identityProvider = identityProvider;
    }

    @Override
    public String runtimeType() {
        return "PLUGIN";
    }

    @Override
    public AgentResponse run(
            AgentDefinition definition, AgentRequest request, Consumer<AgentStep> steps, Consumer<String> tokens) {
        String ref = definition.getRuntimeRef();
        if (ref == null || ref.split("/", -1).length != 2)
            throw new ConnectorException("plugin_agent_reference", "runtimeRef must be pluginId/actionId");
        var caller = identityProvider.currentContext();
        String tenant =
                definition.getTenantId() == null || definition.getTenantId().isBlank()
                        ? "default"
                        : definition.getTenantId();
        if (!tenant.equals(caller.tenantId()))
            throw new ConnectorException("plugin_agent_tenant_mismatch", "Agent and caller tenants differ");
        String[] parts = ref.split("/", -1);
        var key = new ConnectorKey("plugin", parts[0]);
        var action = registry.get().get(key).action(parts[1]);
        if (!Boolean.TRUE.equals(action.metadata().get("agentRuntime")))
            throw new ConnectorException("plugin_agent_action_denied", "Action does not expose an Agent runtime");
        var attributes = new LinkedHashMap<String, Object>();
        if (request.getConversationId() != null) attributes.put("conversationId", request.getConversationId());
        var context = new ConnectorExecutionContext(
                UUID.randomUUID().toString(),
                tenant,
                caller.ownerId(),
                definition.getId(),
                null,
                null,
                null,
                attributes);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", request.getQuery() == null ? "" : request.getQuery());
        inputs.put("variables", request.getVariables() == null ? Map.of() : request.getVariables());
        // Credentials and connection selection belong to deployment/installation, never model variables.
        var result = gateway.get().execute(new ConnectorExecutionRequest(key, parts[1], null, null, inputs, context));
        var response = AgentResponse.forConversation(request.getConversationId());
        if (!result.success()) {
            response.setStatus(AgentResponse.Status.FAILED);
            response.setError(
                    result.error() == null
                            ? "Plugin execution failed"
                            : result.error().message());
            return response;
        }
        Object data = result.data();
        if (result.metadata().get("pluginTask") != null)
            return response.complete(JsonUtils.toJson(
                    Map.of("status", "PENDING", "task", result.metadata().get("pluginTask"))));
        String text = data instanceof String value
                ? value
                : data instanceof Map<?, ?> map && map.get("text") instanceof String value
                        ? value
                        : JsonUtils.toJson(data);
        return response.complete(text);
    }
}
