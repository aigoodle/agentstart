package io.aigoodle.knowledge.rag;

import io.aigoodle.knowledge.rerank.ModelReranker;
import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RetrievalCandidate;
import io.aigoodle.rag.spi.RagReranker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses the existing knowledge reranker with the model selected by this RAG policy. */
public final class KnowledgeModelRagReranker implements RagReranker {
    private final ModelService modelService;

    public KnowledgeModelRagReranker(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public String getName() {
        return "model";
    }

    @Override
    public List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates, RagPolicy.Rerank policy) {
        if (policy.model() == null || policy.model().isBlank()) {
            throw new IllegalArgumentException("A rerank model ID is required");
        }
        Map<String, RetrievalCandidate> originals = new LinkedHashMap<>();
        List<RetrievedSegment> segments = candidates.stream().map(c -> {
            originals.put(c.datasetId() + ':' + c.segmentId(), c);
            return RetrievedSegment.builder().datasetId(c.datasetId()).documentId(c.documentId())
                    .segmentId(c.segmentId()).position(c.position()).content(c.content())
                    .parentContent(c.parentContent()).score(c.score()).metadata(c.metadata()).build();
        }).toList();
        return new ModelReranker(modelService, policy.model()).rerank(query, segments, policy.topN()).stream().map(hit -> {
            RetrievalCandidate original = originals.get(hit.getDatasetId() + ':' + hit.getSegmentId());
            return new RetrievalCandidate(original.datasetId(), original.documentId(), original.segmentId(),
                    original.position(), original.content(), original.parentContent(), hit.getScore(),
                    original.channel(), original.queryId(), original.metadata());
        }).toList();
    }
}
