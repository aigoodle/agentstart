package io.aigoodle.agent.eval;

import io.aigoodle.agent.config.AgentRuntimeAutoConfiguration;
import io.aigoodle.agent.runtime.AgentRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentRuntimeAutoConfiguration.class)
public class AgentEvaluationAutoConfiguration {
    @Bean
    @ConditionalOnBean(AgentRuntime.class)
    @ConditionalOnMissingBean
    public AgentEvaluationRunner agentEvaluationRunner(AgentRuntime runtime) {
        return new AgentEvaluationRunner(runtime);
    }
}
