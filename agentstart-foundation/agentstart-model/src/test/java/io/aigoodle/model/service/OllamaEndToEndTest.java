package io.aigoodle.model.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aigoodle.model.ModelTestApplication;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real end-to-end invocation against a local Ollama server. Skipped automatically
 * (not failed) when Ollama is not reachable, so CI without Ollama stays green.
 */
@SpringBootTest(classes = ModelTestApplication.class)
class OllamaEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(OllamaEndToEndTest.class);

    @Autowired
    private ModelService modelService;

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.test-model}")
    private String testModel;

    @Test
    void chatThroughRegisteredOllamaModel() {
        Assumptions.assumeTrue(ollamaReachable(), "Ollama not reachable at " + baseUrl + " — skipping");

        ModelEntity model = modelService.register(ModelRegistration.builder()
                .tenantId("e2e")
                .providerName("ollama")
                .modelName(testModel)
                .modelType(ModelType.LLM)
                .credentials(Map.of("baseUrl", baseUrl))
                .build());

        ChatClient client = modelService.getModelInstance("e2e", model.getId()).getChatClient();
        String answer = client.prompt()
                .user("Reply with exactly one word: hello")
                .call()
                .content();

        log.info("Ollama answered: {}", answer);
        assertNotNull(answer);
        assertTrue(answer.length() > 0, "expected a non-empty completion");
    }

    private boolean ollamaReachable() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
