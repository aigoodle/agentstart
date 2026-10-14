package io.aigoodle.common.crypto;

/**
 * Symmetric encryptor used to protect secrets (API keys, credential JSON) at rest.
 * Implementations must be deterministic enough to round-trip but should use a random
 * IV per call so the same plaintext does not always yield the same ciphertext.
 */
public interface TextEncryptor {

    /** Encrypt a UTF-8 plaintext, returning a self-describing (Base64) token. */
    String encrypt(String plaintext);

    /** Reverse {@link #encrypt(String)}. Returns {@code null} for {@code null} input. */
    String decrypt(String ciphertext);

    /**
     * Mask a secret for safe display, e.g. {@code sk-12******cd}. Never throws.
     */
    default String obfuscate(String secret) {
        if (secret == null || secret.isEmpty()) {
            return secret;
        }
        int n = secret.length();
        if (n <= 6) {
            return "******";
        }
        return secret.substring(0, 3) + "******" + secret.substring(n - 3);
    }
}
