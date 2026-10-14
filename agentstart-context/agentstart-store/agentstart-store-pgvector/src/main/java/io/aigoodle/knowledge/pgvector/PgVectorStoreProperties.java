package io.aigoodle.knowledge.pgvector;

import lombok.Data;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the pgvector-backed vector store. Each knowledge dataset gets
 * its own physical table named {@code tablePrefix + sanitized(datasetId)}, so
 * dataset isolation is structural and no per-query metadata filter is needed.
 */
@Data
@ConfigurationProperties(prefix = "spring-agent.knowledge.pgvector")
public class PgVectorStoreProperties {

    /** Postgres schema hosting the vector tables. */
    private String schemaName = "public";

    /** Prefix for per-dataset tables; final name is {prefix}{sanitizedDatasetId}. */
    private String tablePrefix = "embeddings_";

    /** Run DDL (CREATE EXTENSION, CREATE TABLE, CREATE INDEX) on first use. */
    private boolean initializeSchema = true;

    /** Similarity metric. COSINE_DISTANCE is the default and works for most embeddings. */
    private PgVectorStore.PgDistanceType distanceType = PgVectorStore.PgDistanceType.COSINE_DISTANCE;

    /** ANN index type; HNSW is fastest for high recall. Use NONE to skip indexing. */
    private PgVectorStore.PgIndexType indexType = PgVectorStore.PgIndexType.HNSW;

    /**
     * Vector dimensions. -1 lets Spring AI infer from the embedding model — usually
     * the right choice. Override only if you use a mixed-dimension setup.
     */
    private int dimensions = PgVectorStore.INVALID_EMBEDDING_DIMENSION;

    /** Max documents per JDBC batch on {@code add}. */
    private int maxDocumentBatchSize = 10_000;
}
