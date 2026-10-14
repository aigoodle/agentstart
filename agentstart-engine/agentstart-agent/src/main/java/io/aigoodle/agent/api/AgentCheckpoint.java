package io.aigoodle.agent.api;

/** Provider-neutral serialized strategy state required to resume a paused run. */
public record AgentCheckpoint(String strategy, String stateJson) {}
