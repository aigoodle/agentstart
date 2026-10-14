package io.aigoodle.connector.config;

import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import io.aigoodle.common.crypto.DerivedTenantTextEncryptor;
import io.aigoodle.common.crypto.TenantSecretCodec;
import io.aigoodle.common.crypto.TenantTextEncryptor;
import io.aigoodle.common.crypto.TextEncryptor;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.execution.ChannelIdentityExecutionPolicy;
import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.execution.ConnectorExecutionPolicy;
import io.aigoodle.connector.execution.ConnectorExecutionQueryService;
import io.aigoodle.connector.execution.ConnectorExecutionRecorder;
import io.aigoodle.connector.execution.DefaultConnectorExecutionGateway;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.persistence.ConnectorConnectionMapper;
import io.aigoodle.connector.persistence.ConnectorExecutionMapper;
import io.aigoodle.connector.persistence.ConnectorInstallationMapper;
import io.aigoodle.connector.provider.ConnectorProvider;
import io.aigoodle.connector.registry.ConnectorRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Connector catalog domain: installation, connection and governed execution. Channel runtime beans live in agentstart-channel. */
@AutoConfiguration
@EnableConfigurationProperties(ConnectorProperties.class)
@MapperScan("io.aigoodle.connector.persistence")
public class GoodleConnectorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChannelIdentityExecutionPolicy.class)
    public ChannelIdentityExecutionPolicy channelIdentityExecutionPolicy() {
        return new ChannelIdentityExecutionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorRegistry connectorRegistry(ObjectProvider<ConnectorProvider> providers) {
        return new ConnectorRegistry(providers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorExecutionGateway connectorExecutionGateway(
            ConnectorRegistry registry,
            ObjectProvider<ConnectorExecutionPolicy> policies,
            ObjectProvider<ConnectorExecutionRecorder> recorders) {
        return new DefaultConnectorExecutionGateway(
                registry, policies.orderedStream().toList(), recorders.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSecretCodec connectorSecretCodec(
            ObjectProvider<TextEncryptor> encryptors,
            ObjectProvider<TenantTextEncryptor> tenantEncryptors,
            ConnectorProperties properties) {
        TextEncryptor encryptor =
                encryptors.getIfAvailable(() -> new AesGcmTextEncryptor(properties.getEncryptionSecret()));
        TenantTextEncryptor tenantEncryptor =
                tenantEncryptors.getIfAvailable(() -> new DerivedTenantTextEncryptor(properties.getEncryptionSecret()));
        return new TenantSecretCodec(tenantEncryptor, encryptor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorConnectionService connectorConnectionService(
            ConnectorConnectionMapper mapper, TenantSecretCodec codec) {
        return new ConnectorConnectionService(mapper, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorInstallationService connectorInstallationService(
            ConnectorInstallationMapper mapper, ConnectorRegistry registry) {
        return new ConnectorInstallationService(mapper, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorExecutionRecorder connectorExecutionRecorder(ConnectorExecutionMapper mapper) {
        return new ConnectorExecutionRecorder(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorExecutionQueryService connectorExecutionQueryService(ConnectorExecutionMapper mapper) {
        return new ConnectorExecutionQueryService(mapper);
    }
}
