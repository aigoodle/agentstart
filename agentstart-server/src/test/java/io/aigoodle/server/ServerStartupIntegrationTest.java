package io.aigoodle.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aigoodle.agent.eval.AgentEvaluationRunner;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.server.support.FakeEmbeddingProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = GoodleServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:servermigration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.continue-on-error=false",
            "spring-agent.model.encryption-secret=server-integration-test-secret",
            "spring-agent.demo.enabled=true",
            "management.health.redis.enabled=false",
            "spring-agent.web.max-upload-size=128B"
        })
@Import(ServerStartupIntegrationTest.TestModels.class)
@AutoConfigureWebTestClient
class ServerStartupIntegrationTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private JsonMapper httpMapper;

    @Autowired
    private io.aigoodle.agent.runtime.AgentRuntimeRegistry runtimes;

    @Autowired
    private io.aigoodle.runtime.agentscope.AgentScopeAgentFactory agentScopeFactory;

    @Test
    void standaloneServerInstallsNativeAndAgentScopeBackends() {
        assertTrue(runtimes.available("NATIVE"));
        assertTrue(runtimes.available("AGENTSCOPE"));
        assertTrue(agentScopeFactory instanceof io.aigoodle.runtime.agentscope.DefaultAgentScopeAgentFactory);
    }

    @Test
    void servesHealthAndModelCatalogFromStandaloneServer() {
        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
        client.get()
                .uri("/agent-start/model-providers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .jsonPath("$.data")
                .isArray();
    }

    @Test
    void discoversRetrievalToolsAndPlatformChannels() {
        client.get()
                .uri("/agent-start/tools")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[?(@.name == 'knowledge_search')]")
                .isNotEmpty();
        client.get()
                .uri("/agent-start/channels")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .jsonPath("$.data")
                .isArray();
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AgentEvaluationRunner evaluationRunner;

    @Test
    void governedToolHttpInvocationPublishesMetrics() {
        double before =
                meterRegistry.find("spring.agent.tools.executions").tag("tool", "calculator").counters().stream()
                        .mapToDouble(counter -> counter.count())
                        .sum();
        client.post()
                .uri("/agent-start/tools/calculator/invoke")
                .bodyValue(Map.of("expression", "2*(3+4)"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo("14");
        double after = meterRegistry.find("spring.agent.tools.executions").tag("tool", "calculator").counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum();
        assertEquals(before + 1, after);
        assertNotNull(evaluationRunner);
    }

    @Test
    void reactiveMultipartUploadAndPreviewUseTheExistingApiProtocol() {
        httpMapper.readValue(
                "{\"providerName\":\"fake\",\"modelName\":\"hash-embed\",\"modelType\":\"TEXT_EMBEDDING\"}",
                ModelRegistration.class);
        byte[] model = client.post()
                .uri("/agent-start/models")
                .bodyValue(Map.of(
                        "providerName",
                        "fake",
                        "modelName",
                        "hash-embed",
                        "modelType",
                        "TEXT_EMBEDDING",
                        "credentials",
                        Map.of("dimensions", 32)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .returnResult()
                .getResponseBody();
        String embeddingId = JsonUtils.readTree(new String(model, StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asText();
        byte[] created = client.post()
                .uri("/agent-start/datasets")
                .bodyValue(Map.of(
                        "name",
                        "multipart-regression",
                        "embeddingModelId",
                        embeddingId,
                        "indexingTechnique",
                        "HIGH_QUALITY"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .returnResult()
                .getResponseBody();
        String datasetId = JsonUtils.readTree(new String(created, StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asText();
        var upload = multipart("upload.txt", "A small document for reactive upload.");
        client.post()
                .uri("/agent-start/datasets/" + datasetId + "/documents/upload")
                .bodyValue(upload.build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .jsonPath("$.data.name")
                .isEqualTo("upload.txt")
                .jsonPath("$.data.tenantId")
                .isEqualTo("default");
        client.post()
                .uri("/agent-start/datasets/preview-chunks")
                .bodyValue(
                        multipart("preview.txt", "Preview this uploaded text.").build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ok")
                .jsonPath("$.data.totalChunks")
                .isEqualTo(1);
        client.post()
                .uri("/agent-start/datasets/preview-chunks")
                .bodyValue(multipart("oversized.txt", "x".repeat(256)).build())
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("upload_too_large");
        var invalidRule = multipart("preview.txt", "Some text.");
        invalidRule.part("rule", "{invalid");
        client.post()
                .uri("/agent-start/datasets/preview-chunks")
                .bodyValue(invalidRule.build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("rule_invalid");
        client.post()
                .uri("/agent-start/datasets/preview-chunks")
                .bodyValue(multipart("empty.txt", "").build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("file_required");
    }

    private static MultipartBodyBuilder multipart(String filename, String text) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(MediaType.TEXT_PLAIN);
        return builder;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestModels {
        @Bean
        ThreadProbeController threadProbeController() {
            return new ThreadProbeController();
        }

        @Bean
        ModelProvider testEmbeddingProvider() {
            return new FakeEmbeddingProvider();
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class ThreadProbeController {
        @org.springframework.web.bind.annotation.GetMapping("/test/request-thread")
        Map<String, Object> requestThread() {
            return Map.of(
                    "thread",
                    Thread.currentThread().getName(),
                    "tenant",
                    io.aigoodle.common.context.UserContextHolder.currentTenantId(),
                    "user",
                    io.aigoodle.common.context.UserContextHolder.currentUserId());
        }
    }

    @Test
    void synchronousControllersUseWorkerThreadsWithTrustedIdentity() {
        client.get()
                .uri("/test/request-thread")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.thread")
                .value(value -> assertTrue(value.toString().startsWith("agentstart-http-")))
                .jsonPath("$.tenant")
                .isEqualTo("default")
                .jsonPath("$.user")
                .isEqualTo("demo-user");
    }
}
