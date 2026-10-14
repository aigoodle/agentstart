package io.aigoodle.knowledge.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RetrievalCandidate;
import io.aigoodle.rag.spi.Retriever;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/** Elasticsearch-native BM25 recall over the same documents used by the vector store. */
public final class ElasticsearchSparseRetriever implements Retriever {
    private final Rest5Client client;
    private final ElasticsearchStoreProperties properties;
    private final DatasetService datasetService;

    public ElasticsearchSparseRetriever(
            Rest5Client client, ElasticsearchStoreProperties properties, DatasetService datasetService) {
        this.client = client;
        this.properties = properties;
        this.datasetService = datasetService;
    }

    @Override
    public String getName() {
        return "elasticsearch-bm25";
    }

    @Override
    public RagPolicy.Channel getChannel() {
        return RagPolicy.Channel.SPARSE;
    }

    @Override
    public List<RetrievalCandidate> retrieve(PlannedQuery query, RagRequest request) {
        List<RetrievalCandidate> result = new ArrayList<>();
        for (String datasetId : request.datasetIds()) {
            DatasetEntity dataset = datasetService.require(request.tenantId(), datasetId);
            result.addAll(search(dataset, query, request));
        }
        return result;
    }

    private List<RetrievalCandidate> search(DatasetEntity dataset, PlannedQuery query, RagRequest request) {
        int limit = Math.max(1, request.policy().retrieval().candidateLimitPerQuery());
        Map<String, Object> body = Map.of(
                "size",
                limit,
                "_source",
                List.of("content", "metadata"),
                "query",
                Map.of("match", Map.of("content", Map.of("query", query.query(), "operator", "or"))));
        Request http =
                new Request("POST", "/" + ElasticsearchVectorStoreFactory.indexName(dataset, properties) + "/_search");
        http.setJsonEntity(JsonUtils.toJson(body));
        try {
            String json = EntityUtils.toString(client.performRequest(http).getEntity());
            JsonNode hits = JsonUtils.readTree(json).path("hits").path("hits");
            List<RetrievalCandidate> result = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                Map<String, Object> metadata = objectMap(source.path("metadata"));
                if (!matches(metadata, request.metadataFilter())) continue;
                String activeVersion = dataset.getActiveIndexVersionId();
                if (activeVersion != null && !activeVersion.equals(string(metadata.get("indexVersionId")))) continue;
                result.add(new RetrievalCandidate(
                        dataset.getId(),
                        string(metadata.get("documentId")),
                        string(metadata.get("segmentId")),
                        integer(metadata.get("position")),
                        source.path("content").asText(),
                        string(metadata.get("parentContent")),
                        hit.path("_score").asDouble(),
                        getChannel(),
                        query.id(),
                        metadata));
            }
            return result;
        } catch (Exception ignored) {
            // Sparse recall is an optional channel; dense retrieval can continue when ES rejects a query.
            return List.of();
        }
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        Map<String, Object> value = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(entry -> value.put(entry.getKey(), JsonUtils.convert(entry.getValue(), Object.class)));
        return value;
    }

    private static boolean matches(Map<String, Object> actual, Map<String, Object> expected) {
        return expected == null
                || expected.entrySet().stream()
                        .allMatch(e -> String.valueOf(e.getValue()).equals(String.valueOf(actual.get(e.getKey()))));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
