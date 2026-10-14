package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.List;

/**
 * SPI for post-retrieval reranking. A reranker takes the fused candidate list produced
 * by {@code HybridRetriever} and returns it, possibly reordered, rescored or trimmed.
 * <p>
 * Implementations are Spring beans. Which reranker is used per request is decided by
 * {@code RetrievalConfig#rerankEnabled}; when disabled, {@link NoopReranker} is used.
 */
public interface Reranker {

    /** Unique name; matched against {@code RetrievalConfig#rerankerName} when set. */
    String getName();

    /**
     * Rerank the candidates for {@code query}. May reorder, rescore, or drop entries.
     * The returned list must never be null; empty in / empty out.
     *
     * @param query      the original user query
     * @param candidates fused hybrid results, highest score first
     * @param topN       requested top count (implementations may return fewer)
     */
    List<RetrievedSegment> rerank(String query, List<RetrievedSegment> candidates, int topN);
}
