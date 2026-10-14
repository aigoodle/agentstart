package io.aigoodle.web.support;

import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/** Resolves configured tool names into the descriptors shown by the agent console. */
public final class AppToolViewMapper {

    private final AppService appService;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;

    public AppToolViewMapper(AppService appService, ObjectProvider<ToolRegistry> toolRegistryProvider) {
        this.appService = appService;
        this.toolRegistryProvider = toolRegistryProvider;
    }

    public List<Map<String, Object>> toolsOf(String tenantId, String agentId) {
        appService.require(tenantId, agentId);
        AppModelConfigEntity modelConfig = appService.getModelConfig(tenantId, agentId);
        List<String> configuredToolNames = configuredToolNames(modelConfig);
        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        if (configuredToolNames.isEmpty() || toolRegistry == null) {
            return List.of();
        }

        List<Map<String, Object>> toolViews = new ArrayList<>();
        for (String toolName : configuredToolNames) {
            if (toolRegistry.has(toolName)) {
                toolViews.add(toView(toolRegistry.get(toolName)));
            }
        }
        return toolViews;
    }

    private static List<String> configuredToolNames(AppModelConfigEntity modelConfig) {
        if (modelConfig == null) {
            return List.of();
        }
        List<String> toolNames = JsonUtils.parseList(modelConfig.getToolNamesJson(), String.class);
        return toolNames == null ? List.of() : toolNames;
    }

    private static Map<String, Object> toView(ToolDefinition tool) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", tool.name());
        view.put("description", tool.description());
        view.put("inputSchema", tool.inputSchema());
        return view;
    }
}
