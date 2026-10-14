package io.aigoodle.agent.support;

import java.util.List;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic chat model for offline agent tests: a responder function maps the last
 * prompt message to the model's reply, letting us script ReAct/delegation loops without
 * a real LLM.
 */
public class ScriptedChatModel implements ChatModel {

    private final Function<String, String> responder;

    public ScriptedChatModel(Function<String, String> responder) {
        this.responder = responder;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        String last =
                messages.isEmpty() ? "" : messages.get(messages.size() - 1).getText();
        String reply = responder.apply(last == null ? "" : last);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }
}
