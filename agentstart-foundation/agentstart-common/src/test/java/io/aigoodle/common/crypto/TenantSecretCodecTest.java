package io.aigoodle.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorSecretCodecTest {
    @Test
    void derivesDifferentAuthenticatedKeysForEachTenant() {
        String root = "test-root-key-that-is-not-used-in-production";
        TenantSecretCodec codec =
                new TenantSecretCodec(new DerivedTenantTextEncryptor(root), new AesGcmTextEncryptor(root));

        String encrypted = codec.encode("tenant-a", Map.of("token", "secret"));

        assertThat(encrypted).startsWith("at1:").doesNotContain("secret");
        assertThat(codec.decode("tenant-a", encrypted)).containsEntry("token", "secret");
        assertThatThrownBy(() -> codec.decode("tenant-b", encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsLegacyGlobalCiphertextForRollingUpgrade() {
        String root = "legacy-root";
        AesGcmTextEncryptor legacy = new AesGcmTextEncryptor(root);
        TenantSecretCodec codec = new TenantSecretCodec(new DerivedTenantTextEncryptor(root), legacy);
        String oldValue = legacy.encrypt("{\"token\":\"old-secret\"}");

        assertThat(codec.decode("tenant-a", oldValue)).containsEntry("token", "old-secret");
    }
}
