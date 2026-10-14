package io.aigoodle.workflow.node.builtin;

import io.aigoodle.knowledge.retrieve.RetrievalRequest;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Normalized datasets and retrieval request for a knowledge workflow node. */
record KnowledgeRetrievalConfiguration(List<String> datasetIds, RetrievalRequest retrievalRequest) {

    private static final int DEFAULT_TOP_K = 5;

    KnowledgeRetrievalConfiguration {
        datasetIds = List.copyOf(datasetIds);
    }

    static KnowledgeRetrievalConfiguration from(NodeDef node, ExecutionContext context) {
        String query = VariableResolver.render(node.getString("query", "{{#sys.query#}}"), context.getPool());
        RetrievalRequest request = RetrievalRequest.builder()
                .query(query)
                .topK(node.getInt("topK", DEFAULT_TOP_K))
                .build();
        return new KnowledgeRetrievalConfiguration(normalizeDatasetIds(node.get("datasetIds")), request);
    }

    boolean hasDatasets() {
        return !datasetIds.isEmpty();
    }

    private static List<String> normalizeDatasetIds(Object configuredDatasetIds) {
        Set<String> uniqueIds = new LinkedHashSet<>();
        if (configuredDatasetIds instanceof List<?> identifiers) {
            identifiers.forEach(identifier -> addDatasetId(uniqueIds, identifier));
        } else {
            addDatasetId(uniqueIds, configuredDatasetIds);
        }
        return new ArrayList<>(uniqueIds);
    }

    private static void addDatasetId(Set<String> datasetIds, Object configuredId) {
        if (configuredId == null) {
            return;
        }
        String datasetId = String.valueOf(configuredId).trim();
        if (!datasetId.isEmpty()) {
            datasetIds.add(datasetId);
        }
    }
}
