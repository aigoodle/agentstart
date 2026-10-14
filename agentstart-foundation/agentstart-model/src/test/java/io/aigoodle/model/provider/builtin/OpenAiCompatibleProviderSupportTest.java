package io.aigoodle.model.provider.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.provider.RemoteModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderSupportTest {

    @Test
    void customEndpointListsModelsAndExecutesChatWithoutUsingOfficialBaseUrl() throws Exception {
        var paths = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/v1/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            String body = exchange.getRequestURI().getPath().endsWith("/models")
                    ? "{\"data\":[{\"id\":\"custom-chat\"}]}"
                    : "{\"id\":\"test\",\"object\":\"chat.completion\",\"model\":\"custom-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"pong\"},\"finish_reason\":\"stop\"}]}";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            var provider = BuiltinModelProviders.custom();
            var endpoint = io.aigoodle.model.provider.ModelEndpoint.builder()
                    .providerName("openai-compatible")
                    .modelName("custom-chat")
                    .apiKey("test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/gateway/v1")
                    .build();
            assertThat(provider.listRemoteModels(endpoint))
                    .extracting(RemoteModel::getModelId)
                    .containsExactly("custom-chat");
            assertThat(provider.createChatModel(endpoint).call("ping")).isEqualTo("pong");
            assertThat(paths).containsExactly("/gateway/v1/models", "/gateway/v1/chat/completions");
            assertThat(provider.credentialSchema().fields())
                    .filteredOn(field -> "baseUrl".equals(field.getName()))
                    .allMatch(field -> field.isRequired());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesDefaultAndProviderSpecificModelCatalogUrls() {
        assertThat(OpenAiCompatibleModelProvider.resolveModelsUrl("https://api.openai.com", null))
                .isEqualTo("https://api.openai.com/v1/models");
        assertThat(OpenAiCompatibleModelProvider.resolveModelsUrl("https://vendor.example/v1/", null))
                .isEqualTo("https://vendor.example/v1/models");
        assertThat(OpenAiCompatibleModelProvider.resolveModelsUrl("https://vendor.example/v1/", "catalog/models"))
                .isEqualTo("https://vendor.example/v1/catalog/models");
    }

    @Test
    void mapsKnownModelsFromPresetsAndInfersUnknownModels() {
        PredefinedModel knownModel = PredefinedModel.builder()
                .model("chat-pro")
                .label("Chat Pro")
                .modelType(ModelType.LLM)
                .contextLength(32_000)
                .build();
        RemoteModelCatalogMapper mapper = new RemoteModelCatalogMapper(List.of(knownModel));

        List<RemoteModel> models = mapper.fromResponse(Map.of(
                "data",
                List.of(
                        Map.of("id", "chat-pro", "owned_by", "vendor"),
                        Map.of("id", "text-embedding-next"),
                        Map.of("owned_by", "invalid"),
                        "unexpected-entry")));

        assertThat(models).hasSize(2);
        assertThat(models.get(0))
                .extracting(
                        RemoteModel::getModelId,
                        RemoteModel::getLabel,
                        RemoteModel::getModelType,
                        RemoteModel::getContextLength,
                        RemoteModel::getOwnedBy,
                        RemoteModel::isTypeInferred)
                .containsExactly("chat-pro", "Chat Pro", ModelType.LLM, 32_000, "vendor", false);
        assertThat(models.get(1))
                .extracting(RemoteModel::getModelId, RemoteModel::getModelType, RemoteModel::isTypeInferred)
                .containsExactly("text-embedding-next", ModelType.TEXT_EMBEDDING, true);
    }

    @Test
    void treatsMissingCatalogDataAsAnEmptyResult() {
        RemoteModelCatalogMapper mapper = new RemoteModelCatalogMapper(List.of());

        assertThat(mapper.fromResponse(null)).isEmpty();
        assertThat(mapper.fromResponse(Map.of("data", "not-a-list"))).isEmpty();
    }
}
