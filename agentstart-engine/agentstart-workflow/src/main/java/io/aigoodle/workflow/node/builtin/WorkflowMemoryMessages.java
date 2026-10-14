package io.aigoodle.workflow.node.builtin;

import io.aigoodle.memory.*;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.List;
import org.springframework.ai.chat.messages.*;

/** Single memory-to-prompt adapter shared by every conversational workflow LLM node. */
final class WorkflowMemoryMessages {
    private static final int MAX_TURNS = 50;

    private WorkflowMemoryMessages() {}

    static List<Message> load(MemoryManager manager, NodeDef node, ExecutionContext context) {
        if (manager == null
                || context.getConversationId() == null
                || context.getConversationId().isBlank()) return List.of();
        LlmMemoryWindow window = LlmMemoryWindow.from(node);
        if (!window.enabled()) return List.of();
        String tenant = input(context, "_memory_tenant_id", "default");
        String owner = input(context, "_memory_owner_id", null);
        return manager.history(tenant, owner, context.getConversationId(), Math.min(window.size(), MAX_TURNS)).stream()
                .map(WorkflowMemoryMessages::toMessage)
                .toList();
    }

    private static Message toMessage(MemoryItem item) {
        String content = item.content();
        if (item.role() == MemoryRole.SYSTEM || item.role() == MemoryRole.FACT || item.role() == MemoryRole.SUMMARY)
            return new SystemMessage(content);
        if (item.role() == MemoryRole.ASSISTANT) return new AssistantMessage(content);
        if (item.role() == MemoryRole.TOOL) return new UserMessage("[tool] " + content);
        return new UserMessage(content);
    }

    private static String input(ExecutionContext context, String key, String fallback) {
        Object value = context.getInputs().get(key);
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? fallback : text;
    }
}
