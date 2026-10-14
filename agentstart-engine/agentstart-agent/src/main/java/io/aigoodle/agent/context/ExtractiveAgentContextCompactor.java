package io.aigoodle.agent.context;

import io.aigoodle.agent.api.AgentMessage;
import java.util.List;
import java.util.Optional;

/** Deterministic fallback compactor; replace this bean with an LLM summarizer when desired. */
public final class ExtractiveAgentContextCompactor implements AgentContextCompactor {

    private static final String PREFIX = "[Earlier conversation summary]\n";

    @Override
    public Optional<AgentMessage> compact(List<AgentMessage> messages, int maxCharacters) {
        if (messages == null || messages.isEmpty() || maxCharacters <= PREFIX.length()) {
            return Optional.empty();
        }
        StringBuilder summary = new StringBuilder(PREFIX);
        for (AgentMessage message : messages) {
            if (message == null
                    || message.content() == null
                    || message.content().isBlank()) continue;
            String line = message.role().name().toLowerCase() + ": "
                    + message.content().strip().replaceAll("\\s+", " ") + "\n";
            int available = maxCharacters - summary.length();
            if (available <= 1) break;
            if (line.length() > available) {
                summary.append(line, 0, Math.max(0, available - 1)).append('…');
                break;
            }
            summary.append(line);
        }
        return summary.length() == PREFIX.length()
                ? Optional.empty()
                : Optional.of(AgentMessage.system(summary.toString().stripTrailing()));
    }
}
