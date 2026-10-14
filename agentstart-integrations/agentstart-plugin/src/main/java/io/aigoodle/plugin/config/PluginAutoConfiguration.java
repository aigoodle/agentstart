package io.aigoodle.plugin.config;

import io.aigoodle.connector.config.GoodleConnectorAutoConfiguration;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.plugin.*;
import io.aigoodle.plugin.host.*;
import io.aigoodle.plugin.remote.RemotePlugin;
import io.aigoodle.plugin.runtime.*;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration(before = GoodleConnectorAutoConfiguration.class)
@EnableConfigurationProperties(PluginProperties.class)
public class PluginAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring-agent.plugin", name = "host-signing-secret")
    public PluginHostTokenService pluginHostTokenService(PluginProperties properties) {
        return new PluginHostTokenService(properties.getHostSigningSecret(), properties.getHostBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring-agent.plugin", name = "host-signing-secret")
    public PluginHostApi pluginHostApi(
            PluginHostTokenService tokens,
            PluginHostFactory hosts,
            ObjectProvider<io.aigoodle.connector.registry.ConnectorRegistry> registry,
            ObjectProvider<ConnectorInstallationService> installations) {
        return new PluginHostApi(tokens, hosts, registry::getObject, installations::getObject);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginHostFactory pluginHostFactory(
            ObjectProvider<PluginHostCapability> capabilities, PluginProperties properties) {
        return new PluginHostFactory(capabilities.orderedStream().toList(), properties.getGrants());
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginConnectionResolver pluginConnectionResolver(
            ObjectProvider<ConnectorInstallationService> installations,
            ObjectProvider<ConnectorConnectionService> connections) {
        // Lazy to avoid registry -> provider -> installation service -> registry cycle.
        return request ->
                new StoredPluginConnectionResolver(installations.getObject(), connections.getObject()).resolve(request);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginConnectorProvider pluginConnectorProvider(
            ObjectProvider<Plugin> localPlugins,
            PluginProperties properties,
            ResourceLoader resources,
            PluginConnectionResolver connections,
            PluginHostFactory hosts,
            ObjectProvider<PluginHostTokenService> tokens)
            throws IOException {
        var plugins = new ArrayList<>(localPlugins.orderedStream().toList());
        for (var remote : properties.getRemotes()) {
            if (remote.getManifest() == null
                    || !(remote.getManifest().startsWith("classpath:")
                            || remote.getManifest().startsWith("file:")))
                throw new IllegalArgumentException(
                        "Remote plugin manifest must be a deployment-owned classpath: or file: resource");
            var manifest = PluginManifests.load(resources.getResource(remote.getManifest()));
            plugins.add(new RemotePlugin(
                    manifest,
                    URI.create(remote.getEndpoint()),
                    remote.getToken(),
                    remote.getTimeout(),
                    tokens.getIfAvailable()));
        }
        return new PluginConnectorProvider(plugins, connections, hosts);
    }
}
