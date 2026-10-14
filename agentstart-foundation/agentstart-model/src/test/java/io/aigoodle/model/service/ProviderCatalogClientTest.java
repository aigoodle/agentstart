package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.registry.ModelProviderRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProviderCatalogClientTest {

    @Test
    void separatesTransportCredentialsFromProviderProperties() {
        ModelProviderRegistry registry = mock(ModelProviderRegistry.class);
        ModelProvider provider = mock(ModelProvider.class);
        when(registry.get("openai")).thenReturn(provider);
        when(provider.listRemoteModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        ProviderCatalogClient client =
                new ProviderCatalogClient(registry, mock(ProviderCredentialService.class), mock(CredentialCodec.class));

        client.preview(
                "openai",
                Map.of(
                        "apiKey", "secret",
                        "baseUrl", "https://models.example.test",
                        "organization", "acme"));

        ArgumentCaptor<ModelEndpoint> endpoint = ArgumentCaptor.forClass(ModelEndpoint.class);
        verify(provider).listRemoteModels(endpoint.capture());
        assertThat(endpoint.getValue().getApiKey()).isEqualTo("secret");
        assertThat(endpoint.getValue().getBaseUrl()).isEqualTo("https://models.example.test");
        assertThat(endpoint.getValue().getProperties()).containsExactly(Map.entry("organization", "acme"));
    }

    @Test
    void explainsTheRequiredActionWhenRefreshHasNoCredential() {
        ProviderCatalogClient client = new ProviderCatalogClient(
                mock(ModelProviderRegistry.class), mock(ProviderCredentialService.class), mock(CredentialCodec.class));

        assertThatThrownBy(() -> client.refresh("tenant-1", "openai"))
                .isInstanceOf(PlatformException.class)
                .hasMessage("Save a provider credential before refreshing the catalog");
    }
}
