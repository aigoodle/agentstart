package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.common.exception.PlatformException;
import org.junit.jupiter.api.Test;

class AgentDefinitionFactoryTest {

    @Test
    void createsRunnableDefaultsWhenNoSidecarExists() {
        AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
        AgentDefinitionFactory definitionFactory = new AgentDefinitionFactory(modelConfigService);
        AppEntity agent = agent("agent-1");
        agent.setModelProvider("openai");
        agent.setModelName("gpt-test");

        AgentDefinition definition = definitionFactory.create(agent);

        assertThat(definition.getModelProvider()).isEqualTo("openai");
        assertThat(definition.getModelName()).isEqualTo("gpt-test");
        assertThat(definition.getStrategy()).isEqualTo(AgentStrategyType.REACT);
        assertThat(definition.getMaxIterations()).isEqualTo(6);
        assertThat(definition.getMaxModelCalls()).isZero();
        assertThat(definition.getMaxToolCalls()).isZero();
        assertThat(definition.getMemoryWindow()).isEqualTo(20);
        assertThat(definition.isMemoryEnabled()).isTrue();
        assertThat(definition.getToolNames()).isEmpty();
        assertThat(definition.getModelSettings()).isEmpty();
    }

    @Test
    void acceptsHumanFriendlyStrategyNamesAndBlankJsonFields() {
        AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
        AppModelConfigEntity modelConfig = new AppModelConfigEntity();
        modelConfig.setStrategy(" function-calling ");
        modelConfig.setToolNamesJson(" ");
        modelConfig.setApprovalToolsJson("");
        modelConfig.setDelegateAgentIdsJson(" ");
        modelConfig.setConfigs("");
        modelConfig.setMaxModelCalls(4);
        modelConfig.setMaxToolCalls(9);
        when(modelConfigService.findByAppId("default", "agent-1")).thenReturn(modelConfig);
        AgentDefinitionFactory definitionFactory = new AgentDefinitionFactory(modelConfigService);

        AgentDefinition definition = definitionFactory.create(agent("agent-1"));

        assertThat(definition.getStrategy()).isEqualTo(AgentStrategyType.FUNCTION_CALLING);
        assertThat(definition.getToolNames()).isEmpty();
        assertThat(definition.getApprovalRequiredTools()).isEmpty();
        assertThat(definition.getDelegateAgentIds()).isEmpty();
        assertThat(definition.getModelSettings()).isEmpty();
        assertThat(definition.getMaxModelCalls()).isEqualTo(4);
        assertThat(definition.getMaxToolCalls()).isEqualTo(9);
    }

    @Test
    void reportsUnsupportedStrategiesAsDomainErrors() {
        AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
        AppModelConfigEntity modelConfig = new AppModelConfigEntity();
        modelConfig.setStrategy("guess-and-hope");
        when(modelConfigService.findByAppId("default", "agent-1")).thenReturn(modelConfig);
        AgentDefinitionFactory definitionFactory = new AgentDefinitionFactory(modelConfigService);

        assertThatThrownBy(() -> definitionFactory.create(agent("agent-1")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("invalid_agent_strategy");
                    assertThat(exception).hasMessageContaining("guess-and-hope");
                });
    }

    @Test
    void sidecarModelOnlyOverridesCatalogValuesWhenItHasText() {
        AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
        AppModelConfigEntity modelConfig = new AppModelConfigEntity();
        modelConfig.setModelProvider(" ");
        modelConfig.setModelName("sidecar-model");
        when(modelConfigService.findByAppId("default", "agent-1")).thenReturn(modelConfig);
        AgentDefinitionFactory definitionFactory = new AgentDefinitionFactory(modelConfigService);
        AppEntity agent = agent("agent-1");
        agent.setModelProvider("catalog-provider");
        agent.setModelName("catalog-model");

        AppEntity enriched = definitionFactory.enrich(agent);

        assertThat(enriched.getModelProvider()).isEqualTo("catalog-provider");
        assertThat(enriched.getModelName()).isEqualTo("sidecar-model");
    }

    private static AppEntity agent(String agentId) {
        AppEntity agent = new AppEntity();
        agent.setId(agentId);
        agent.setTenantId("default");
        agent.setName("Researcher");
        return agent;
    }
}
