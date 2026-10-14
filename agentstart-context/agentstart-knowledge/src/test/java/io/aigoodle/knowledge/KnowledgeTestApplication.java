package io.aigoodle.knowledge;

import io.aigoodle.knowledge.support.FakeEmbeddingProvider;
import io.aigoodle.model.provider.ModelProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test context: enables auto-configuration for the model + knowledge modules and
 * publishes a deterministic embedding provider so RAG can be exercised offline.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class KnowledgeTestApplication {

    @Bean
    public ModelProvider fakeEmbeddingProvider() {
        return new FakeEmbeddingProvider();
    }
}
