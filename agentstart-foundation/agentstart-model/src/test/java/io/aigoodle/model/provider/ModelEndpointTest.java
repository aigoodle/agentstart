package io.aigoodle.model.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelEndpointTest {

    @Test
    void readsTypedProviderPropertiesTolerantly() {
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .properties(Map.of(
                        "dimensions", 1_536,
                        "temperature", "0.65",
                        "invalid", "not-a-number"))
                .build();

        assertThat(endpoint.intProperty("dimensions")).isEqualTo(1_536);
        assertThat(endpoint.decimalProperty("temperature")).isEqualTo(0.65);
        assertThat(endpoint.intProperty("invalid")).isNull();
        assertThat(endpoint.decimalProperty("invalid")).isNull();
        assertThat(endpoint.decimalProperty("missing")).isNull();
    }

    @Test
    void resolvesBaseUrlByExplicitPrecedence() {
        ModelEndpoint endpointColumn =
                ModelEndpoint.builder().baseUrl("https://endpoint.example/v1").build();
        ModelEndpoint propertyOverride = ModelEndpoint.builder()
                .baseUrl("https://endpoint.example/v1")
                .properties(Map.of("baseUrl", "https://property.example/v2"))
                .build();

        assertThat(ModelEndpoint.builder().build().resolveBaseUrl("https://default.example"))
                .isEqualTo("https://default.example");
        assertThat(endpointColumn.resolveBaseUrl("https://default.example")).isEqualTo("https://endpoint.example/v1");
        assertThat(propertyOverride.resolveBaseUrl("https://default.example")).isEqualTo("https://property.example/v2");
    }
}
