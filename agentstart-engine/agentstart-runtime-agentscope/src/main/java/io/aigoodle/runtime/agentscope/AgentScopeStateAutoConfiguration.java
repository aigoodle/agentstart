package io.aigoodle.runtime.agentscope;

import io.agentscope.core.state.AgentStateStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        before = AgentScopeFactoryAutoConfiguration.class)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "spring-agent.agentscope", name = "state-store", havingValue = "jdbc")
public class AgentScopeStateAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentStateStore.class)
    public AgentStateStore agentScopeStateStore(JdbcTemplate jdbc) {
        return new JdbcAgentScopeStateStore(jdbc);
    }
}
