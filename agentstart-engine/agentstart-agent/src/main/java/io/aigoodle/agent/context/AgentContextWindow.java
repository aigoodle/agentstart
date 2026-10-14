package io.aigoodle.agent.context;

import io.aigoodle.agent.api.AgentMessage;
import java.util.List;

/** Result of context assembly, including budget diagnostics for observability. */
public record AgentContextWindow(List<AgentMessage> messages, int usedCharacters, int droppedMessages) {
    public AgentContextWindow {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
