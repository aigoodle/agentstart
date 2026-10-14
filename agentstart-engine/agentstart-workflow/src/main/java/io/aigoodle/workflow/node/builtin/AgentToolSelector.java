package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

/** Selects registered tool callbacks using an optional node-level allowlist. */
final class AgentToolSelector {

    private static final Logger log = LoggerFactory.getLogger(AgentToolSelector.class);

    private final List<ToolCallback> availableTools;

    AgentToolSelector(List<ToolCallback> availableTools) {
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }

    List<ToolCallback> selectFor(NodeDef node) {
        Set<String> allowedToolNames = configuredToolNames(node);
        if (allowedToolNames.isEmpty()) {
            return availableTools;
        }

        List<ToolCallback> selectedTools = new ArrayList<>();
        for (ToolCallback availableTool : availableTools) {
            String toolName = readToolName(availableTool);
            if (toolName != null && allowedToolNames.contains(toolName)) {
                selectedTools.add(availableTool);
            }
        }
        return List.copyOf(selectedTools);
    }

    private static Set<String> configuredToolNames(NodeDef node) {
        Object configuredTools = node.get("tools");
        if (!(configuredTools instanceof List<?> toolNames)) {
            return Set.of();
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (Object configuredName : toolNames) {
            if (configuredName == null) {
                continue;
            }
            String toolName = String.valueOf(configuredName).trim();
            if (!toolName.isEmpty()) {
                normalizedNames.add(toolName);
            }
        }
        return normalizedNames;
    }

    private static String readToolName(ToolCallback toolCallback) {
        try {
            return toolCallback.getToolDefinition().name();
        } catch (RuntimeException invalidToolMetadata) {
            log.warn("Skipping tool callback with unreadable definition: {}", invalidToolMetadata.getMessage());
            return null;
        }
    }
}
