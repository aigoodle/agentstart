package io.aigoodle.agent.eval;

/** Pluggable judge; applications can publish semantic, safety or LLM-as-judge implementations. */
@FunctionalInterface
public interface AgentEvaluator {
    AgentEvaluationScore evaluate(AgentEvaluationContext context);
}
