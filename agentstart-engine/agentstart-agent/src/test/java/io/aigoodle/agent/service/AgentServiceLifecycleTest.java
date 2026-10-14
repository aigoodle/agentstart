package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.runtime.AgentRunEvent;
import io.aigoodle.agent.runtime.InMemoryAgentRunStore;
import io.aigoodle.agent.strategy.AgentStrategy;
import io.aigoodle.agent.strategy.AgentStrategyRegistry;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;

class AgentServiceLifecycleTest {

    @Test
    void createsAnAgentInTheDefaultTenantWhenTenantIsBlank() {
        Dependencies dependencies = new Dependencies();
        doAnswer(invocation -> {
                    invocation.<AppEntity>getArgument(0).setId("agent-1");
                    return 1;
                })
                .when(dependencies.appMapper)
                .insert(any(AppEntity.class));
        AgentService agentService = dependencies.createService();

        agentService.create(
                SaveAppRequest.builder().tenantId(" ").name("Researcher").build());

        ArgumentCaptor<AppEntity> insertedAgent = ArgumentCaptor.forClass(AppEntity.class);
        verify(dependencies.appMapper).insert(insertedAgent.capture());
        assertThat(insertedAgent.getValue().getTenantId()).isEqualTo("default");
        assertThat(insertedAgent.getValue().getName()).isEqualTo("Researcher");
    }

    @Test
    void deletesTheModelSidecarBeforeItsCatalogEntry() {
        Dependencies dependencies = new Dependencies();
        AppEntity owned = new AppEntity();
        owned.setId("agent-1");
        owned.setTenantId("default");
        when(dependencies.appMapper.selectOne(any())).thenReturn(owned);
        AgentService agentService = dependencies.createService();

        agentService.delete("agent-1");

        InOrder deletionOrder = inOrder(dependencies.modelConfigService, dependencies.appMapper);
        deletionOrder.verify(dependencies.modelConfigService).deleteByAppId("default", "agent-1");
        deletionOrder.verify(dependencies.appMapper).delete(any());
    }

    @Test
    void tenantScopedManagementCannotAdoptOrDeleteAnUnownedAgent() {
        Dependencies dependencies = new Dependencies();
        when(dependencies.appMapper.selectOne(any())).thenReturn(null);
        AgentService agentService = dependencies.createService();

        assertThatThrownBy(() -> agentService.update(
                        "tenant-b",
                        "tenant-a-agent",
                        SaveAppRequest.builder()
                                .tenantId("tenant-b")
                                .name("stolen")
                                .build()))
                .hasMessageContaining("Application not found");
        assertThatThrownBy(() -> agentService.delete("tenant-b", "tenant-a-agent"))
                .hasMessageContaining("Application not found");

        verify(dependencies.appMapper, never()).update(any(), any());
        verify(dependencies.appMapper, never()).delete(any());
        verify(dependencies.modelConfigService, never()).deleteByAppId(anyString());
    }

    @Test
    void generatesAConversationIdWhenTheRequestContainsOnlyWhitespace() {
        Dependencies dependencies = new Dependencies();
        AgentStrategy strategy = mock(AgentStrategy.class);
        AgentResponse strategyResponse = new AgentResponse();
        when(dependencies.strategyRegistry.get(any())).thenReturn(strategy);
        when(strategy.run(any())).thenReturn(strategyResponse);
        when(dependencies.modelService.getChatClient(anyString(), anyString(), anyString()))
                .thenReturn(mock(ChatClient.class));
        AgentService agentService = dependencies.createService();
        AgentDefinition definition = AgentDefinition.builder()
                .id("agent-1")
                .tenantId("default")
                .name("Researcher")
                .modelProvider("openai")
                .modelName("gpt-test")
                .toolNames(List.of("unregistered-tool"))
                .memoryEnabled(false)
                .build();

        AgentResponse response = agentService.runDefinition(
                definition,
                AgentRequest.builder().query("Hello").conversationId(" ").build());

        assertThat(response.getConversationId()).isNotBlank();
        assertThat(response.getConversationId()).isNotEqualTo(" ");
    }

    @Test
    void persistsReasoningStepsEvenWithoutAnSseListener() {
        Dependencies dependencies = new Dependencies();
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        AgentStrategy strategy = mock(AgentStrategy.class);
        when(dependencies.strategyRegistry.get(any())).thenReturn(strategy);
        when(dependencies.modelService.getChatClient(anyString(), anyString(), anyString()))
                .thenReturn(mock(ChatClient.class));
        doAnswer(invocation -> {
                    var context = invocation.<io.aigoodle.agent.strategy.AgentRunContext>getArgument(0);
                    context.publishStep(AgentStep.of(AgentStep.Kind.THOUGHT, "inspect"));
                    context.publishStep(AgentStep.observation("evidence"));
                    AgentResponse response = AgentResponse.forConversation(context.getConversationId());
                    response.complete("done");
                    return response;
                })
                .when(strategy)
                .run(any());
        AgentService service = new AgentService(
                dependencies.appMapper,
                dependencies.modelConfigService,
                dependencies.modelService,
                dependencies.toolRegistry,
                dependencies.strategyRegistry,
                dependencies.memory,
                dependencies.approvalGate,
                store);
        AgentDefinition definition = AgentDefinition.builder()
                .id("agent-1")
                .tenantId("tenant-a")
                .modelProvider("openai")
                .modelName("gpt-test")
                .memoryEnabled(false)
                .build();

        AgentResponse response = service.runDefinition(definition, AgentRequest.of("hello"));

        assertThat(store.events("tenant-a", response.getRunId(), 0, 20))
                .extracting(AgentRunEvent::type)
                .containsSubsequence("STEP_THOUGHT", "STEP_OBSERVATION", "RUN_COMPLETED");
    }

    private static final class Dependencies {

        private final AppMapper appMapper = mock(AppMapper.class);
        private final AppModelConfigService modelConfigService = mock(AppModelConfigService.class);
        private final ModelService modelService = mock(ModelService.class);
        private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
        private final AgentStrategyRegistry strategyRegistry = mock(AgentStrategyRegistry.class);
        private final MemoryManager memory = mock(MemoryManager.class);
        private final ApprovalGate approvalGate = mock(ApprovalGate.class);

        AgentService createService() {
            return new AgentService(
                    appMapper, modelConfigService, modelService, toolRegistry, strategyRegistry, memory, approvalGate);
        }
    }
}
