package io.aigoodle.knowledge.retrieve;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import io.aigoodle.knowledge.index.VectorStoreManager;
import io.aigoodle.knowledge.mapper.SegmentMapper;
import io.aigoodle.knowledge.rerank.NoopReranker;
import io.aigoodle.knowledge.rerank.Reranker;
import io.aigoodle.knowledge.rerank.RerankerRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

/**
 * Retrieves candidates using dense vector similarity. When reranking is enabled,
 * candidates are passed through the {@link Reranker} chosen by {@link RetrievalConfig}.
 */
public class HybridRetriever {

    private final SegmentMapper segmentMapper;
    private final VectorStoreManager vectorStoreManager;
    private final RerankerRegistry rerankerRegistry;
    private final List<QueryTransformer> queryTransformers;

    public HybridRetriever(SegmentMapper segmentMapper, VectorStoreManager vectorStoreManager) {
        this(segmentMapper, vectorStoreManager, new RerankerRegistry(List.of(), new NoopReranker()));
    }

    public HybridRetriever(
            SegmentMapper segmentMapper, VectorStoreManager vectorStoreManager, RerankerRegistry rerankerRegistry) {
        this(segmentMapper, vectorStoreManager, rerankerRegistry, List.of(new DefaultQueryTransformer()));
    }

    public HybridRetriever(
            SegmentMapper segmentMapper,
            VectorStoreManager vectorStoreManager,
            RerankerRegistry rerankerRegistry,
            List<QueryTransformer> queryTransformers) {
        this.segmentMapper = segmentMapper;
        this.vectorStoreManager = vectorStoreManager;
        this.rerankerRegistry = rerankerRegistry;
        this.queryTransformers = queryTransformers == null ? List.of() : queryTransformers;
    }

    public List<RetrievedSegment> retrieve(DatasetEntity dataset, RetrievalConfig config, RetrievalRequest request) {
        if (!vectorStoreManager.hasVectorIndex(dataset)) return List.of();
        RetrievalPlan plan = RetrievalPlan.resolve(config, request);
        List<String> queries = queryVariants(request.getQuery(), config);
        Map<String, Double> vectorScores = new HashMap<>();
        for (String query : queries) {
            mergeMaximum(vectorScores, recallVectorScores(dataset, query, plan));
        }

        Set<String> candidateIds = vectorScores.keySet();
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<String, SegmentEntity> segmentsById = new HashMap<>();
        for (SegmentEntity segment : segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .in(SegmentEntity::getId, candidateIds))) {
            segmentsById.put(segment.getId(), segment);
        }

        List<RetrievedSegment> results = new ArrayList<>();
        for (String candidateId : candidateIds) {
            SegmentEntity segment = segmentsById.get(candidateId);
            if (segment == null || Boolean.FALSE.equals(segment.getEnabled())) {
                continue;
            }
            Map<String, Object> metadata = JsonUtils.parseMap(segment.getMetadataJson());
            if (!matchesFilter(metadata, request.getMetadataFilter())) {
                continue;
            }
            double vectorScore = vectorScores.getOrDefault(candidateId, 0.0);
            Object parentContent = metadata.get("parentContent");
            results.add(RetrievedSegment.builder()
                    .segmentId(segment.getId())
                    .datasetId(segment.getDatasetId())
                    .documentId(segment.getDocumentId())
                    .position(segment.getPosition())
                    .content(segment.getContent())
                    .parentContent(parentContent == null ? null : String.valueOf(parentContent))
                    .vectorScore(vectorScore)
                    .score(vectorScore)
                    .metadata(metadata)
                    .build());
        }

        results.sort(Comparator.comparingDouble(RetrievedSegment::getScore).reversed());

        if (config.isRerankEnabled() && !results.isEmpty()) {
            Reranker reranker = pickReranker(config);
            int rerankPool = Math.min(results.size(), Math.max(plan.topK(), config.getRerankPoolSize()));
            results = new ArrayList<>(reranker.rerank(request.getQuery(), results.subList(0, rerankPool), plan.topK()));
        }

        List<RetrievedSegment> selectedResults = new ArrayList<>();
        Map<String, Integer> perDocument = new HashMap<>();
        for (RetrievedSegment result : results) {
            if (result.getScore() < plan.scoreThreshold()) {
                continue;
            }
            if (plan.maxChunksPerDocument() > 0
                    && perDocument.getOrDefault(result.getDocumentId(), 0) >= plan.maxChunksPerDocument()) {
                continue;
            }
            selectedResults.add(result);
            perDocument.merge(result.getDocumentId(), 1, Integer::sum);
            if (selectedResults.size() >= plan.topK()) {
                break;
            }
        }
        expandAdjacentContext(dataset.getTenantId(), selectedResults, config.getNeighborWindow());
        return selectedResults;
    }

    private List<String> queryVariants(String query, RetrievalConfig config) {
        if (!config.isQueryExpansionEnabled()) return List.of(query);
        Set<String> variants = new LinkedHashSet<>();
        variants.add(query);
        String current = query;
        for (QueryTransformer transformer : queryTransformers.stream()
                .sorted(Comparator.comparingInt(QueryTransformer::getOrder))
                .toList()) {
            for (String candidate : transformer.transform(current)) {
                if (candidate != null && !candidate.isBlank()) variants.add(candidate);
            }
        }
        return variants.stream()
                .limit(Math.max(1, config.getMaxQueryVariants()))
                .toList();
    }

    private static void mergeMaximum(Map<String, Double> target, Map<String, Double> source) {
        source.forEach((id, score) -> target.merge(id, score, Math::max));
    }

    private void expandAdjacentContext(String tenantId, List<RetrievedSegment> results, int window) {
        if (window <= 0) return;
        for (RetrievedSegment result : results) {
            int position = result.getPosition() == null ? 0 : result.getPosition();
            List<SegmentEntity> neighbors = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                    .eq(SegmentEntity::getTenantId, tenantId)
                    .eq(SegmentEntity::getDocumentId, result.getDocumentId())
                    .eq(SegmentEntity::getEnabled, true)
                    .between(SegmentEntity::getPosition, Math.max(0, position - window), position + window)
                    .orderByAsc(SegmentEntity::getPosition));
            if (neighbors.size() > 1) {
                result.setExpandedContext(neighbors.stream()
                        .map(SegmentEntity::getContent)
                        .filter(content -> content != null && !content.isBlank())
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse(result.contextText()));
            }
        }
    }

    private Map<String, Double> recallVectorScores(DatasetEntity dataset, String query, RetrievalPlan plan) {
        Map<String, Double> scores = new HashMap<>();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(plan.recallLimit())
                .similarityThreshold(0.0)
                .build();
        List<Document> documents = vectorStoreManager.getStore(dataset).similaritySearch(searchRequest);
        if (documents == null) {
            return scores;
        }
        for (Document document : documents) {
            if (!belongsToActiveIndex(dataset, document.getMetadata().get("indexVersionId"))) {
                continue;
            }
            Object segmentId = document.getMetadata().get("segmentId");
            if (segmentId != null) {
                scores.put(segmentId.toString(), document.getScore() == null ? 0.0 : document.getScore());
            }
        }
        return scores;
    }

    private static boolean belongsToActiveIndex(DatasetEntity dataset, Object vectorIndexVersion) {
        String active = dataset.getActiveIndexVersionId();
        return active == null ? vectorIndexVersion == null : active.equals(String.valueOf(vectorIndexVersion));
    }

    private Reranker pickReranker(RetrievalConfig config) {
        String name = config.getRerankerName();
        // Convention: when a rerankModelId is configured but no explicit name is set,
        // route to the model-based reranker automatically.
        if ((name == null || name.isBlank())
                && config.getRerankModelId() != null
                && !config.getRerankModelId().isBlank()) {
            name = "model";
        }
        return rerankerRegistry.get(name);
    }

    private static boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> expectedEntry : filter.entrySet()) {
            Object actual = metadata.get(expectedEntry.getKey());
            if (actual == null || !String.valueOf(actual).equals(String.valueOf(expectedEntry.getValue()))) {
                return false;
            }
        }
        return true;
    }
}
