package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.model.service.ModelService;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Reranker that delegates to an LLM: asks the model to score each candidate for
 * relevance and reorders by the returned scores. Falls back to the hybrid score if the
 * model reply cannot be parsed, so a bad model never breaks retrieval.
 * <p>
 * The model id is taken from {@code RetrievalConfig#rerankModelId} at call time and
 * resolved via {@link ModelService}, so any provider registered in the model module can
 * act as a reranker without extra wiring.
 */
public class ModelReranker implements Reranker {

    public static final String NAME = "model";
    private static final Logger logger = LoggerFactory.getLogger(ModelReranker.class);

    private final ModelService modelService;
    private final String defaultModelId;

    public ModelReranker(ModelService modelService, String defaultModelId) {
        this.modelService = modelService;
        this.defaultModelId = defaultModelId;
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
        if (defaultModelId == null || defaultModelId.isBlank()) {
            return candidates;
        }
        try {
            ChatClient chatClient = modelService.getChatClient(defaultModelId);
            String modelResponse = chatClient
                    .prompt()
                    .system(ModelRerankPrompt.SYSTEM_MESSAGE)
                    .user(ModelRerankPrompt.render(query, candidates))
                    .call()
                    .content();
            ModelRelevanceScores relevanceScores = ModelRelevanceScoreParser.parse(modelResponse, candidates.size());
            List<RetrievedSegment> rescoredCandidates = new ArrayList<>(candidates.size());
            for (int index = 0; index < candidates.size(); index++) {
                RetrievedSegment candidate = candidates.get(index);
                OptionalDouble modelScore = relevanceScores.scoreAt(index);
                if (modelScore.isPresent()) {
                    candidate.setScore(modelScore.getAsDouble());
                }
                rescoredCandidates.add(candidate);
            }
            return RankedSegments.highestScoring(rescoredCandidates, topN);
        } catch (Exception exception) {
            logger.warn("Model reranker failed, keeping hybrid order: {}", exception.getMessage(), exception);
            return candidates;
        }
    }
}
