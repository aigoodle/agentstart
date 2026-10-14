package io.aigoodle.knowledge.retrieve;

import io.aigoodle.knowledge.config.RetrievalConfig;

/** Immutable effective settings for one vector retrieval request. */
record RetrievalPlan(int topK, int recallLimit, double scoreThreshold, int maxChunksPerDocument) {

    static RetrievalPlan resolve(RetrievalConfig config, RetrievalRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : config.getTopK();
        int recallLimit = config.isRerankEnabled()
                ? Math.max(topK, config.getRerankPoolSize())
                : Math.max(topK * Math.max(1, config.getRecallMultiplier()), topK);
        return new RetrievalPlan(
                topK,
                recallLimit,
                request.getScoreThreshold() != null ? request.getScoreThreshold() : config.getScoreThreshold(),
                Math.max(0, config.getMaxChunksPerDocument()));
    }
}
