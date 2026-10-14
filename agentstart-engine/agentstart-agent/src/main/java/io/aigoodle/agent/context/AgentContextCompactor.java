package io.aigoodle.agent.context;

import io.aigoodle.agent.api.AgentMessage;
import java.util.List;
import java.util.Optional;

/** SPI for compressing dialogue omitted by the context budget. */
@FunctionalInterface
public interface AgentContextCompactor {
    Optional<AgentMessage> compact(List<AgentMessage> omittedMessages, int maxCharacters);
}
