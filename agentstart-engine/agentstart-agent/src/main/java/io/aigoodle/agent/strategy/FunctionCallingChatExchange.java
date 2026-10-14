package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

/** Builds and executes the Spring AI request used by native function calling. */
final class FunctionCallingChatExchange {

    String exchange(AgentRunContext context, List<ToolCallback> toolCallbacks) {
        context.claimModelCall();
        ChatClient.ChatClientRequestSpec request = configureRequest(context, toolCallbacks);
        if (!context.isTokenStreamingEnabled()) {
            String content = request.call().content();
            return content == null ? "" : content;
        }

        StringBuilder streamedContent = new StringBuilder();
        request.stream()
                .content()
                .doOnNext(delta -> appendToken(context, streamedContent, delta))
                .blockLast();
        return streamedContent.toString();
    }

    private static ChatClient.ChatClientRequestSpec configureRequest(
            AgentRunContext context, List<ToolCallback> toolCallbacks) {
        AgentDefinition definition = context.getDefinition();
        ChatClient.ChatClientRequestSpec request = context.getChatClient().prompt();
        org.springframework.ai.chat.prompt.ChatOptions chatOptions = AgentChatOptionsFactory.build(definition);
        if (chatOptions != null) {
            request = request.options(chatOptions.mutate());
        }
        if (definition.getInstructions() != null
                && !definition.getInstructions().isBlank()) {
            request = request.system(definition.getInstructions());
        }

        List<Message> history = toSpringMessages(context.getHistory());
        if (!history.isEmpty()) {
            request = request.messages(history);
        }
        request = request.user(context.getQuery());
        if (!toolCallbacks.isEmpty()) {
            request = request.toolCallbacks(toolCallbacks.toArray(ToolCallback[]::new));
        }
        return request;
    }

    private static List<Message> toSpringMessages(List<AgentMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (AgentMessage message : history) {
            messages.add(
                    switch (message.role()) {
                        case ASSISTANT -> new AssistantMessage(message.content());
                        case SYSTEM -> new SystemMessage(message.content());
                        default -> new UserMessage(message.content());
                    });
        }
        return messages;
    }

    private static void appendToken(AgentRunContext context, StringBuilder streamedContent, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        streamedContent.append(delta);
        context.publishToken(delta);
    }
}
