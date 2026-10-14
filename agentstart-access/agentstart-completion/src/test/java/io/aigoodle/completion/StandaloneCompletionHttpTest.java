package io.aigoodle.completion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aigoodle.common.exception.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        classes = StandaloneCompletionHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
class StandaloneCompletionHttpTest {
    @Autowired
    private WebTestClient client;

    @Test
    void standaloneReactiveSurfacePreservesErrorEnvelopeWithoutWebModule() {
        assertThrows(
                ClassNotFoundException.class, () -> Class.forName("io.aigoodle.web.config.GoodleWebAutoConfiguration"));
        client.get()
                .uri("/test/errors/platform")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("agent_not_found")
                .jsonPath("$.traceId")
                .isNotEmpty();
        client.get()
                .uri("/test/errors/unexpected")
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("server_error")
                .jsonPath("$.message")
                .isEqualTo("Internal server error");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "io.aigoodle.model.config.GoodleModelAutoConfiguration",
                "io.aigoodle.agent.config.AgentRuntimeAutoConfiguration",
                "io.aigoodle.memory.config.GoodleMemoryAutoConfiguration",
                "io.aigoodle.skill.config.GoodleSkillAutoConfiguration",
                "io.aigoodle.knowledge.config.GoodleKnowledgeAutoConfiguration",
                "io.aigoodle.workflow.config.GoodleWorkflowAutoConfiguration"
            })
    static class TestApplication {
        @Bean
        ErrorsController errorsController() {
            return new ErrorsController();
        }
    }

    @RestController
    static class ErrorsController {
        @GetMapping("/test/errors/platform")
        String platform() {
            throw new PlatformException("agent_not_found", "No matching agent", null);
        }

        @GetMapping("/test/errors/unexpected")
        String unexpected() {
            throw new IllegalStateException("private diagnostic detail");
        }
    }
}
