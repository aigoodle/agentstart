package io.aigoodle.model.service;

import io.aigoodle.model.entity.ModelEntity;

/** Creates persistence rows from model-management use-case inputs. */
final class ModelEntityFactory {

    private ModelEntityFactory() {}

    static ModelEntity registered(ModelRegistration registration, String tenantId, String encryptedConfiguration) {
        ModelEntity model = new ModelEntity();
        model.setTenantId(tenantId);
        model.setProviderName(registration.getProviderName());
        model.setModelName(registration.getModelName());
        model.setModelType(registration.getModelType());
        model.setCredentialId(registration.getCredentialId());
        model.setEncryptedConfig(encryptedConfiguration);
        model.setEnabled(Boolean.TRUE);
        model.setIsDefault(registration.isAsDefault());
        return model;
    }
}
