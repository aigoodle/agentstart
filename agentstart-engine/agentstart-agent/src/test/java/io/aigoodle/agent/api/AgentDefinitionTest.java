package io.aigoodle.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentDefinitionTest {

    @Test
    void suppliesRunnableDefaultsForAnEmptyDefinition() {
        AgentDefinition definition = AgentDefinition.builder().build();

        assertThat(definition.getStrategy()).isEqualTo(AgentStrategyType.REACT);
        assertThat(definition.getMaxIterations()).isEqualTo(6);
        assertThat(definition.getMemoryWindow()).isEqualTo(20);
        assertThat(definition.isMemoryEnabled()).isTrue();
        assertThat(definition.getToolNames()).isEmpty();
        assertThat(definition.getApprovalRequiredTools()).isEmpty();
        assertThat(definition.getDelegateAgentIds()).isEmpty();
        assertThat(definition.getModelSettings()).isEmpty();
    }

    @Test
    void protectsStrategiesFromExplicitlyNullCollectionsAndStrategy() {
        AgentDefinition definition = AgentDefinition.builder()
                .strategy(null)
                .toolNames(null)
                .approvalRequiredTools(null)
                .delegateAgentIds(null)
                .modelSettings(null)
                .build();

        assertThat(definition.getStrategy()).isEqualTo(AgentStrategyType.REACT);
        assertThat(definition.getToolNames()).isEmpty();
        assertThat(definition.getApprovalRequiredTools()).isEmpty();
        assertThat(definition.getDelegateAgentIds()).isEmpty();
        assertThat(definition.getModelSettings()).isEmpty();
    }

    @Test
    void replacesNonPositiveRuntimeLimitsWithSafeDefaults() {
        AgentDefinition definition =
                AgentDefinition.builder().maxIterations(-5).memoryWindow(0).build();

        assertThat(definition.getMaxIterations()).isEqualTo(6);
        assertThat(definition.getMemoryWindow()).isEqualTo(20);
    }
}
