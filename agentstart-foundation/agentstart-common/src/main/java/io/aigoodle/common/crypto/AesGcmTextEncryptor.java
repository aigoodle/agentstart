package io.aigoodle.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256/GCM implementation of {@link TextEncryptor}.
 * <p>
 * The configured secret is hashed with SHA-256 to derive a 256-bit key, so any
 * passphrase length is accepted. A fresh 12-byte IV is generated per call and
 * prepended to the ciphertext; the output is URL-safe Base64.
 */
public class AesGcmTextEncryptor implements TextEncryptor {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DIGEST_ALGORITHM = "SHA-256";
    private static final String TOKEN_PREFIX = "agcm:";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;
    private static final int AUTHENTICATION_TAG_LENGTH_BYTES = AUTHENTICATION_TAG_LENGTH_BITS / Byte.SIZE;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    public AesGcmTextEncryptor(String secret) {
        this(secret, new SecureRandom());
    }

    AesGcmTextEncryptor(String secret, SecureRandom secureRandom) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Encryption secret must not be blank");
        }
        this.encryptionKey = deriveKey(secret);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest keyDigest = MessageDigest.getInstance(KEY_DIGEST_ALGORITHM);
            byte[] keyBytes = keyDigest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for AES key derivation", exception);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = initializedCipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return TOKEN_PREFIX + EncryptedPayload.of(nonce, ciphertext).encode();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt text with AES-GCM", exception);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        // Tolerate values that were never encrypted (e.g. legacy plaintext config).
        if (!ciphertext.startsWith(TOKEN_PREFIX)) {
            return ciphertext;
        }
        try {
            EncryptedPayload payload = EncryptedPayload.decode(ciphertext.substring(TOKEN_PREFIX.length()));
            Cipher cipher = initializedCipher(Cipher.DECRYPT_MODE, payload.nonce());
            byte[] plaintext = cipher.doFinal(payload.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decrypt AES-GCM token", exception);
        }
    }

    private Cipher initializedCipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(mode, encryptionKey, new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, nonce));
        return cipher;
    }

    private record EncryptedPayload(byte[] nonce, byte[] ciphertext) {

        private static EncryptedPayload of(byte[] nonce, byte[] ciphertext) {
            return new EncryptedPayload(nonce, ciphertext);
        }

        private static EncryptedPayload decode(String encodedPayload) {
            byte[] payload = Base64.getUrlDecoder().decode(encodedPayload);
            int minimumLength = NONCE_LENGTH_BYTES + AUTHENTICATION_TAG_LENGTH_BYTES;
            if (payload.length < minimumLength) {
                throw new IllegalArgumentException("AES-GCM payload must contain a nonce and authentication tag");
            }
            return new EncryptedPayload(
                    Arrays.copyOfRange(payload, 0, NONCE_LENGTH_BYTES),
                    Arrays.copyOfRange(payload, NONCE_LENGTH_BYTES, payload.length));
        }

        private String encode() {
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        }
    }
}
