package io.aigoodle.plugin.agent;

import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.tool.execution.ToolExecutionContextProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class PluginAgentAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public PluginSkillTools pluginSkillTools(
            ObjectProvider<io.aigoodle.plugin.runtime.PluginConnectorProvider> plugins,
            ObjectProvider<io.aigoodle.connector.installation.ConnectorInstallationService> installations) {
        return new PluginSkillTools(plugins::getObject, installations::getObject);
    }

    @Bean
    @ConditionalOnMissingBean(PluginModelCapability.class)
    public PluginModelCapability pluginModelCapability(ObjectProvider<io.aigoodle.model.service.ModelService> models) {
        return new PluginModelCapability(models::getObject);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginAgentRuntime pluginAgentRuntime(
            ObjectProvider<ConnectorExecutionGateway> gateway,
            ObjectProvider<ConnectorRegistry> registry,
            ObjectProvider<ToolExecutionContextProvider> identity) {
        return new PluginAgentRuntime(gateway::getObject, registry::getObject, () -> identity.getObject()
                .currentContext());
    }
}
