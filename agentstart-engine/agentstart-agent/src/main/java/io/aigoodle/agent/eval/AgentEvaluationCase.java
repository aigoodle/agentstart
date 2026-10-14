package io.aigoodle.agent.eval;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import java.util.List;

/** One executable regression case for an embedded agent. */
public record AgentEvaluationCase(
        String name, AgentDefinition definition, AgentRequest request, List<AgentEvaluator> evaluators) {
    public AgentEvaluationCase {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Evaluation name is required");
        if (definition == null) throw new IllegalArgumentException("Agent definition is required");
        request = request == null ? AgentRequest.of("") : request;
        evaluators = evaluators == null ? List.of() : List.copyOf(evaluators);
    }
}
