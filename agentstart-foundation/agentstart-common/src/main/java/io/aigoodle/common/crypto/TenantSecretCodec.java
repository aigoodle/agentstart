package io.aigoodle.common.crypto;

import io.aigoodle.common.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;

public class TenantSecretCodec {
    private static final String TENANT_TOKEN_PREFIX = "at1:";
    private final TenantTextEncryptor tenantEncryptor;
    private final TextEncryptor legacyEncryptor;

    /** Compatibility constructor for hosts that supplied only a global encryptor. */
    public TenantSecretCodec(TextEncryptor encryptor) {
        this(
                new TenantTextEncryptor() {
                    @Override
                    public String encrypt(String tenantId, String plaintext) {
                        return encryptor.encrypt(plaintext);
                    }

                    @Override
                    public String decrypt(String tenantId, String ciphertext) {
                        return encryptor.decrypt(ciphertext);
                    }
                },
                encryptor);
    }

    public TenantSecretCodec(TenantTextEncryptor tenantEncryptor, TextEncryptor legacyEncryptor) {
        this.tenantEncryptor = tenantEncryptor;
        this.legacyEncryptor = legacyEncryptor;
    }

    public String encode(String tenantId, Map<String, Object> values) {
        return values == null || values.isEmpty()
                ? null
                : TENANT_TOKEN_PREFIX + tenantEncryptor.encrypt(tenant(tenantId), JsonUtils.toJson(values));
    }

    public Map<String, Object> decode(String tenantId, String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return new LinkedHashMap<>();
        String plaintext = encrypted.startsWith(TENANT_TOKEN_PREFIX)
                ? tenantEncryptor.decrypt(tenant(tenantId), encrypted.substring(TENANT_TOKEN_PREFIX.length()))
                : legacyEncryptor.decrypt(encrypted);
        Map<String, Object> value = JsonUtils.parseMap(plaintext);
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private static String tenant(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }
}
