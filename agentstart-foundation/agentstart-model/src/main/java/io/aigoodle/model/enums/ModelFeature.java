package io.aigoodle.model.enums;

/**
 * Optional capabilities a concrete LLM may advertise. Used by the workflow/agent
 * layer to decide whether tool calling or vision can be enabled for a model.
 */
public enum ModelFeature {
    TOOL_CALL,
    STREAM_TOOL_CALL,
    AGENT_THOUGHT,
    VISION,
    STREAM,
    STRUCTURED_OUTPUT
}
