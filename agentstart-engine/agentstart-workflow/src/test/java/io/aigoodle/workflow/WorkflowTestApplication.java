package io.aigoodle.workflow;

import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.workflow.support.FakeEmbeddingProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test context wiring the model + knowledge + workflow modules together, with a
 * deterministic embedding provider so the knowledge-retrieval node can be exercised
 * offline.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class WorkflowTestApplication {

    @Bean
    public ModelProvider fakeEmbeddingProvider() {
        return new FakeEmbeddingProvider();
    }
}
