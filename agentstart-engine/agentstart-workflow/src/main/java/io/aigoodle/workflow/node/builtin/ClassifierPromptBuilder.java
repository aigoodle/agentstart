package io.aigoodle.workflow.node.builtin;

import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.graph.NodeDef;
import java.util.Map;

/** Selects and renders either a reusable classifier template or the built-in prompt. */
final class ClassifierPromptBuilder {

    private final PromptTemplateService promptTemplateService;

    ClassifierPromptBuilder(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    String build(NodeDef node, ClassifierCategorySet categorySet) {
        String categoryMenu = categorySet.menu();
        PromptTemplateEntity promptTemplate = findConfiguredTemplate(node);
        if (promptTemplate != null) {
            return promptTemplateService.render(promptTemplate.getContent(), Map.of("categories", categoryMenu));
        }
        return defaultPrompt(categoryMenu);
    }

    private PromptTemplateEntity findConfiguredTemplate(NodeDef node) {
        String templateId = node.getString("systemPromptTemplateId");
        if (promptTemplateService == null || templateId == null || templateId.isBlank()) {
            return null;
        }
        return promptTemplateService.get(io.aigoodle.common.context.UserContextHolder.currentTenantId(), templateId);
    }

    private static String defaultPrompt(String categoryMenu) {
        return "You are a precise text classifier. Choose exactly ONE category that best matches "
                + "the user input. Reply with ONLY the category id, nothing else.\nCategories:\n"
                + categoryMenu;
    }
}
