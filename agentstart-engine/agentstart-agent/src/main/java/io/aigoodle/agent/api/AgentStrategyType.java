package io.aigoodle.agent.api;

/** The reasoning strategy an agent uses. */
public enum AgentStrategyType {

    /** Manual thought-action-observation loop that works with text-only models. */
    REACT,

    /** Native tool calling delegated to the model and Spring AI. */
    FUNCTION_CALLING,

    /** Plan the work, execute each step, and synthesize a final response. */
    PLAN_EXECUTE
}
