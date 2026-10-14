package io.aigoodle.web.dto;

import io.aigoodle.agent.entity.AppEntity;
import java.util.List;
import java.util.Map;

/** Lightweight published workflow application option for designer selectors. */
public record WorkflowAppOption(
        String appId,
        String workflowId,
        String name,
        String icon,
        String iconBackground,
        List<Map<String, Object>> inputVariables) {

    public static WorkflowAppOption from(AppEntity app) {
        return new WorkflowAppOption(
                app.getId(), app.getWorkflowId(), app.getName(), app.getIcon(), app.getIconBackground(), List.of());
    }

    public static WorkflowAppOption from(AppEntity app, List<Map<String, Object>> inputVariables) {
        return new WorkflowAppOption(
                app.getId(),
                app.getWorkflowId(),
                app.getName(),
                app.getIcon(),
                app.getIconBackground(),
                inputVariables == null ? List.of() : inputVariables);
    }
}
