package io.aigoodle.model.service;

import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import java.util.concurrent.ConcurrentHashMap;

/** Default local implementation. Hosts may replace it with a KMS-backed bean. */
public final class DerivedTenantCredentialEncryptor implements TenantCredentialEncryptor {
    private final String rootSecret;
    private final ConcurrentHashMap<String, AesGcmTextEncryptor> encryptors = new ConcurrentHashMap<>();

    public DerivedTenantCredentialEncryptor(String rootSecret) {
        this.rootSecret = rootSecret;
    }

    @Override
    public String encrypt(String tenantId, String plaintext) {
        return encryptor(tenantId).encrypt(plaintext);
    }

    @Override
    public String decrypt(String tenantId, String ciphertext) {
        return encryptor(tenantId).decrypt(ciphertext);
    }

    private AesGcmTextEncryptor encryptor(String tenantId) {
        String normalized = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        return encryptors.computeIfAbsent(
                normalized, key -> new AesGcmTextEncryptor(rootSecret + "\u001fagent-start-model-tenant\u001f" + key));
    }
}
