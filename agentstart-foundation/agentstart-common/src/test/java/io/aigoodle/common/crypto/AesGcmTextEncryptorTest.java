package io.aigoodle.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmTextEncryptorTest {

    private final AesGcmTextEncryptor encryptor = new AesGcmTextEncryptor("readable-test-secret");

    @Test
    void keepsTheEstablishedTokenFormatAndRoundTripsUnicodeText() {
        String token = encryptor.encrypt("你好, AES-GCM");

        assertThat(token).startsWith("agcm:");
        byte[] payload = Base64.getUrlDecoder().decode(token.substring("agcm:".length()));
        assertThat(payload).hasSizeGreaterThanOrEqualTo(12 + 16);
        assertThat(encryptor.decrypt(token)).isEqualTo("你好, AES-GCM");
    }

    @Test
    void rejectsMalformedOrTruncatedEncryptedTokensWithOnePublicError() {
        assertThatThrownBy(() -> encryptor.decrypt("agcm:not-base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to decrypt AES-GCM token")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        String truncated = "agcm:" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]);
        assertThatThrownBy(() -> encryptor.decrypt(truncated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to decrypt AES-GCM token");
    }

    @Test
    void preservesNullAndLegacyPlaintextValues() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.decrypt("legacy-api-key")).isEqualTo("legacy-api-key");
    }
}
