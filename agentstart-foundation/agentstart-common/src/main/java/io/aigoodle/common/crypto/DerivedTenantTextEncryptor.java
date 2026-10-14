package io.aigoodle.common.crypto;

import java.util.concurrent.ConcurrentHashMap;

/** Default local implementation deriving an independent AES-GCM key per tenant from the root secret. */
public final class DerivedTenantTextEncryptor implements TenantTextEncryptor {
    private final String rootSecret;
    private final ConcurrentHashMap<String, AesGcmTextEncryptor> encryptors = new ConcurrentHashMap<>();

    public DerivedTenantTextEncryptor(String rootSecret) {
        if (rootSecret == null || rootSecret.isBlank()) throw new IllegalArgumentException("root secret is required");
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
        String tenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        return encryptors.computeIfAbsent(
                tenant, key -> new AesGcmTextEncryptor(rootSecret + "\u001fagent-start-tenant\u001f" + key));
    }
}
