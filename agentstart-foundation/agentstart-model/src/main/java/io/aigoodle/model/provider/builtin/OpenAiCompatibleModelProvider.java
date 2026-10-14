package io.aigoodle.model.provider.builtin;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.provider.RemoteModel;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Provider for any service exposing an OpenAI-compatible REST API. A single
 * implementation covers OpenAI itself plus DeepSeek, Zhipu, Moonshot, Qwen,
 * Volcengine Ark and most Chinese vendors — they differ only by base url and the
 * set of models they ship, which is why concrete presets are just instances of
 * this class with a different {@code name}/{@code defaultBaseUrl}.
 */
public class OpenAiCompatibleModelProvider extends AbstractModelProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiCompatibleModelProvider.class);

    private final String name;
    private final String label;
    private final String defaultBaseUrl;
    private final List<PredefinedModel> predefinedModels;
    private final RemoteModelCatalogMapper remoteModelCatalogMapper;

    public OpenAiCompatibleModelProvider(
            String name, String label, String defaultBaseUrl, List<PredefinedModel> predefinedModels) {
        this.name = name;
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.predefinedModels = predefinedModels == null ? List.of() : List.copyOf(predefinedModels);
        this.remoteModelCatalogMapper = new RemoteModelCatalogMapper(this.predefinedModels);
    }

    public OpenAiCompatibleModelProvider(String name, String label, String defaultBaseUrl) {
        this(name, label, defaultBaseUrl, List.of());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.LLM, ModelType.TEXT_EMBEDDING);
    }

    @Override
    public CredentialSchema credentialSchema() {
        return CredentialSchema.of(
                CredentialField.secret("apiKey", "API Key", true),
                CredentialField.builder()
                        .name("baseUrl")
                        .label("Base URL")
                        .type(CredentialField.Type.TEXT)
                        .required(defaultBaseUrl == null)
                        .defaultValue(defaultBaseUrl)
                        .placeholder(defaultBaseUrl)
                        .build());
    }

    @Override
    public List<PredefinedModel> predefinedModels() {
        return predefinedModels;
    }

    private OpenAiClients buildClients(ModelEndpoint endpoint) {
        requireApiKey(endpoint);
        String baseUrl = endpoint.resolveBaseUrl(defaultBaseUrl);
        Integer configuredSeconds = endpoint.intProperty("readTimeoutSeconds");
        Duration timeout =
                Duration.ofSeconds(configuredSeconds != null && configuredSeconds > 0 ? configuredSeconds : 300);
        var sync = OpenAiSetup.setupSyncClient(
                baseUrl,
                endpoint.getApiKey(),
                null,
                null,
                null,
                null,
                false,
                false,
                endpoint.getModelName(),
                timeout,
                3,
                null,
                Map.of(),
                ObservationRegistry.NOOP,
                null,
                List.of());
        var async = OpenAiSetup.setupAsyncClient(
                baseUrl,
                endpoint.getApiKey(),
                null,
                null,
                null,
                null,
                false,
                false,
                endpoint.getModelName(),
                timeout,
                3,
                null,
                Map.of(),
                ObservationRegistry.NOOP,
                null,
                List.of());
        return new OpenAiClients(sync, async);
    }

    @Override
    public ChatModel createChatModel(ModelEndpoint endpoint) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(endpoint.getModelName());
        applyParameters(endpoint, options);
        OpenAiClients clients = buildClients(endpoint);
        return OpenAiChatModel.builder()
                .openAiClient(clients.sync())
                .openAiClientAsync(clients.async())
                .options(options.build())
                .build();
    }

    /** Pull optional model parameters from the DB-stored config (temperature, etc.). */
    private void applyParameters(ModelEndpoint endpoint, OpenAiChatOptions.Builder options) {
        Double temperature = endpoint.decimalProperty("temperature");
        if (temperature != null) {
            options.temperature(temperature);
        }
        Double topP = endpoint.decimalProperty("topP");
        if (topP != null) {
            options.topP(topP);
        }
        Integer maxTokens = endpoint.intProperty("maxTokens");
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelEndpoint endpoint) {
        OpenAiEmbeddingOptions.Builder options =
                OpenAiEmbeddingOptions.builder().model(endpoint.getModelName());
        Integer dimensions = endpoint.intProperty("dimensions");
        if (dimensions != null) {
            options.dimensions(dimensions);
        }
        return OpenAiEmbeddingModel.builder()
                .openAiClient(buildClients(endpoint).sync())
                .options(options.build())
                .build();
    }

    @Override
    public boolean supportsRemoteModelListing() {
        return true;
    }

    /**
     * Hit {@code GET {baseUrl}/models} and translate the OpenAI-shaped response into
     * {@link RemoteModel}s. When a returned id matches one of our presets we inherit
     * the preset's type/context/dimensions; otherwise we infer from the id itself.
     * <p>
     * Any HTTP or parse failure is surfaced by wrapping in {@link IllegalStateException}
     * so the UI can render a "拉取失败" hint alongside the vendor's own error message —
     * this is the primary "did my key work" signal, so we do <em>not</em> silently return
     * {@link #predefinedModels()} on error.
     */
    @Override
    public List<RemoteModel> listRemoteModels(ModelEndpoint endpoint) {
        requireApiKey(endpoint);
        String baseUrl = endpoint.resolveBaseUrl(defaultBaseUrl);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Provider '" + name + "' has no base url");
        }
        String modelsUrl = resolveModelsUrl(baseUrl, endpoint.property("modelsPath"));

        RestClient client = RestClient.builder()
                .requestFactory(newRequestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body;
        try {
            body = client.get()
                    .uri(modelsUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + endpoint.getApiKey())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception exception) {
            logger.warn(
                    "Remote model listing failed for provider={} url={}: {}", name, modelsUrl, exception.getMessage());
            throw new IllegalStateException(
                    "Failed to list models from " + modelsUrl + ": " + exception.getMessage(), exception);
        }
        return remoteModelCatalogMapper.fromResponse(body);
    }

    /**
     * Concatenate the {@code /models} path onto a base url without double-slashes,
     * and honour caller-provided overrides. Most OpenAI-compat vendors serve the
     * listing at {@code {base}/models}, but some (Ollama, ZhipuAI's official SDK) use
     * different paths — support a {@code modelsPath} property for those edge cases.
     */
    static String resolveModelsUrl(String baseUrl, String configuredPath) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (configuredPath != null && !configuredPath.isBlank()) {
            String normalizedPath = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
            return trimmed + normalizedPath;
        }
        // OpenAI's official base is https://api.openai.com — no /v1 — normalise both.
        if (!trimmed.contains("/v")) {
            return trimmed + "/v1/models";
        }
        return trimmed + "/models";
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory newRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return requestFactory;
    }

    private record OpenAiClients(com.openai.client.OpenAIClient sync, com.openai.client.OpenAIClientAsync async) {}
}
