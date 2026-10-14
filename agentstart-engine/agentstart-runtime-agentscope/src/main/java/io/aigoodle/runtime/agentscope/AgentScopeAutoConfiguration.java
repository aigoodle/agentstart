package io.aigoodle.runtime.agentscope;

import io.aigoodle.agent.runtime.AgentRunStore;
import io.aigoodle.agent.runtime.InMemoryAgentRunStore;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.aigoodle.agent.config.AgentRuntimeAutoConfiguration")
@ConditionalOnBean(AgentScopeAgentFactory.class)
public class AgentScopeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentScopeRuntime.class)
    public AgentScopeRuntime agentScopeRuntime(
            Map<String, AgentScopeAgentFactory> factories, ObjectProvider<AgentRunStore> stores) {
        var fallback = new InMemoryAgentRunStore();
        return new AgentScopeRuntime(factories, () -> stores.getIfAvailable(() -> fallback));
    }
}
