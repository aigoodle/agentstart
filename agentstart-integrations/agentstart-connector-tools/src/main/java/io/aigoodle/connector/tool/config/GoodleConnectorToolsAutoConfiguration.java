package io.aigoodle.connector.tool.config;

import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.registry.ConnectorRegistry;
import io.aigoodle.connector.tool.ConnectorToolProvider;
import io.aigoodle.tool.ToolProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GoodleConnectorToolsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "connectorToolProvider")
    public ToolProvider connectorToolProvider(ConnectorRegistry registry, ConnectorExecutionGateway gateway) {
        return new ConnectorToolProvider(registry, gateway);
    }
}
