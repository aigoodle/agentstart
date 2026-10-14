package io.aigoodle.knowledge.milvus;

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Milvus-backed vector store. Each dataset gets its own
 * collection named {@code collectionPrefix + sanitized(datasetId)}.
 */
@Data
@ConfigurationProperties(prefix = "spring-agent.knowledge.milvus")
public class MilvusStoreProperties {

    /** Milvus host, e.g. localhost. */
    private String host = "localhost";

    /** Milvus gRPC port. */
    private int port = 19530;

    /** URI form ("https://cluster-xxx.zillizcloud.com"); overrides host+port when set. */
    private String uri;

    /** Auth token / API key (mutually exclusive with username/password). */
    private String token;

    /** Auth username (optional). */
    private String username;

    /** Auth password (optional). */
    private String password;

    /** Target database name. */
    private String databaseName = "default";

    /** Per-dataset collection name prefix. */
    private String collectionPrefix = "embeddings_";

    /** Vector index type. IVF_FLAT is a safe default; use HNSW for higher recall. */
    private IndexType indexType = IndexType.IVF_FLAT;

    /** Similarity metric. */
    private MetricType metricType = MetricType.COSINE;

    /** Vector field name inside the collection. */
    private String embeddingFieldName = "embedding";

    /**
     * Vector dimensions. -1 lets Spring AI/Milvus infer from the embedding model
     * — usually the right choice.
     */
    private int dimensions = -1;
}
