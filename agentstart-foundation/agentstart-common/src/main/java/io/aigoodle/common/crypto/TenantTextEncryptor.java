package io.aigoodle.common.crypto;

/** Host-overridable KMS boundary for tenant-isolated secrets. */
public interface TenantTextEncryptor {
    String encrypt(String tenantId, String plaintext);

    String decrypt(String tenantId, String ciphertext);
}
