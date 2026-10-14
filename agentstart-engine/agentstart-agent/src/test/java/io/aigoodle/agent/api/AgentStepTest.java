package io.aigoodle.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentStepTest {

    @Test
    void createsAnActionAsACompleteTraceValue() {
        AgentStep action = AgentStep.action("search", "{\"query\":\"Spring\"}", "Need docs");

        assertThat(action.getKind()).isEqualTo(AgentStep.Kind.ACTION);
        assertThat(action.getAction()).isEqualTo("search");
        assertThat(action.getActionInput()).isEqualTo("{\"query\":\"Spring\"}");
        assertThat(action.getThought()).isEqualTo("Need docs");
    }

    @Test
    void createsAnObservationAsACompleteTraceValue() {
        AgentStep observation = AgentStep.observation("Search result");

        assertThat(observation.getKind()).isEqualTo(AgentStep.Kind.OBSERVATION);
        assertThat(observation.getObservation()).isEqualTo("Search result");
    }
}
