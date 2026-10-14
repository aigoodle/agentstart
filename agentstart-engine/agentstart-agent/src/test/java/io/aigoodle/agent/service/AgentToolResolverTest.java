package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolResolverTest {

    @Test
    void exposesAllRegisteredToolsWhenNoWhitelistIsConfigured() {
        ToolDefinition search = new NamedTool("search");
        ToolDefinition calculator = new NamedTool("calculator");
        AgentToolResolver resolver =
                resolver(mock(AppMapper.class), new ToolRegistry(List.of(search, calculator), List.of()));

        AgentDefinition definition = definition("agent-1");
        definition.setToolNames(List.of());

        List<ToolDefinition> resolved = resolver.resolve(definition, this::emptyRun);

        assertThat(resolved).extracting(ToolDefinition::name).containsExactly("search", "calculator");
    }

    @Test
    void resolvesAWhitelistOnceAndSkipsBlankOrUnknownNames() {
        ToolDefinition search = new NamedTool("search");
        AgentToolResolver resolver = resolver(mock(AppMapper.class), new ToolRegistry(List.of(search), List.of()));
        AgentDefinition definition = definition("agent-1");
        definition.setToolNames(List.of("search", " ", "missing", "search"));

        List<ToolDefinition> resolved = resolver.resolve(definition, this::emptyRun);

        assertThat(resolved).extracting(ToolDefinition::name).containsExactly("search");
    }

    @Test
    void rejectsSelfDelegationBeforeLoadingTheAgent() {
        AppMapper appMapper = mock(AppMapper.class);
        AgentToolResolver resolver = resolver(appMapper, emptyRegistry());
        AgentDefinition definition = definition("agent-1");
        definition.setDelegateAgentIds(List.of("agent-1"));

        assertThatThrownBy(() -> resolver.resolve(definition, this::emptyRun))
                .isInstanceOfSatisfying(PlatformException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("invalid_agent_delegation"));
        verify(appMapper, never()).selectOne(any());
    }

    @Test
    void rejectsDelegationAcrossTenantBoundaries() {
        AppMapper appMapper = mock(AppMapper.class);
        AppEntity delegate = delegate("worker-1", "tenant-b", "Worker");
        when(appMapper.selectOne(any())).thenReturn(delegate);
        AgentToolResolver resolver = resolver(appMapper, emptyRegistry());
        AgentDefinition definition = definition("agent-1");
        definition.setTenantId("tenant-a");
        definition.setDelegateAgentIds(List.of("worker-1"));

        assertThatThrownBy(() -> resolver.resolve(definition, this::emptyRun))
                .isInstanceOfSatisfying(PlatformException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("delegate_cross_tenant"));
    }

    @Test
    void buildsAStableToolNameWhenTheDisplayNameHasNoAsciiCharacters() {
        AppMapper appMapper = mock(AppMapper.class);
        AppEntity delegate = delegate("worker-1", " ", "研究助手");
        when(appMapper.selectOne(any())).thenReturn(delegate);
        AgentToolResolver resolver = resolver(appMapper, emptyRegistry());
        AgentDefinition definition = definition("agent-1");
        definition.setTenantId(null);
        definition.setDelegateAgentIds(List.of("worker-1", "worker-1", " "));

        List<ToolDefinition> resolved = resolver.resolve(definition, this::emptyRun);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().name()).isEqualTo("delegate_to_worker_1");
        assertThat(resolved.getFirst().description()).isEqualTo("Delegate a subtask to the '研究助手' agent.");
        verify(appMapper).selectOne(any());
    }

    private AgentResponse emptyRun(String agentId, AgentRequest request) {
        return new AgentResponse();
    }

    private static AgentToolResolver resolver(AppMapper appMapper, ToolRegistry toolRegistry) {
        return new AgentToolResolver(appMapper, mock(AppModelConfigService.class), toolRegistry);
    }

    private static ToolRegistry emptyRegistry() {
        return new ToolRegistry(List.of(), List.of());
    }

    private static AgentDefinition definition(String agentId) {
        return AgentDefinition.builder()
                .id(agentId)
                .tenantId("default")
                .toolNames(List.of("unregistered"))
                .build();
    }

    private static AppEntity delegate(String agentId, String tenantId, String name) {
        AppEntity agent = new AppEntity();
        agent.setId(agentId);
        agent.setTenantId(tenantId);
        agent.setName(name);
        return agent;
    }

    private record NamedTool(String name) implements ToolDefinition {

        @Override
        public String description() {
            return name;
        }

        @Override
        public Object execute(Map<String, Object> arguments) {
            return null;
        }
    }
}
