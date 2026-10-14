package io.aigoodle.knowledge.elasticsearch;

import java.util.List;
import lombok.Data;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Elasticsearch-backed vector store. Each dataset gets its
 * own index named {@code indexPrefix + sanitized(datasetId)}.
 */
@Data
@ConfigurationProperties(prefix = "spring-agent.knowledge.elasticsearch")
public class ElasticsearchStoreProperties {

    /** Elasticsearch endpoint URIs, e.g. http://localhost:9200 (comma-separated for a cluster). */
    private List<String> uris = List.of("http://localhost:9200");

    /** Basic-auth username (optional). */
    private String username;

    /** Basic-auth password (optional). */
    private String password;

    /** API key for API-key auth (mutually exclusive with basic auth). */
    private String apiKey;

    /** Per-dataset index name prefix; final index is {prefix}{sanitizedDatasetId}. */
    private String indexPrefix = "agent-vector-";

    /** Similarity metric applied by the dense_vector mapping. */
    private SimilarityFunction similarity = SimilarityFunction.cosine;

    /**
     * Vector dimensions. -1 means "infer from the embedding model on first use"
     * — usually the right choice.
     */
    private int dimensions = -1;
}
