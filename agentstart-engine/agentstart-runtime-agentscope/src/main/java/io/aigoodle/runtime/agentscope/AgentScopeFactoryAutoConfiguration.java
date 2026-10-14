package io.aigoodle.runtime.agentscope;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.tool.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        afterName = {
            "io.aigoodle.model.config.GoodleModelAutoConfiguration",
            "io.aigoodle.tool.config.GoodleToolsAutoConfiguration",
            "io.aigoodle.skill.config.GoodleSkillAutoConfiguration"
        },
        before = AgentScopeAutoConfiguration.class)
@ConditionalOnBean({ModelService.class, ToolRegistry.class})
public class AgentScopeFactoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentScopeAgentFactory.class)
    public AgentScopeAgentFactory agentScopeAgentFactory(
            ModelService models,
            ToolRegistry tools,
            ObjectProvider<SkillResolver> skills,
            ObjectProvider<AgentStateStore> stores,
            org.springframework.core.env.Environment environment,
            ObjectProvider<io.aigoodle.agent.service.AgentToolCatalog> catalogs,
            ObjectProvider<io.aigoodle.agent.service.AgentExecutionService> executions) {
        String backend = environment.getProperty("spring-agent.agentscope.state-store", "memory");
        if (!java.util.Set.of("memory", "jdbc").contains(backend)) {
            throw new IllegalArgumentException("Unsupported AgentScope state store: " + backend);
        }
        AgentStateStore stateStore = stores.getIfAvailable();
        if (stateStore == null && "jdbc".equals(backend)) {
            throw new IllegalStateException(
                    "AgentScope JDBC state store requires a JdbcTemplate or a host AgentStateStore");
        }
        if (stateStore == null) stateStore = new InMemoryAgentStateStore();
        return new DefaultAgentScopeAgentFactory(
                models,
                tools,
                skills.getIfAvailable(SkillResolver::none),
                stateStore,
                definition -> catalogs.getObject()
                        .resolve(
                                definition,
                                (id, request) -> PublishedAgentDelegation.run(
                                        definition, id, request, (target, delegatedRequest) -> executions
                                                .getObject()
                                                .runPublished(
                                                        definition.getTenantId(),
                                                        target,
                                                        null,
                                                        delegatedRequest,
                                                        null,
                                                        null))));
    }
}
