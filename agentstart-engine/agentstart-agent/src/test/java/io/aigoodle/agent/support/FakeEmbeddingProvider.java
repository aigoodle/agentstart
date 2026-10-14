package io.aigoodle.agent.support;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingModel;

/** Test embedding provider ("fake") for vector-memory tests. */
public class FakeEmbeddingProvider extends AbstractModelProvider {

    @Override
    public String getName() {
        return "fake";
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.TEXT_EMBEDDING);
    }

    @Override
    public CredentialSchema credentialSchema() {
        return CredentialSchema.of();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelEndpoint endpoint) {
        Integer dim = endpoint.intProperty("dimensions");
        return new HashingEmbeddingModel(dim == null ? 256 : dim);
    }
}
