package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.mapper.ProviderCredentialMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderCredentialServiceTest {

    @Test
    void mergesOnlyCredentialValuesActuallySuppliedByTheUi() {
        Map<String, Object> merged = CredentialPatchMerger.merge(
                Map.of("apiKey", "old-secret", "baseUrl", "https://old.example"),
                Map.of("apiKey", "", "baseUrl", "https://new.example", "timeout", 30));

        assertThat(merged)
                .containsEntry("apiKey", "old-secret")
                .containsEntry("baseUrl", "https://new.example")
                .containsEntry("timeout", 30);
    }

    @Test
    void keepsExistingCredentialsWhenThePatchIsAbsent() {
        Map<String, Object> existing = Map.of("apiKey", "secret");

        Map<String, Object> merged = CredentialPatchMerger.merge(existing, null);

        assertThat(merged).containsExactlyEntriesOf(existing);
        assertThat(merged).isNotSameAs(existing);
    }

    @Test
    void savesARegistrationWithNormalizedIdentityAndEncryptedValues() {
        ProviderCredentialMapper mapper = mock(ProviderCredentialMapper.class);
        CredentialCodec codec = mock(CredentialCodec.class);
        when(codec.encode("default", Map.of("apiKey", "secret"))).thenReturn("encrypted");
        ProviderCredentialService service = new ProviderCredentialService(mapper, codec);

        ProviderCredentialEntity saved =
                service.save(new ProviderCredentialRegistration(" ", "openai", " ", Map.of("apiKey", "secret")));

        assertThat(saved.getTenantId()).isEqualTo("default");
        assertThat(saved.getCredentialName()).isEqualTo("openai");
        assertThat(saved.getEncryptedConfig()).isEqualTo("encrypted");
        assertThat(saved.getEnabled()).isTrue();
        verify(mapper).insert(saved);
    }

    @Test
    void replacesAnExistingEncryptedCredentialPayload() {
        ProviderCredentialMapper mapper = mock(ProviderCredentialMapper.class);
        CredentialCodec codec = mock(CredentialCodec.class);
        ProviderCredentialEntity existing = new ProviderCredentialEntity();
        existing.setId("credential-id");
        existing.setTenantId("default");
        existing.setEncryptedConfig("old-ciphertext");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(codec.encode("default", Map.of("apiKey", "rotated"))).thenReturn("new-ciphertext");
        ProviderCredentialService service = new ProviderCredentialService(mapper, codec);

        service.update("credential-id", Map.of("apiKey", "rotated"));

        assertThat(existing.getEncryptedConfig()).isEqualTo("new-ciphertext");
        verify(mapper).update(eq(existing), any());
    }
}
