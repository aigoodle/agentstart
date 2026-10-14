package io.aigoodle.agent.eval;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentRuntime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Executes repeatable Agent regression suites without requiring a standalone platform. */
public final class AgentEvaluationRunner {

    private final AgentRuntime runtime;
    private final double passThreshold;

    public AgentEvaluationRunner(AgentRuntime runtime) {
        this(runtime, 0.8);
    }

    public AgentEvaluationRunner(AgentRuntime runtime, double passThreshold) {
        this.runtime = runtime;
        this.passThreshold = Math.max(0, Math.min(1, passThreshold));
    }

    public AgentEvaluationResult run(AgentEvaluationCase testCase) {
        long started = System.nanoTime();
        AgentResponse response = null;
        Throwable error = null;
        try {
            response = runtime.run(testCase.definition(), testCase.request());
        } catch (RuntimeException failure) {
            error = failure;
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        AgentEvaluationContext context = new AgentEvaluationContext(testCase, response, duration, error);
        List<AgentEvaluationScore> scores = new ArrayList<>();
        for (AgentEvaluator evaluator : testCase.evaluators()) {
            try {
                AgentEvaluationScore score = evaluator.evaluate(context);
                if (score != null) scores.add(score);
            } catch (RuntimeException judgeFailure) {
                scores.add(new AgentEvaluationScore(
                        evaluator.getClass().getSimpleName(), 0, "Evaluator failed: " + judgeFailure.getMessage()));
            }
        }
        if (scores.isEmpty())
            scores.add(new AgentEvaluationScore(
                    "execution", error == null ? 1 : 0, error == null ? "Execution completed" : error.getMessage()));
        double aggregate = scores.stream()
                .mapToDouble(AgentEvaluationScore::score)
                .average()
                .orElse(0);
        boolean passed = error == null && scores.stream().allMatch(score -> score.passed(passThreshold));
        return new AgentEvaluationResult(
                testCase.name(),
                passed,
                aggregate,
                duration,
                response,
                error == null ? null : error.getMessage(),
                scores);
    }

    public List<AgentEvaluationResult> runAll(List<AgentEvaluationCase> cases) {
        return cases == null ? List.of() : cases.stream().map(this::run).toList();
    }
}
