package io.aigoodle.knowledge.index;

import io.aigoodle.common.util.JsonUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A dependency-free vector store backed by a plain relational table, so embeddings
 * live in the same database as everything else (the pgvector extension is not
 * required). Vectors are stored as a comma-joined float string and cosine similarity
 * is computed in Java over the dataset's rows. Each instance is scoped to one dataset,
 * matching {@link VectorStoreManager}'s per-dataset model.
 * <p>
 * Suitable for small/medium datasets; swap in a native pgvector/Elasticsearch store
 * via a custom {@link VectorStoreFactory} for large-scale workloads.
 */
public class JdbcVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final JdbcVectorStoreConfiguration configuration;

    public JdbcVectorStore(
            JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, JdbcVectorStoreConfiguration configuration) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.configuration = configuration;
    }

    /** @deprecated Use the configuration-based constructor. */
    @Deprecated(forRemoval = false)
    public JdbcVectorStore(
            JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, String datasetId, String tableName) {
        this(jdbcTemplate, embeddingModel, new JdbcVectorStoreConfiguration(datasetId, tableName));
    }

    @Override
    public void add(List<Document> documents) {
        for (Document document : documents) {
            float[] embedding = embeddingModel.embed(document.getText());
            jdbcTemplate.update(
                    "INSERT INTO " + configuration.tableName()
                            + " (id, dataset_id, content, metadata_json, embedding) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    document.getId(),
                    configuration.datasetId(),
                    document.getText(),
                    JsonUtils.toJson(document.getMetadata()),
                    JdbcVectorCodec.encode(embedding));
        }
    }

    @Override
    public void delete(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        String placeholders =
                String.join(",", documentIds.stream().map(ignored -> "?").toList());
        Object[] parameters = new Object[documentIds.size() + 1];
        parameters[0] = configuration.datasetId();
        System.arraycopy(documentIds.toArray(), 0, parameters, 1, documentIds.size());
        jdbcTemplate.update(
                "DELETE FROM " + configuration.tableName() + " WHERE dataset_id = ? AND id IN (" + placeholders + ")",
                parameters);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException("Filter-expression delete is not supported by JdbcVectorStore");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        float[] queryEmbedding = embeddingModel.embed(request.getQuery());
        List<Document> matchingDocuments = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, content, metadata_json, embedding FROM " + configuration.tableName()
                        + " WHERE dataset_id = ?",
                resultSet -> {
                    float[] storedEmbedding = JdbcVectorCodec.decode(resultSet.getString("embedding"));
                    double score = VectorSimilarity.cosine(queryEmbedding, storedEmbedding);
                    if (score >= request.getSimilarityThreshold()) {
                        Map<String, Object> metadata = JsonUtils.parseMap(resultSet.getString("metadata_json"));
                        matchingDocuments.add(Document.builder()
                                .id(resultSet.getString("id"))
                                .text(resultSet.getString("content"))
                                .metadata(metadata)
                                .score(score)
                                .build());
                    }
                },
                configuration.datasetId());
        matchingDocuments.sort(Comparator.comparingDouble(document -> -document.getScore()));
        return matchingDocuments.size() > request.getTopK()
                ? matchingDocuments.subList(0, request.getTopK())
                : matchingDocuments;
    }
}
