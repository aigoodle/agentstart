package io.aigoodle.knowledge.rag;

import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RetrievalCandidate;
import io.aigoodle.rag.spi.FusionStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion implements FusionStrategy {
    @Override
    public RagPolicy.FusionMethod getMethod() {
        return RagPolicy.FusionMethod.RRF;
    }

    @Override
    public List<RetrievalCandidate> fuse(List<List<RetrievalCandidate>> resultSets, RagPolicy.Fusion policy) {
        int k = Math.max(1, policy.rrfK());
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RetrievalCandidate> values = new LinkedHashMap<>();
        for (List<RetrievalCandidate> resultSet : resultSets) {
            for (int rank = 0; rank < resultSet.size(); rank++) {
                RetrievalCandidate item = resultSet.get(rank);
                String key = key(item);
                scores.merge(key, 1.0 / (k + rank + 1), Double::sum);
                values.putIfAbsent(key, item);
            }
        }
        List<RetrievalCandidate> fused = new ArrayList<>();
        scores.forEach((key, score) -> {
            RetrievalCandidate c = values.get(key);
            fused.add(new RetrievalCandidate(
                    c.datasetId(),
                    c.documentId(),
                    c.segmentId(),
                    c.position(),
                    c.content(),
                    c.parentContent(),
                    score,
                    c.channel(),
                    c.queryId(),
                    c.metadata()));
        });
        fused.sort(Comparator.comparingDouble(RetrievalCandidate::score).reversed());
        return fused;
    }

    private static String key(RetrievalCandidate item) {
        return item.datasetId() + ':' + item.documentId() + ':' + item.segmentId();
    }
}
