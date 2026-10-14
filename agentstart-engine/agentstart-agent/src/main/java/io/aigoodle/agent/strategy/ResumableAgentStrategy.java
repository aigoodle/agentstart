package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentResumeCommand;

/** Strategy extension implemented only when execution can continue from an exact checkpoint. */
public interface ResumableAgentStrategy extends AgentStrategy {
    AgentResponse resume(AgentRunContext context, AgentResponse paused, AgentResumeCommand command);
}
