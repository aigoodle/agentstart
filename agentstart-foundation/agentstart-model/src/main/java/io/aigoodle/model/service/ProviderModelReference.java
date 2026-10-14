package io.aigoodle.model.service;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.ProviderModelSettingEntity;
import io.aigoodle.model.entity.TenantDefaultModelEntity;
import io.aigoodle.model.enums.ModelType;
import java.util.Objects;

/** Identifies one provider model independently of its persistence representation. */
record ProviderModelReference(String providerName, String modelName, ModelType modelType) {

    ProviderModelReference {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(modelType, "modelType must not be null");
    }

    ProviderModelSettingEntity newEnabledSetting(String tenantId) {
        ProviderModelSettingEntity setting = new ProviderModelSettingEntity();
        setting.setTenantId(tenantId);
        setting.setProviderName(providerName);
        setting.setModelName(modelName);
        setting.setModelType(modelType);
        setting.setEnabled(Boolean.TRUE);
        setting.setLoadBalancingEnabled(Boolean.FALSE);
        return setting;
    }

    TenantDefaultModelEntity newDefault(String tenantId) {
        TenantDefaultModelEntity defaultModel = new TenantDefaultModelEntity();
        defaultModel.setTenantId(tenantId);
        defaultModel.setProviderName(providerName);
        defaultModel.setModelName(modelName);
        defaultModel.setModelType(modelType);
        return defaultModel;
    }

    ModelEntity newMaterializedModel(String tenantId, String credentialId) {
        ModelEntity model = new ModelEntity();
        model.setTenantId(tenantId);
        model.setProviderName(providerName);
        model.setModelName(modelName);
        model.setModelType(modelType);
        model.setCredentialId(credentialId);
        model.setEnabled(Boolean.TRUE);
        model.setIsDefault(Boolean.FALSE);
        return model;
    }
}
