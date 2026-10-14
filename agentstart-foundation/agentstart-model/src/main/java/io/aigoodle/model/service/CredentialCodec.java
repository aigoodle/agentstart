package io.aigoodle.model.service;

import io.aigoodle.common.crypto.TextEncryptor;
import io.aigoodle.common.util.JsonUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Serialises a credential map to an encrypted JSON blob and back. Keeps the
 * encryption concern out of the services and entities.
 */
public class CredentialCodec {

    private static final String TENANT_TOKEN_PREFIX = "tenant:v1:";
    private final TextEncryptor legacyEncryptor;
    private final TenantCredentialEncryptor tenantEncryptor;

    public CredentialCodec(TextEncryptor encryptor) {
        this(encryptor, new TenantCredentialEncryptor() {
            @Override
            public String encrypt(String tenantId, String plaintext) {
                return encryptor.encrypt(plaintext);
            }

            @Override
            public String decrypt(String tenantId, String ciphertext) {
                return encryptor.decrypt(ciphertext);
            }
        });
    }

    public CredentialCodec(TextEncryptor legacyEncryptor, TenantCredentialEncryptor tenantEncryptor) {
        this.legacyEncryptor = legacyEncryptor;
        this.tenantEncryptor = tenantEncryptor;
    }

    /** @deprecated use the tenant-explicit overload for persisted credentials. */
    @Deprecated
    public String encode(Map<String, Object> credentials) {
        return encode("default", credentials);
    }

    public String encode(String tenantId, Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        return TENANT_TOKEN_PREFIX + tenantEncryptor.encrypt(normalize(tenantId), JsonUtils.toJson(credentials));
    }

    /** @deprecated use the tenant-explicit overload for persisted credentials. */
    @Deprecated
    public Map<String, Object> decode(String encrypted) {
        return decode("default", encrypted);
    }

    public Map<String, Object> decode(String tenantId, String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return new HashMap<>();
        }
        String json = encrypted.startsWith(TENANT_TOKEN_PREFIX)
                ? tenantEncryptor.decrypt(normalize(tenantId), encrypted.substring(TENANT_TOKEN_PREFIX.length()))
                : legacyEncryptor.decrypt(encrypted);
        Map<String, Object> map = JsonUtils.parseMap(json);
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }

    /** Mask secret-named keys for safe display. */
    public Map<String, Object> obfuscate(Map<String, Object> credentials, Iterable<String> secretKeys) {
        Map<String, Object> copy = new HashMap<>(credentials);
        for (String key : secretKeys) {
            Object v = copy.get(key);
            if (v instanceof String s && !s.isEmpty()) {
                copy.put(key, legacyEncryptor.obfuscate(s));
            }
        }
        return copy;
    }

    private static String normalize(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
    }
}
