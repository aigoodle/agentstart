package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.multiagent.AgentDelegationTool;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/** Resolves registered tools and tenant-safe agent delegations for a runtime definition. */
public final class AgentToolResolver implements AgentToolCatalog {

    private static final String DEFAULT_TENANT_ID = "default";

    private final AppMapper appMapper;
    private final AppModelConfigService modelConfigService;
    private final ToolRegistry toolRegistry;

    public AgentToolResolver(AppMapper appMapper, AppModelConfigService modelConfigService, ToolRegistry toolRegistry) {
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public List<ToolDefinition> resolve(
            AgentDefinition definition, BiFunction<String, AgentRequest, AgentResponse> agentRunner) {
        List<ToolDefinition> resolvedTools = resolveRegisteredTools(definition.getToolNames());
        for (String delegateAgentId : distinctIds(definition.getDelegateAgentIds())) {
            ToolDefinition delegationTool = createDelegationTool(definition, delegateAgentId, agentRunner);
            if (delegationTool != null) {
                resolvedTools.add(delegationTool);
            }
        }
        return resolvedTools;
    }

    private List<ToolDefinition> resolveRegisteredTools(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return new ArrayList<>(toolRegistry.all());
        }

        List<ToolDefinition> resolvedTools = new ArrayList<>();
        for (String toolName : distinctIds(allowedToolNames)) {
            if (toolRegistry.has(toolName)) {
                resolvedTools.add(toolRegistry.get(toolName));
            }
        }
        return resolvedTools;
    }

    private ToolDefinition createDelegationTool(
            AgentDefinition owner,
            String delegateAgentId,
            BiFunction<String, AgentRequest, AgentResponse> agentRunner) {
        if (Objects.equals(owner.getId(), delegateAgentId)) {
            throw new PlatformException(
                    "invalid_agent_delegation", "Agent cannot delegate to itself: " + delegateAgentId, null);
        }

        AppEntity delegate = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, effectiveTenant(owner.getTenantId()))
                .eq(AppEntity::getId, delegateAgentId)
                .last("LIMIT 1"));
        if (delegate == null) {
            return null;
        }
        requireSameTenant(owner, delegate);

        AppModelConfigEntity delegateConfig = modelConfigService.findByAppId(delegate.getTenantId(), delegateAgentId);
        String displayName = firstText(delegate.getName(), delegateAgentId);
        String toolName = "delegate_to_" + toolNameSegment(displayName, delegateAgentId);
        String description = delegationDescription(displayName, delegateConfig);
        return new AgentDelegationTool(toolName, description, delegateAgentId, agentRunner);
    }

    private static void requireSameTenant(AgentDefinition owner, AppEntity delegate) {
        if (!Objects.equals(effectiveTenant(owner.getTenantId()), effectiveTenant(delegate.getTenantId()))) {
            throw new PlatformException(
                    "delegate_cross_tenant",
                    "Delegate agent " + delegate.getId() + " belongs to a different tenant",
                    null);
        }
    }

    private static String delegationDescription(String displayName, AppModelConfigEntity modelConfig) {
        String description = "Delegate a subtask to the '" + displayName + "' agent.";
        String instructions = modelConfig == null ? null : modelConfig.getPrePrompt();
        return hasText(instructions) ? description + " Instructions: " + instructions : description;
    }

    private static String toolNameSegment(String displayName, String delegateAgentId) {
        String normalizedName = normalizeToolName(displayName);
        if (!normalizedName.isEmpty()) {
            return normalizedName;
        }

        String normalizedId = normalizeToolName(delegateAgentId);
        return !normalizedId.isEmpty()
                ? normalizedId
                : "agent_" + Integer.toUnsignedString(delegateAgentId.hashCode(), 36);
    }

    private static String normalizeToolName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static Set<String> distinctIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> distinctIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (hasText(id)) {
                distinctIds.add(id);
            }
        }
        return distinctIds;
    }

    private static String effectiveTenant(String tenantId) {
        return hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
    }

    private static String firstText(String preferredValue, String fallbackValue) {
        return hasText(preferredValue) ? preferredValue : fallbackValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
