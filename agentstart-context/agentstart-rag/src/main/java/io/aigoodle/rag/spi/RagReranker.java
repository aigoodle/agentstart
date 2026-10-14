package io.aigoodle.rag.spi;

import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RetrievalCandidate;
import java.util.List;

public interface RagReranker {
    String getName();

    List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates, RagPolicy.Rerank policy);
}
