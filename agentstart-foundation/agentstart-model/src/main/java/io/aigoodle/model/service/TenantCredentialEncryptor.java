package io.aigoodle.model.service;

/** Host-overridable tenant key boundary for persisted model credentials. */
public interface TenantCredentialEncryptor {
    String encrypt(String tenantId, String plaintext);

    String decrypt(String tenantId, String ciphertext);
}
