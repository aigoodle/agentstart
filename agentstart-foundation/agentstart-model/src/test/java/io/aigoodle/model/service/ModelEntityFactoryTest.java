package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import org.junit.jupiter.api.Test;

class ModelEntityFactoryTest {

    @Test
    void createsARegisteredModelFromOneCoherentRequest() {
        ModelRegistration registration = ModelRegistration.builder()
                .providerName("openai")
                .modelName("gpt-readable")
                .modelType(ModelType.LLM)
                .credentialId("credential-1")
                .asDefault(true)
                .build();

        ModelEntity model = ModelEntityFactory.registered(registration, "tenant-1", "encrypted-configuration");

        assertThat(model)
                .extracting(
                        ModelEntity::getTenantId,
                        ModelEntity::getProviderName,
                        ModelEntity::getModelName,
                        ModelEntity::getModelType,
                        ModelEntity::getCredentialId,
                        ModelEntity::getEncryptedConfig,
                        ModelEntity::getEnabled,
                        ModelEntity::getIsDefault)
                .containsExactly(
                        "tenant-1",
                        "openai",
                        "gpt-readable",
                        ModelType.LLM,
                        "credential-1",
                        "encrypted-configuration",
                        true,
                        true);
    }
}
