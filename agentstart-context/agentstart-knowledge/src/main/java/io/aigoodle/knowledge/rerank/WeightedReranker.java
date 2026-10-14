package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rescores candidates by blending vector score and content-length prior.
 * The prior mildly penalises very short / very long chunks, which is the same heuristic
 * Dify's "weighted score" reranker applies.
 * <p>
 * Weights are configured via {@code spring-agent.knowledge.reranker.weighted.*}:
 * {@code vectorWeight} + {@code lengthWeight} should sum to 1
 * (they are renormalised if they don't).
 */
public class WeightedReranker implements Reranker {

    public static final String NAME = "weighted";

    private final WeightedRerankerSettings settings;

    public WeightedReranker() {
        this(WeightedRerankerSettings.defaults());
    }

    public WeightedReranker(WeightedRerankerSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<RetrievedSegment> rerank(String query, List<RetrievedSegment> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<RetrievedSegment> rescoredCandidates = new ArrayList<>(candidates.size());
        for (RetrievedSegment candidate : candidates) {
            double contentLengthScore = contentLengthPrior(candidate.getContent());
            double combinedScore =
                    settings.vectorWeight() * candidate.getVectorScore() + settings.lengthWeight() * contentLengthScore;
            candidate.setScore(combinedScore);
            rescoredCandidates.add(candidate);
        }
        return RankedSegments.highestScoring(rescoredCandidates, topN);
    }

    /** Bell curve peaking at the ideal content length. */
    private double contentLengthPrior(String content) {
        int contentLength = content == null ? 0 : content.length();
        if (contentLength == 0) {
            return 0.0;
        }
        double lengthRatio = (double) contentLength / settings.idealContentLength();
        // Gaussian-like drop-off centred at 1.0.
        double logarithmicDistance = Math.log(lengthRatio);
        return Math.exp(-0.5 * logarithmicDistance * logarithmicDistance);
    }
}
