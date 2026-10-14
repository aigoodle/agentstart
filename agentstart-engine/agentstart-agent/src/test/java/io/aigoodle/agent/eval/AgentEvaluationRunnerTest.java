package io.aigoodle.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.runtime.AgentRuntime;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentEvaluationRunnerTest {

    @Test
    void runsDeterministicRegressionJudges() {
        AgentRuntime runtime = runtimeReturning("Java 21 is supported");
        AgentEvaluationRunner runner = new AgentEvaluationRunner(runtime);
        AgentEvaluationCase testCase = new AgentEvaluationCase(
                "java-answer",
                AgentDefinition.builder().id("a1").build(),
                AgentRequest.of("Which Java?"),
                List.of(
                        AgentEvaluators.completed(),
                        AgentEvaluators.contains("Java 21"),
                        AgentEvaluators.maxLatency(Duration.ofSeconds(1))));

        AgentEvaluationResult result = runner.run(testCase);

        assertThat(result.passed()).isTrue();
        assertThat(result.aggregateScore()).isEqualTo(1);
        assertThat(result.scores()).hasSize(3);
    }

    @Test
    void capturesRuntimeFailureInsteadOfAbortingTheSuite() {
        AgentRuntime runtime = new AgentRuntime() {
            public AgentResponse run(
                    AgentDefinition definition,
                    AgentRequest request,
                    Consumer<AgentStep> steps,
                    Consumer<String> tokens) {
                throw new IllegalStateException("model unavailable");
            }
        };
        AgentEvaluationRunner runner = new AgentEvaluationRunner(runtime);

        AgentEvaluationResult result = runner.run(new AgentEvaluationCase(
                "failure", AgentDefinition.builder().id("a1").build(), AgentRequest.of("q"), List.of()));

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).contains("model unavailable");
    }

    private static AgentRuntime runtimeReturning(String answer) {
        return new AgentRuntime() {
            public AgentResponse run(
                    AgentDefinition definition,
                    AgentRequest request,
                    Consumer<AgentStep> steps,
                    Consumer<String> tokens) {
                return AgentResponse.forConversation("c1").complete(answer);
            }
        };
    }
}
