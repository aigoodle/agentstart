package io.aigoodle.model.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import io.aigoodle.common.crypto.TextEncryptor;
import io.aigoodle.model.mapper.ModelMapper;
import io.aigoodle.model.mapper.PredefinedModelMapper;
import io.aigoodle.model.mapper.PromptTemplateMapper;
import io.aigoodle.model.mapper.ProviderCredentialMapper;
import io.aigoodle.model.mapper.ProviderDefinitionMapper;
import io.aigoodle.model.mapper.ProviderModelSettingMapper;
import io.aigoodle.model.mapper.TenantDefaultModelMapper;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.builtin.BuiltinModelProviders;
import io.aigoodle.model.provider.builtin.OllamaModelProvider;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import io.aigoodle.model.service.CredentialCodec;
import io.aigoodle.model.service.DerivedTenantCredentialEncryptor;
import io.aigoodle.model.service.ModelConnectionTester;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.model.service.ProviderCatalogClient;
import io.aigoodle.model.service.ProviderCredentialService;
import io.aigoodle.model.service.ProviderDefinitionSeeder;
import io.aigoodle.model.service.ProviderDefinitionService;
import io.aigoodle.model.service.ProviderModelSettingsService;
import io.aigoodle.model.service.TenantCredentialEncryptor;
import java.util.ArrayList;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the model module. Importing the {@code agent-start-model}
 * jar makes provider registry, credential encryption, the instance factory and the
 * services available with zero manual wiring.
 * <p>
 * Built-in providers are added to the registry directly (not as individual beans) and
 * any {@link ModelProvider} the application publishes as a Spring bean — e.g. a
 * third-party connector jar — is merged in as well.
 */
@AutoConfiguration(after = io.aigoodle.persistence.TenantPersistenceAutoConfiguration.class)
@EnableConfigurationProperties(GoodleModelProperties.class)
@MapperScan("io.aigoodle.model.mapper")
public class GoodleModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public io.aigoodle.model.video.VideoModelService videoModelService(
            ModelService models,
            ModelProviderRegistry providers,
            ProviderDefinitionService definitions,
            ProviderModelSettingsService settings) {
        return new io.aigoodle.model.video.VideoModelService(models, providers, definitions, settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public TextEncryptor agentTextEncryptor(GoodleModelProperties properties) {
        return new AesGcmTextEncryptor(properties.getEncryptionSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public CredentialCodec credentialCodec(
            TextEncryptor encryptor,
            ObjectProvider<TenantCredentialEncryptor> tenantEncryptors,
            GoodleModelProperties properties) {
        TenantCredentialEncryptor tenantEncryptor = tenantEncryptors.getIfAvailable(
                () -> new DerivedTenantCredentialEncryptor(properties.getEncryptionSecret()));
        return new CredentialCodec(encryptor, tenantEncryptor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "agentAuditMetaObjectHandler")
    public MetaObjectHandler agentAuditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelProviderRegistry modelProviderRegistry(
            ObjectProvider<ModelProvider> providerBeans, GoodleModelProperties properties) {
        List<ModelProvider> all = new ArrayList<>();
        // Application / third-party providers published as beans take precedence.
        providerBeans.orderedStream().forEach(all::add);
        if (properties.isRegisterBuiltinProviders()) {
            all.add(new OllamaModelProvider());
            all.addAll(BuiltinModelProviders.openAiCompatible());
        }
        return new ModelProviderRegistry(all);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelInstanceFactory modelInstanceFactory(
            ModelProviderRegistry registry,
            org.springframework.beans.factory.ObjectProvider<io.aigoodle.model.runtime.ChatModelDecorator> decorators) {
        return new ModelInstanceFactory(registry, decorators.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderCredentialService providerCredentialService(ProviderCredentialMapper mapper, CredentialCodec codec) {
        return new ProviderCredentialService(mapper, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderCatalogClient providerCatalogClient(
            ModelProviderRegistry registry, ProviderCredentialService credentialService, CredentialCodec codec) {
        return new ProviderCatalogClient(registry, credentialService, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelConnectionTester modelConnectionTester(
            ModelProviderRegistry registry, ModelInstanceFactory instanceFactory) {
        return new ModelConnectionTester(registry, instanceFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelService modelService(
            ModelMapper modelMapper,
            ProviderCredentialService credentialService,
            CredentialCodec codec,
            ModelProviderRegistry registry,
            ModelInstanceFactory instanceFactory,
            ProviderDefinitionService definitionService,
            ProviderModelSettingsService settingsService,
            ModelConnectionTester connectionTester,
            ProviderCatalogClient providerCatalogClient) {
        return new ModelService(
                modelMapper,
                credentialService,
                codec,
                registry,
                instanceFactory,
                definitionService,
                settingsService,
                connectionTester,
                providerCatalogClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptTemplateService promptTemplateService(PromptTemplateMapper mapper) {
        return new PromptTemplateService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderDefinitionService providerDefinitionService(
            ProviderDefinitionMapper providerMapper, PredefinedModelMapper predefinedMapper) {
        return new ProviderDefinitionService(providerMapper, predefinedMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderModelSettingsService providerModelSettingsService(
            ProviderModelSettingMapper settingMapper, TenantDefaultModelMapper defaultMapper) {
        return new ProviderModelSettingsService(settingMapper, defaultMapper);
    }

    /**
     * Explicit catalog maintenance for provider configuration and manual synchronization.
     * Creating this bean does not query or write the database.
     */
    @Bean
    @ConditionalOnMissingBean
    public ProviderDefinitionSeeder providerDefinitionSeeder(
            ModelProviderRegistry registry, ProviderDefinitionService service) {
        return new ProviderDefinitionSeeder(registry, service);
    }
}
