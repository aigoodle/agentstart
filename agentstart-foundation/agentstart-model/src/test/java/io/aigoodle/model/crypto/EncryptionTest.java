package io.aigoodle.model.crypto;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import io.aigoodle.common.crypto.TextEncryptor;
import io.aigoodle.model.service.CredentialCodec;
import io.aigoodle.model.service.DerivedTenantCredentialEncryptor;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EncryptionTest {

    private final TextEncryptor encryptor = new AesGcmTextEncryptor("unit-test-secret");

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "sk-abcdef0123456789";
        String cipher = encryptor.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertTrue(cipher.startsWith("agcm:"));
        assertEquals(plain, encryptor.decrypt(cipher));
    }

    @Test
    void sameInputProducesDifferentCipherButSamePlaintext() {
        String plain = "hello";
        String c1 = encryptor.encrypt(plain);
        String c2 = encryptor.encrypt(plain);
        assertNotEquals(c1, c2, "random IV should make ciphertexts differ");
        assertEquals(encryptor.decrypt(c1), encryptor.decrypt(c2));
    }

    @Test
    void decryptToleratesPlaintextLegacyValues() {
        assertEquals("not-encrypted", encryptor.decrypt("not-encrypted"));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void credentialCodecRoundTrip() {
        CredentialCodec codec = new CredentialCodec(encryptor);
        Map<String, Object> creds = Map.of("apiKey", "sk-123", "baseUrl", "https://x", "dimensions", 1536);
        String encoded = codec.encode(creds);
        Map<String, Object> decoded = codec.decode(encoded);
        assertEquals("sk-123", decoded.get("apiKey"));
        assertEquals("https://x", decoded.get("baseUrl"));
        assertEquals(1536, decoded.get("dimensions"));
    }

    @Test
    void obfuscateMasksSecrets() {
        CredentialCodec codec = new CredentialCodec(encryptor);
        Map<String, Object> masked = codec.obfuscate(Map.of("apiKey", "sk-abcdef0123"), java.util.List.of("apiKey"));
        assertTrue(masked.get("apiKey").toString().contains("******"));
        assertFalse(masked.get("apiKey").toString().equals("sk-abcdef0123"));
    }

    @Test
    void tenantCredentialCannotBeDecryptedByAnotherTenant() {
        CredentialCodec codec = new CredentialCodec(encryptor, new DerivedTenantCredentialEncryptor("unit-test-root"));
        String encoded = codec.encode("tenant-a", Map.of("apiKey", "sk-a"));
        assertTrue(encoded.startsWith("tenant:v1:"));
        assertEquals("sk-a", codec.decode("tenant-a", encoded).get("apiKey"));
        assertThrows(RuntimeException.class, () -> codec.decode("tenant-b", encoded));
    }

    @Test
    void tenantCodecCanStillReadLegacyCiphertext() {
        CredentialCodec codec = new CredentialCodec(encryptor, new DerivedTenantCredentialEncryptor("unit-test-root"));
        String legacy = encryptor.encrypt(io.aigoodle.common.util.JsonUtils.toJson(Map.of("apiKey", "old")));
        assertEquals("old", codec.decode("tenant-a", legacy).get("apiKey"));
    }
}
