package io.aigoodle.runtime.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.ConflictPolicy;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.strategy.AgentChatOptionsFactory;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.runtime.ModelInstance;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.CurrentUserToolExecutionContextProvider;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Builds a call-owned SDK agent from the platform's tenant-scoped model and tool catalog. */
public final class DefaultAgentScopeAgentFactory implements AgentScopeAgentFactory {
    private final ModelService models;
    private final ToolRegistry tools;
    private final SkillResolver skills;
    private final AgentStateStore stateStore;
    private final java.util.function.Function<AgentDefinition, java.util.List<io.aigoodle.tool.ToolDefinition>> catalog;

    public DefaultAgentScopeAgentFactory(ModelService models, ToolRegistry tools, SkillResolver skills) {
        this(models, tools, skills, new InMemoryAgentStateStore());
    }

    public DefaultAgentScopeAgentFactory(
            ModelService models, ToolRegistry tools, SkillResolver skills, AgentStateStore stateStore) {
        this(models, tools, skills, stateStore, null);
    }

    public DefaultAgentScopeAgentFactory(
            ModelService models,
            ToolRegistry tools,
            SkillResolver skills,
            AgentStateStore stateStore,
            java.util.function.Function<AgentDefinition, java.util.List<io.aigoodle.tool.ToolDefinition>> catalog) {
        this.models = Objects.requireNonNull(models, "models");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.catalog = catalog;
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        if (!stateStore.supportsVersioning()) {
            throw new IllegalArgumentException(
                    "AgentScope state store must support versioned writes for shared sessions");
        }
    }

    @Override
    public ReActAgent create(AgentDefinition definition, AgentRequest request, RuntimeContext context) {
        if (!definition.getDelegateAgentIds().isEmpty() && catalog == null) {
            throw new PlatformException(
                    "agentscope_delegation_unavailable", "AgentScope delegation is not configured", null);
        }
        String tenant = context.get("tenantId");
        var principal = UserContextHolder.get();
        var identity = new CurrentUserToolExecutionContextProvider().currentContext();
        if (!tenant.equals(identity.tenantId())) throw new SecurityException("AgentScope caller tenant mismatch");
        var execution = new ToolExecutionContext(
                context.get("runId"), tenant, identity.ownerId(), context.get("conversationId"), identity.metadata());
        var skill = skills.resolve(tenant, definition.getSkillIds());
        String prompt = definition.getInstructions() == null ? "" : definition.getInstructions();
        if (skill.prompt() != null && !skill.prompt().isBlank()) prompt += "\n\n" + skill.prompt();
        var names = new LinkedHashSet<>(definition.getToolNames());
        names.addAll(skill.toolNames());
        if (names.isEmpty()) names.addAll(tools.names());
        ToolRegistry executionTools = tools;
        if (!definition.getDelegateAgentIds().isEmpty()) {
            if (definition.getId() != null && definition.getDelegateAgentIds().contains(definition.getId())) {
                throw new IllegalArgumentException("An agent cannot delegate to itself");
            }
            var resolved = catalog.apply(definition.toBuilder()
                    .tenantId(tenant)
                    .toolNames(java.util.List.copyOf(names))
                    .build());
            executionTools = tools.scoped(resolved);
            names = new LinkedHashSet<>(executionTools.names());
        }
        Toolkit toolkit = new Toolkit();
        Integer usedModels = context.get("usedModelCalls");
        Integer usedTools = context.get("usedToolCalls");
        var budget = new AgentScopeCallBudget(
                definition.getMaxModelCalls(),
                definition.getMaxToolCalls(),
                usedModels == null ? 0 : usedModels,
                usedTools == null ? 0 : usedTools);
        context.put("callBudget", budget);
        for (String name : names)
            toolkit.registerAgentTool(
                    new GovernedAgentScopeTool(executionTools, name, principal, execution, budget::toolCall));
        var permissions = io.agentscope.core.permission.PermissionContextState.builder()
                .mode(io.agentscope.core.permission.PermissionMode.DEFAULT);
        for (String name : names) {
            var behavior = definition.getApprovalRequiredTools().contains(name)
                    ? io.agentscope.core.permission.PermissionBehavior.ASK
                    : io.agentscope.core.permission.PermissionBehavior.ALLOW;
            var rule = new io.agentscope.core.permission.PermissionRule(name, null, behavior, "agentstart");
            if (behavior == io.agentscope.core.permission.PermissionBehavior.ASK) permissions.addAskRule(name, rule);
            else permissions.addAllowRule(name, rule);
        }
        String resourceTenant = definition.getResourceTenantId();
        if (resourceTenant == null || resourceTenant.isBlank()) resourceTenant = tenant;
        final String modelTenant = resourceTenant;
        ModelInstance instance = principal != null && !tenant.equals(modelTenant)
                ? UserContextHolder.callAs(
                        principal.toBuilder().tenantId(modelTenant).build(),
                        () -> resolveModel(modelTenant, definition))
                : resolveModel(modelTenant, definition);
        return ReActAgent.builder()
                .permissionContext(permissions.build())
                .conflictPolicy(ConflictPolicy.FAIL)
                .stateStore(definition.isMemoryEnabled() ? stateStore : new InMemoryAgentStateStore())
                .name(definition.getName() == null ? "agent" : definition.getName())
                .sysPrompt(prompt)
                .maxIters(definition.getMaxIterations())
                .toolkit(toolkit)
                .model(new SpringAiAgentScopeModel(
                        instance.getChatModel(),
                        instance.getEndpoint().getModelName(),
                        AgentChatOptionsFactory.build(definition),
                        () -> budget.modelCall(definition.getMaxIterations())))
                .build();
    }

    private ModelInstance resolveModel(String tenant, AgentDefinition definition) {
        if (definition.getModelName() == null || definition.getModelName().isBlank()) {
            return models.getDefaultInstance(tenant, ModelType.LLM);
        }
        var entity = models.findOrMaterialize(
                tenant, definition.getModelProvider(), definition.getModelName(), ModelType.LLM);
        return models.getModelInstance(tenant, entity.getId());
    }
}
