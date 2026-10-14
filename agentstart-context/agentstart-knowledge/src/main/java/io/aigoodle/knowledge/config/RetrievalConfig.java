package io.aigoodle.knowledge.config;

import io.aigoodle.knowledge.enums.RetrievalMethod;
import io.aigoodle.rag.api.RagPolicy;
import lombok.Data;

/**
 * How queries are matched and ranked against a dataset.
 */
@Data
public class RetrievalConfig {

    /** Composable execution policy used by the new /rag/retrieve pipeline. */
    private RagPolicy ragPolicy = RagPolicy.defaults();

    private RetrievalMethod method = RetrievalMethod.VECTOR;

    private int topK = 5;

    /** Minimum vector similarity score (0..1) for a chunk to be returned. */
    private double scoreThreshold = 0.0;

    /** Candidate multiplier before fusion/reranking. */
    private int recallMultiplier = 6;

    /** Diversity guard; zero means unlimited chunks from the same document. */
    private int maxChunksPerDocument = 0;

    /** Expand each hit with this many preceding/following chunks from the same document. */
    private int neighborWindow = 0;

    /** Normalize the query and generate conservative lexical variants before recall. */
    private boolean queryExpansionEnabled = true;

    /** Maximum number of query variants, including the original query. */
    private int maxQueryVariants = 3;

    /** Optional rerank model id (from the model module); null disables reranking. */
    private String rerankModelId;

    private boolean rerankEnabled = false;

    /**
     * Which reranker to use when {@link #rerankEnabled}. Matched against
     * {@code Reranker#getName()}. Values shipped by default: {@code noop},
     * {@code weighted}, {@code model}. Defaults to {@code weighted}.
     */
    private String rerankerName = "weighted";

    /** How many candidates to pass into the reranker (default: 4 &times; topK). */
    private int rerankPoolSize = 20;

    public static RetrievalConfig vector() {
        return new RetrievalConfig();
    }
}
