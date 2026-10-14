package io.aigoodle.workflow.node.builtin;

import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** Builds the ordered message list for one LLM workflow-node invocation. */
final class LlmConversationBuilder {

    private static final Logger log = LoggerFactory.getLogger(LlmConversationBuilder.class);

    private final PromptTemplateService promptTemplateService;
    private final MemoryManager conversationMemory;

    LlmConversationBuilder(PromptTemplateService promptTemplateService, MemoryManager conversationMemory) {
        this.promptTemplateService = promptTemplateService;
        this.conversationMemory = conversationMemory;
    }

    List<Message> build(NodeDef node, ExecutionContext context) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = resolveSystemPrompt(node, context);
        if (!systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        appendConversationMemory(node, context, messages);

        String userTemplate = node.getString("userPrompt", node.getString("prompt", ""));
        String userPrompt = VariableResolver.render(userTemplate, context.getPool());
        messages.add(new UserMessage(userPrompt));
        return messages;
    }

    private String resolveSystemPrompt(NodeDef node, ExecutionContext context) {
        String templateId = node.getString("systemPromptTemplateId");
        if (templateId != null && !templateId.isBlank() && promptTemplateService != null) {
            var template = context.withResourceTenant(() -> promptTemplateService.get(
                    io.aigoodle.common.context.UserContextHolder.currentTenantId(), templateId));
            if (template != null) {
                return promptTemplateService.render(template.getContent(), flattenVariables(context));
            }
        }
        return VariableResolver.render(node.getString("systemPrompt", ""), context.getPool());
    }

    private static Map<String, Object> flattenVariables(ExecutionContext context) {
        Map<String, Object> flattenedVariables = new HashMap<>();
        Map<String, Map<String, Object>> variablesByNamespace =
                context.getPool().snapshot();
        Map<String, Object> systemVariables = variablesByNamespace.get("sys");
        if (systemVariables != null) {
            flattenedVariables.putAll(systemVariables);
        }
        for (Map.Entry<String, Map<String, Object>> namespace : variablesByNamespace.entrySet()) {
            if (!"sys".equals(namespace.getKey())) {
                flattenedVariables.putAll(namespace.getValue());
            }
        }
        return flattenedVariables;
    }

    private void appendConversationMemory(NodeDef node, ExecutionContext context, List<Message> messages) {
        try {
            messages.addAll(WorkflowMemoryMessages.load(conversationMemory, node, context));
        } catch (Exception exception) {
            log.warn(
                    "Memory load skipped for node {} / conversation {}: {}",
                    node.getId(),
                    context.getConversationId(),
                    exception.getMessage());
        }
    }
}
