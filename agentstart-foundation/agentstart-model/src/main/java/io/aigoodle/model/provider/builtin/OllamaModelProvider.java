package io.aigoodle.model.provider.builtin;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.RemoteModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Provider for a local or remote <a href="https://ollama.com">Ollama</a> server.
 * No api key is required; only the server base url (defaults to localhost:11434).
 */
public class OllamaModelProvider extends AbstractModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelProvider.class);

    public static final String NAME = "ollama";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final OllamaRemoteModelCatalog remoteModelCatalog = new OllamaRemoteModelCatalog();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getLabel() {
        return "Ollama";
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.LLM, ModelType.TEXT_EMBEDDING);
    }

    @Override
    public CredentialSchema credentialSchema() {
        return CredentialSchema.of(CredentialField.builder()
                .name("baseUrl")
                .label("Base URL")
                .type(CredentialField.Type.TEXT)
                .required(false)
                .defaultValue(DEFAULT_BASE_URL)
                .placeholder(DEFAULT_BASE_URL)
                .build());
    }

    private OllamaApi buildApi(ModelEndpoint endpoint) {
        return OllamaApi.builder().baseUrl(resolveBaseUrl(endpoint)).build();
    }

    @Override
    public ChatModel createChatModel(ModelEndpoint endpoint) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(endpoint.getModelName());
        applyTemperature(endpoint, options);
        return OllamaChatModel.builder()
                .ollamaApi(buildApi(endpoint))
                .options(options.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelEndpoint endpoint) {
        OllamaEmbeddingOptions options =
                OllamaEmbeddingOptions.builder().model(endpoint.getModelName()).build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(buildApi(endpoint))
                .options(options)
                .build();
    }

    @Override
    public boolean supportsRemoteModelListing() {
        return true;
    }

    /**
     * Ollama returns {@code {models:[{name, size, digest, ...}]}} at
     * {@code GET /api/tags}. We emit each installed model once with the inferred type;
     * users who want to alias a chat model as embedding (Ollama does support it at
     * runtime) can add the alternate entry via "手动添加".
     */
    @Override
    public List<RemoteModel> listRemoteModels(ModelEndpoint endpoint) {
        String catalogUrl = catalogUrl(resolveBaseUrl(endpoint));

        RestClient restClient = RestClient.builder()
                .requestFactory(newRequestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> responseBody;
        try {
            responseBody = restClient.get().uri(catalogUrl).retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException exception) {
            log.warn("Ollama model discovery failed at {}: {}", catalogUrl, exception.getMessage());
            throw new IllegalStateException("Failed to list Ollama models from " + catalogUrl, exception);
        }
        return remoteModelCatalog.fromResponse(responseBody);
    }

    private static String resolveBaseUrl(ModelEndpoint endpoint) {
        String endpointBaseUrl = endpoint.getBaseUrl();
        String defaultBaseUrl =
                endpointBaseUrl != null && !endpointBaseUrl.isBlank() ? endpointBaseUrl : DEFAULT_BASE_URL;
        return endpoint.propertyOrDefault("baseUrl", defaultBaseUrl);
    }

    private static String catalogUrl(String baseUrl) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/api/tags";
    }

    private static void applyTemperature(ModelEndpoint endpoint, OllamaChatOptions.Builder options) {
        String configuredTemperature = endpoint.property("temperature");
        if (configuredTemperature == null || configuredTemperature.isBlank()) {
            return;
        }
        try {
            options.temperature(Double.valueOf(configuredTemperature));
        } catch (NumberFormatException invalidTemperature) {
            // Keep Spring AI's default when an older stored endpoint contains bad data.
        }
    }

    private static SimpleClientHttpRequestFactory newRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return requestFactory;
    }
}
