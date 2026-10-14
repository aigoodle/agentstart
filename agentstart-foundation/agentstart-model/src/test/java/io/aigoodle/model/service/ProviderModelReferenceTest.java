package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.ProviderModelSettingEntity;
import io.aigoodle.model.entity.TenantDefaultModelEntity;
import io.aigoodle.model.enums.ModelType;
import org.junit.jupiter.api.Test;

class ProviderModelReferenceTest {

    private final ProviderModelReference model = new ProviderModelReference("openai", "gpt-readable", ModelType.LLM);

    @Test
    void createsExplicitOptInSetting() {
        ProviderModelSettingEntity setting = model.newEnabledSetting("tenant-1");

        assertThat(setting.getTenantId()).isEqualTo("tenant-1");
        assertThat(setting.getProviderName()).isEqualTo("openai");
        assertThat(setting.getModelName()).isEqualTo("gpt-readable");
        assertThat(setting.getModelType()).isEqualTo(ModelType.LLM);
        assertThat(setting.getEnabled()).isTrue();
        assertThat(setting.getLoadBalancingEnabled()).isFalse();
    }

    @Test
    void createsTenantDefaultForSameModelIdentity() {
        TenantDefaultModelEntity defaultModel = model.newDefault("tenant-1");

        assertThat(defaultModel.getTenantId()).isEqualTo("tenant-1");
        assertThat(defaultModel.getProviderName()).isEqualTo("openai");
        assertThat(defaultModel.getModelName()).isEqualTo("gpt-readable");
        assertThat(defaultModel.getModelType()).isEqualTo(ModelType.LLM);
    }

    @Test
    void materializesAnEnabledNonDefaultModel() {
        ModelEntity materializedModel = model.newMaterializedModel("tenant-1", "credential-1");

        assertThat(materializedModel)
                .extracting(
                        ModelEntity::getTenantId,
                        ModelEntity::getProviderName,
                        ModelEntity::getModelName,
                        ModelEntity::getModelType,
                        ModelEntity::getCredentialId,
                        ModelEntity::getEnabled,
                        ModelEntity::getIsDefault)
                .containsExactly("tenant-1", "openai", "gpt-readable", ModelType.LLM, "credential-1", true, false);
    }
}
