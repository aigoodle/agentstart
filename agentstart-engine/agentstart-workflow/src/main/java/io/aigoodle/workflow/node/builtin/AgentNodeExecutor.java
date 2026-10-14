package io.aigoodle.workflow.node.builtin;

import io.aigoodle.agent.api.*;
import io.aigoodle.agent.runtime.AgentRuntime;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionMode;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.List;
import java.util.Locale;

/** Workflow adapter for the complete agent runtime (strategy, tools, HITL and memory). */
public class AgentNodeExecutor implements NodeExecutor {
    private final AgentRuntime agentRuntime;
    private final ModelService modelService;

    public AgentNodeExecutor(AgentRuntime agentRuntime) {
        this(agentRuntime, null);
    }

    public AgentNodeExecutor(AgentRuntime agentRuntime, ModelService modelService) {
        this.agentRuntime = agentRuntime;
        this.modelService = modelService;
    }

    @Override
    public NodeType type() {
        return NodeType.AGENT;
    }

    @Override
    public NodeExecutionMode executionMode(NodeDef node) {
        return NodeExecutionMode.SIDE_EFFECT;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        context.throwIfCancelled();
        String runtimeType = node.getString("runtimeType", "NATIVE");
        boolean nativeRuntime = "NATIVE".equalsIgnoreCase(runtimeType);
        String provider = firstNonBlank(node.getString("modelProvider"), node.getString("provider"));
        String model = firstNonBlank(node.getString("modelName"), node.getString("model"));
        if (nativeRuntime && (provider == null || model == null) && modelService != null) {
            String modelId = node.getString("modelId");
            if (modelId != null && !modelId.isBlank()) {
                String runTenant = firstNonBlank(context.resourceTenant(), "default");
                ModelEntity entity = context.withResourceTenant(() -> modelService.require(runTenant, modelId));
                provider = entity.getProviderName();
                model = entity.getModelName();
            }
        }
        if (nativeRuntime && (provider == null || model == null)) {
            return NodeResult.failure("Agent node requires modelProvider + modelName");
        }
        try {
            AgentDefinition definition = AgentDefinition.builder()
                    .id("workflow:" + node.getId())
                    .tenantId(firstNonBlank(context.getTenantId(), "default"))
                    .resourceTenantId(context.resourceTenant())
                    .name(firstNonBlank(node.getTitle(), node.getId()))
                    .runtimeType(runtimeType)
                    .runtimeRef(node.getString("runtimeRef"))
                    .instructions(VariableResolver.render(
                            node.getString("systemPrompt", "You are a helpful assistant. Use tools when helpful."),
                            context.getPool()))
                    .modelProvider(provider)
                    .modelName(model)
                    .strategy(strategy(node.getString("strategy", "react")))
                    .toolNames(toolNames(node))
                    .skillIds(stringList(node.get("skillIds")))
                    .maxIterations(node.getInt("maxIterations", 6))
                    .memoryEnabled(booleanValue(node.get("memoryEnabled"), true))
                    .memoryWindow(node.getInt("memoryWindow", 20))
                    .build();
            String query = VariableResolver.render(node.getString("query", "{{#sys.query#}}"), context.getPool());
            AgentResponse response = io.aigoodle.common.context.UserContextHolder.callAs(
                    io.aigoodle.common.context.CurrentUser.builder()
                            .tenantId(definition.getTenantId())
                            .userId(context.getUserId())
                            .build(),
                    () -> agentRuntime.run(
                            definition,
                            AgentRequest.builder()
                                    .query(query)
                                    .conversationId(context.getConversationId())
                                    .variables(context.getInputs())
                                    .build()));
            context.throwIfCancelled();
            if (response.getStatus() != AgentResponse.Status.COMPLETED) {
                return NodeResult.failure("Agent run ended with status " + response.getStatus());
            }
            return NodeResult.of("text", response.getText())
                    .output("conversationId", response.getConversationId())
                    .output("steps", response.getSteps());
        } catch (RuntimeException exception) {
            return NodeResult.failure("Agent node failed: " + exception.getMessage());
        }
    }

    private static AgentStrategyType strategy(String value) {
        try {
            return AgentStrategyType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return AgentStrategyType.REACT;
        }
    }

    private static List<String> toolNames(NodeDef node) {
        Object tools = node.get("tools");
        if (!(tools instanceof List<?> list)) return List.of();
        return list.stream()
                .map(item -> item instanceof java.util.Map<?, ?> map
                        ? firstNonBlank(string(map.get("name")), string(map.get("toolName")))
                        : string(item))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .map(AgentNodeExecutor::string)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null
                ? fallback
                : value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
